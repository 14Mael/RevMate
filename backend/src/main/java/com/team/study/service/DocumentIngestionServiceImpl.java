package com.team.study.service;

import com.team.study.entity.EmbeddingStatus;
import com.team.study.entity.MaterialChunk;
import com.team.study.repository.MaterialChunkRepository;
import com.team.study.repository.MaterialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档处理服务 — 简化版（关键词检索，不依赖外部 embedding API）
 * 后续可替换为正式的 VectorStore 方案
 */
@Service
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionServiceImpl.class);

    private final MaterialChunkRepository materialChunkRepository;
    private final MaterialRepository materialRepository;
    private final EmbeddingService embeddingService;

    @Autowired
    public DocumentIngestionServiceImpl(
            MaterialChunkRepository materialChunkRepository,
            MaterialRepository materialRepository,
            EmbeddingService embeddingService) {
        this.materialChunkRepository = materialChunkRepository;
        this.materialRepository = materialRepository;
        this.embeddingService = embeddingService;
    }

    DocumentIngestionServiceImpl(
            MaterialChunkRepository materialChunkRepository,
            MaterialRepository materialRepository) {
        this(materialChunkRepository, materialRepository, disabledEmbeddingService());
    }

    @Override
    @Transactional
    public void ingest(Long userId, Long materialId, String sourceName, String text) {
        if (text == null || text.isBlank()) {
            log.warn("文本为空，跳过入库: materialId={}", materialId);
            return;
        }

        // 简单切片（按段落/句子切分）
        List<String> segments = splitText(text, 500);
        List<MaterialChunk> chunks = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            String chunkText = segments.get(i);
            if (chunkText.isBlank()) continue;
            MaterialChunk chunk = new MaterialChunk();
            chunk.setMaterialId(materialId);
            chunk.setUserId(userId);
            chunk.setChunkIndex(i);
            chunk.setText(chunkText);
            chunk.setSource(sourceName != null ? sourceName : "未知资料");
            chunk.setEmbeddingStatus(EmbeddingStatus.PENDING);
            chunks.add(chunk);
        }

        fillEmbeddings(chunks);
        materialChunkRepository.deleteByMaterialId(materialId);
        materialChunkRepository.saveAll(chunks);
        log.info("入库完成: materialId={}, 切片数={}", materialId, chunks.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> retrieve(Long userId, String query, int topK) {
        return retrieveFromChunks(materialChunkRepository.findByUserId(userId), userId, query, topK);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> retrieve(Long userId, Long subjectId, String query, int topK) {
        List<Long> materialIds = materialRepository.findIdsByUserIdAndSubjectId(userId, subjectId);
        if (materialIds.isEmpty()) {
            return List.of();
        }
        return retrieveFromChunks(
                materialChunkRepository.findByUserIdAndMaterialIdIn(userId, materialIds),
                userId,
                query,
                topK);
    }

    private List<Document> retrieveFromChunks(List<MaterialChunk> candidateChunks, Long userId, String query, int topK) {
        float[] queryEmbedding = createQueryEmbedding(query);
        List<Document> results = new ArrayList<>();
        for (MaterialChunk chunk : candidateChunks) {
            double keywordScore = keywordScore(chunk, query);
            double vectorScore = queryEmbedding == null ? 0.0 : vectorScore(chunk, queryEmbedding);
            double score = queryEmbedding == null
                    ? keywordScore
                    : vectorScore * 0.75 + keywordScore * 0.25;
            if (score > 0.0) {
                Document doc = new Document(chunk.getText(), Map.of(
                        "userId", userId.toString(),
                        "materialId", chunk.getMaterialId().toString(),
                        "source", chunk.getSource()
                ));
                doc.getMetadata().put("score", score);
                doc.getMetadata().put("keywordScore", keywordScore);
                doc.getMetadata().put("vectorScore", vectorScore);
                results.add(doc);
            }
        }

        // 按匹配度排序
        results.sort((a, b) -> {
            double sa = (double) a.getMetadata().getOrDefault("score", 0.0);
            double sb = (double) b.getMetadata().getOrDefault("score", 0.0);
            return Double.compare(sb, sa);
        });

        List<Document> topResults = results.stream().limit(topK).collect(Collectors.toList());
        if (!topResults.isEmpty()) {
            log.info("检索命中: query={}, 结果数={}, 最高分={}",
                    query, topResults.size(), topResults.get(0).getMetadata().get("score"));
        } else {
            log.warn("检索无命中: query='{}', 候选切片数={}", query, candidateChunks.size());
        }
        return topResults;
    }

    private void fillEmbeddings(List<MaterialChunk> chunks) {
        if (!embeddingService.isEnabled()) {
            return;
        }
        for (MaterialChunk chunk : chunks) {
            try {
                float[] embedding = embeddingService.embed(chunk.getText());
                chunk.setEmbedding(EmbeddingVectorCodec.serialize(embedding));
                chunk.setEmbeddingModel(embeddingService.modelName());
                chunk.setEmbeddingStatus(EmbeddingStatus.READY);
            } catch (Exception e) {
                chunk.setEmbedding(null);
                chunk.setEmbeddingModel(embeddingService.modelName());
                chunk.setEmbeddingStatus(EmbeddingStatus.FAILED);
                log.warn("生成切片 embedding 失败: materialId={}, chunkIndex={}, reason={}",
                        chunk.getMaterialId(), chunk.getChunkIndex(), e.getMessage());
            }
        }
    }

    private float[] createQueryEmbedding(String query) {
        if (!embeddingService.isEnabled()) {
            return null;
        }
        try {
            return embeddingService.embed(query);
        } catch (Exception e) {
            log.warn("生成查询 embedding 失败，回退关键词检索: reason={}", e.getMessage());
            return null;
        }
    }

    private double vectorScore(MaterialChunk chunk, float[] queryEmbedding) {
        if (chunk.getEmbeddingStatus() != EmbeddingStatus.READY || chunk.getEmbedding() == null || chunk.getEmbedding().isBlank()) {
            return 0.0;
        }
        try {
            return EmbeddingVectorCodec.cosineSimilarity(queryEmbedding, EmbeddingVectorCodec.deserialize(chunk.getEmbedding()));
        } catch (Exception e) {
            log.warn("解析切片 embedding 失败: materialId={}, chunkIndex={}, reason={}",
                    chunk.getMaterialId(), chunk.getChunkIndex(), e.getMessage());
            return 0.0;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String getMaterialContext(Long userId, Long materialId, int maxChunks) {
        return getMaterialContext(userId, materialId, null, maxChunks);
    }

    @Override
    @Transactional(readOnly = true)
    public String getMaterialContext(Long userId, Long materialId, String query, int maxChunks) {
        List<MaterialChunk> chunks = materialChunkRepository.findByMaterialIdOrderByChunkIndexAsc(materialId);

        List<MaterialChunk> userChunks = chunks.stream()
                .filter(c -> c.getUserId().equals(userId))
                .toList();
        List<MaterialChunk> selectedChunks = selectMaterialContextChunks(userChunks, query, maxChunks);

        return selectedChunks.stream()
                .limit(maxChunks)
                .map(MaterialChunk::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    @Override
    @Transactional(readOnly = true)
    public String getSubjectContext(Long userId, Long subjectId, int maxChunks) {
        List<Long> materialIds = materialRepository.findIdsByUserIdAndSubjectId(userId, subjectId);
        if (materialIds.isEmpty()) {
            return "";
        }
        return materialChunkRepository.findByUserIdAndMaterialIdInOrderByMaterialIdAscChunkIndexAsc(userId, materialIds)
                .stream()
                .limit(maxChunks)
                .map(MaterialChunk::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    @Override
    @Transactional
    public void removeByMaterial(Long materialId) {
        materialChunkRepository.deleteByMaterialId(materialId);
        log.info("已清理资料: materialId={}", materialId);
    }

    @Override
    @Transactional
    public int reindexMaterial(Long userId, Long materialId) {
        List<MaterialChunk> chunks = materialChunkRepository.findByMaterialIdOrderByChunkIndexAsc(materialId)
                .stream()
                .filter(chunk -> chunk.getUserId().equals(userId))
                .filter(chunk -> chunk.getEmbeddingStatus() != EmbeddingStatus.READY
                        || chunk.getEmbedding() == null
                        || chunk.getEmbedding().isBlank())
                .toList();
        if (chunks.isEmpty() || !embeddingService.isEnabled()) {
            log.info("资料重建 embedding 跳过: materialId={}, userId={}, candidates={}, embeddingEnabled={}",
                    materialId, userId, chunks.size(), embeddingService.isEnabled());
            return 0;
        }

        List<MaterialChunk> updated = new ArrayList<>();
        for (MaterialChunk chunk : chunks) {
            try {
                float[] embedding = embeddingService.embed(chunk.getText());
                chunk.setEmbedding(EmbeddingVectorCodec.serialize(embedding));
                chunk.setEmbeddingModel(embeddingService.modelName());
                chunk.setEmbeddingStatus(EmbeddingStatus.READY);
                updated.add(chunk);
            } catch (Exception e) {
                chunk.setEmbedding(null);
                chunk.setEmbeddingModel(embeddingService.modelName());
                chunk.setEmbeddingStatus(EmbeddingStatus.FAILED);
                log.warn("重建切片 embedding 失败: materialId={}, chunkIndex={}, reason={}",
                        materialId, chunk.getChunkIndex(), e.getMessage());
            }
        }
        if (!updated.isEmpty()) {
            materialChunkRepository.saveAll(updated);
        }
        log.info("资料重建 embedding 完成: materialId={}, userId={}, updated={}", materialId, userId, updated.size());
        return updated.size();
    }

    /**
     * 简单文本切片
     */
    private List<String> splitText(String text, int maxLen) {
        List<String> result = new ArrayList<>();
        String[] paragraphs = text.split("\n");
        StringBuilder current = new StringBuilder();

        for (String p : paragraphs) {
            if (p.length() > maxLen) {
                if (current.length() > 0) {
                    result.add(current.toString().trim());
                    current = new StringBuilder();
                }
                for (int start = 0; start < p.length(); start += maxLen) {
                    int end = Math.min(start + maxLen, p.length());
                    result.add(p.substring(start, end));
                }
                continue;
            }
            if (current.length() + p.length() > maxLen && current.length() > 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(p).append("\n");
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result;
    }

    private List<MaterialChunk> selectMaterialContextChunks(List<MaterialChunk> chunks, String query, int maxChunks) {
        if (chunks.isEmpty() || maxChunks <= 0) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return chunks.stream().limit(maxChunks).toList();
        }

        Map<Integer, MaterialChunk> selected = new LinkedHashMap<>();
        if (isSummaryQuestion(query)) {
            addChunk(selected, chunks.getFirst(), maxChunks);
            chunks.stream()
                    .filter(chunk -> containsConclusionMarker(chunk.getText()))
                    .forEach(chunk -> addChunk(selected, chunk, maxChunks));
            addChunk(selected, chunks.getLast(), maxChunks);
        }

        chunks.stream()
                .map(chunk -> Map.entry(chunk, keywordScore(chunk, query)))
                .filter(entry -> entry.getValue() > 0)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(entry -> addChunk(selected, entry.getKey(), maxChunks));

        chunks.forEach(chunk -> addChunk(selected, chunk, maxChunks));
        return selected.values().stream().limit(maxChunks).toList();
    }

    private void addChunk(Map<Integer, MaterialChunk> selected, MaterialChunk chunk, int maxChunks) {
        if (selected.size() >= maxChunks || chunk == null || chunk.getChunkIndex() == null) {
            return;
        }
        selected.putIfAbsent(chunk.getChunkIndex(), chunk);
    }

    private boolean isSummaryQuestion(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        return normalized.contains("总结")
                || normalized.contains("概括")
                || normalized.contains("主要")
                || normalized.contains("结论")
                || normalized.contains("展望")
                || normalized.contains("讲了什么")
                || normalized.contains("summary")
                || normalized.contains("conclusion");
    }

    private boolean containsConclusionMarker(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("总结")
                || normalized.contains("结论")
                || normalized.contains("展望")
                || normalized.contains("summary")
                || normalized.contains("conclusion");
    }

    private double keywordScore(MaterialChunk chunk, String query) {
        String queryLower = query.toLowerCase(Locale.ROOT);
        String text = Optional.ofNullable(chunk.getText()).orElse("").toLowerCase(Locale.ROOT);
        String sourceInfo = Optional.ofNullable(chunk.getSource()).orElse("").toLowerCase(Locale.ROOT);

        Set<String> queryGrams = generateNGrams(queryLower, 2);
        queryGrams.addAll(generateNGrams(queryLower, 3));
        Set<String> textGrams = generateNGrams(text, 2);
        textGrams.addAll(generateNGrams(text, 3));

        boolean exactMatch = text.contains(queryLower) || sourceInfo.contains(queryLower);
        long overlap = textGrams.stream().filter(queryGrams::contains).count();
        double overlapRate = queryGrams.isEmpty() ? 0 : (double) overlap / queryGrams.size();

        boolean sourceMatch = false;
        for (String keyword : queryLower.split("[\\s,，。.、？?！!；;：:()（）\"'\\[\\]{}]")) {
            if (keyword.length() >= 2 && sourceInfo.contains(keyword)) {
                sourceMatch = true;
                break;
            }
        }

        double score = 0;
        if (exactMatch) score += 1.0;
        if (sourceMatch) score += 0.8;
        score += overlapRate * 0.5;

        if (score == 0) {
            for (String keyword : queryLower.split("[\\s,，。.、？?！!；;：:()（）\"'\\[\\]{}]")) {
                if (keyword.length() < 2) continue;
                if (text.contains(keyword) || sourceInfo.contains(keyword)) {
                    score += 0.2;
                    break;
                }
                if (keyword.length() >= 6 && text.contains(keyword.substring(0, 6))) {
                    score += 0.2;
                    break;
                }
            }
        }
        return score;
    }

    /** 生成字符级 n-gram。 */
    private Set<String> generateNGrams(String text, int n) {
        Set<String> grams = new HashSet<>();
        if (text == null || text.length() < n) {
            return grams;
        }
        for (int i = 0; i <= text.length() - n; i++) {
            grams.add(text.substring(i, i + n));
        }
        return grams;
    }

    private static EmbeddingService disabledEmbeddingService() {
        return new EmbeddingService() {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public String modelName() {
                return "";
            }

            @Override
            public float[] embed(String text) {
                throw new IllegalStateException("embedding 未启用");
            }
        };
    }
}
