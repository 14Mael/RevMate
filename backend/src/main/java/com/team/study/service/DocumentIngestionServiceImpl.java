package com.team.study.service;

import com.team.study.entity.MaterialChunk;
import com.team.study.repository.MaterialChunkRepository;
import com.team.study.repository.MaterialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
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

    public DocumentIngestionServiceImpl(
            MaterialChunkRepository materialChunkRepository,
            MaterialRepository materialRepository) {
        this.materialChunkRepository = materialChunkRepository;
        this.materialRepository = materialRepository;
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
            chunks.add(chunk);
        }

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
        // n-gram 检索（2-gram + 3-gram，中英文通用），同时保留文件名关键字匹配。
        List<Document> results = new ArrayList<>();
        String queryLower = query.toLowerCase();
        Set<String> queryGrams = generateNGrams(queryLower, 2);
        queryGrams.addAll(generateNGrams(queryLower, 3));

        for (MaterialChunk chunk : candidateChunks) {
            String text = chunk.getText().toLowerCase();
            String sourceInfo = chunk.getSource().toLowerCase();

            boolean exactMatch = text.contains(queryLower) || sourceInfo.contains(queryLower);
            Set<String> textGrams = generateNGrams(text, 2);
            textGrams.addAll(generateNGrams(text, 3));
            long overlap = textGrams.stream().filter(queryGrams::contains).count();
            double overlapRate = queryGrams.isEmpty() ? 0 : (double) overlap / queryGrams.size();

            boolean sourceMatch = false;
            for (String kw : queryLower.split("[\\s,，。.、？?！!；;：:()（）\"'\\[\\]{}]")) {
                if (kw.length() >= 2 && sourceInfo.contains(kw)) {
                    sourceMatch = true;
                    break;
                }
            }

            double score = 0;
            if (exactMatch) score += 1.0;
            if (sourceMatch) score += 0.8;
            score += overlapRate * 0.5;

            if (score == 0) {
                for (String kw : queryLower.split("[\\s,，。.、？?！!；;：:()（）\"'\\[\\]{}]")) {
                    if (kw.length() < 2) continue;
                    if (text.contains(kw)) {
                        score += 0.2;
                        break;
                    }
                    if (sourceInfo.contains(kw)) {
                        score += 0.2;
                        break;
                    }
                }
            }

            if (score > 0) {
                Document doc = new Document(chunk.getText(), Map.of(
                        "userId", userId.toString(),
                        "materialId", chunk.getMaterialId().toString(),
                        "source", chunk.getSource()
                ));
                doc.getMetadata().put("score", score);
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

    @Override
    @Transactional(readOnly = true)
    public String getMaterialContext(Long userId, Long materialId, int maxChunks) {
        List<MaterialChunk> chunks = materialChunkRepository.findByMaterialIdOrderByChunkIndexAsc(materialId);

        return chunks.stream()
                .filter(c -> c.getUserId().equals(userId))
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
}
