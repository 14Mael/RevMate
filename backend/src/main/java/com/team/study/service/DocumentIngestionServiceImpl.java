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
        // 关键词检索（支持子串匹配，提高 OCR 容错）
        String[] keywords = query.toLowerCase().split("[\\s,，。.、？?！!；;：:()（）\"'\\[\\]{}]");

        List<Document> results = new ArrayList<>();
        String queryLower = query.toLowerCase();

        for (MaterialChunk chunk : candidateChunks) {
            String text = chunk.getText().toLowerCase();
            String sourceInfo = chunk.getSource().toLowerCase();
            int matchCount = 0;
            int totalValidKeywords = 0;

            for (String kw : keywords) {
                if (kw.length() < 2) continue;
                totalValidKeywords++;

                // 匹配文本内容
                if (text.contains(kw)) {
                    matchCount++;
                    continue;
                }

                // 匹配文件名（解决"图片里写了什么"这类问题）
                if (sourceInfo.contains(kw)) {
                    matchCount++;
                    continue;
                }

                // 长关键词用子串匹配（提高 OCR 容错）
                if (kw.length() >= 6) {
                    String sub = kw.substring(0, Math.min(6, kw.length()));
                    if (text.contains(sub)) {
                        matchCount++;
                    }
                }
            }

            // 也检查整个查询是否在文本中
            if (text.contains(queryLower)) {
                matchCount = Math.max(matchCount, totalValidKeywords);
            }

            if (matchCount > 0) {
                Document doc = new Document(chunk.getText(), Map.of(
                        "userId", userId.toString(),
                        "materialId", chunk.getMaterialId().toString(),
                        "source", chunk.getSource()
                ));
                double score = totalValidKeywords > 0 ? (double) matchCount / totalValidKeywords : 0;
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
            log.info("关键词检索命中: query={}, 结果数={}, 最高分={}",
                    query, topResults.size(), topResults.get(0).getMetadata().get("score"));
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
