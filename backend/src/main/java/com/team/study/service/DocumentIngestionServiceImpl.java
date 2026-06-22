package com.team.study.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 文档处理服务 — 简化版（关键词检索，不依赖外部 embedding API）
 * 后续可替换为正式的 VectorStore 方案
 */
@Service
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionServiceImpl.class);

    /** 内存存储：materialId → 切片列表 */
    private final Map<Long, List<ChunkInfo>> store = new ConcurrentHashMap<>();

    /** 存储切片信息 */
    private static class ChunkInfo {
        final String docId;
        final String text;
        final Long userId;
        final String source;

        ChunkInfo(String docId, String text, Long userId, String source) {
            this.docId = docId;
            this.text = text;
            this.userId = userId;
            this.source = source;
        }
    }

    @Override
    public void ingest(Long userId, Long materialId, String sourceName, String text) {
        if (text == null || text.isBlank()) {
            log.warn("文本为空，跳过入库: materialId={}", materialId);
            return;
        }

        // 简单切片（按段落/句子切分）
        List<String> segments = splitText(text, 500);
        List<ChunkInfo> chunks = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            String chunkText = segments.get(i);
            if (chunkText.isBlank()) continue;
            chunks.add(new ChunkInfo(
                    materialId + "-" + i,
                    chunkText,
                    userId,
                    sourceName
            ));
        }

        store.put(materialId, chunks);
        log.info("入库完成: materialId={}, 切片数={}", materialId, chunks.size());
    }

    @Override
    public List<Document> retrieve(Long userId, String query, int topK) {
        // n-gram 检索（2-gram + 3-gram，中英文通用）
        String queryLower = query.toLowerCase();
        Set<String> queryGrams = generateNGrams(queryLower, 2);
        queryGrams.addAll(generateNGrams(queryLower, 3));

        List<Document> results = new ArrayList<>();

        for (Map.Entry<Long, List<ChunkInfo>> entry : store.entrySet()) {
            for (ChunkInfo chunk : entry.getValue()) {
                if (!chunk.userId.equals(userId)) continue;

                String text = chunk.text.toLowerCase();
                String sourceInfo = chunk.source.toLowerCase();

                // 1. 完整查询匹配文件名或内容（最高权重）
                boolean exactMatch = text.contains(queryLower) || sourceInfo.contains(queryLower);

                // 2. n-gram 重叠率
                Set<String> textGrams = generateNGrams(text, 2);
                textGrams.addAll(generateNGrams(text, 3));
                long overlap = textGrams.stream().filter(queryGrams::contains).count();
                double overlapRate = queryGrams.isEmpty() ? 0 : (double) overlap / queryGrams.size();

                // 3. 文件名关键子串匹配
                boolean sourceMatch = false;
                for (String kw : queryLower.split("[\\s,，。.、？?！!；;：:()（）\"'\\[\\]{}]")) {
                    if (kw.length() >= 2 && sourceInfo.contains(kw)) {
                        sourceMatch = true;
                        break;
                    }
                }

                // 综合评分（阈值 0.3）
                double score = 0;
                if (exactMatch) score += 1.0;
                if (sourceMatch) score += 0.8;
                score += overlapRate * 0.5;

                if (score > 0.3) {
                    Document doc = new Document(chunk.text, Map.of(
                            "userId", userId.toString(),
                            "materialId", entry.getKey().toString(),
                            "source", chunk.source
                    ));
                    doc.getMetadata().put("score", score);
                    results.add(doc);
                }
            }
        }

        results.sort((a, b) -> Double.compare(
                (double) b.getMetadata().getOrDefault("score", 0.0),
                (double) a.getMetadata().getOrDefault("score", 0.0)));

        List<Document> topResults = results.stream().limit(topK).collect(Collectors.toList());
        if (!topResults.isEmpty()) {
            log.info("检索命中: query={}, 结果数={}, 最高分={}",
                    query, topResults.size(), topResults.get(0).getMetadata().get("score"));
        } else {
            // 调试：列出当前用户的所有资料
            List<String> sources = new ArrayList<>();
            for (Map.Entry<Long, List<ChunkInfo>> entry : store.entrySet()) {
                for (ChunkInfo chunk : entry.getValue()) {
                    if (chunk.userId.equals(userId) && !sources.contains(chunk.source)) {
                        sources.add(chunk.source);
                    }
                }
            }
            log.warn("检索无命中: query='{}', 用户资料文件={}", query, sources);
        }
        return topResults;
    }

    @Override
    public String getMaterialContext(Long userId, Long materialId, int maxChunks) {
        List<ChunkInfo> chunks = store.get(materialId);
        if (chunks == null) return "";

        return chunks.stream()
                .filter(c -> c.userId.equals(userId))
                .limit(maxChunks)
                .map(c -> c.text)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    @Override
    public void removeByMaterial(Long materialId) {
        store.remove(materialId);
        log.info("已清理资料: materialId={}", materialId);
    }

    /** 生成字符级 n-gram（2-gram 和 3-gram，对中英文通用） */
    private Set<String> generateNGrams(String text, int n) {
        Set<String> grams = new HashSet<>();
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
