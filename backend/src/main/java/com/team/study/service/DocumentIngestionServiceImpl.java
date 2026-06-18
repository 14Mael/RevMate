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
        // 关键词检索（匹配该用户所有资料的切片）
        String[] keywords = query.toLowerCase().split("[\\s,，。.、？?！!；;：:]");

        List<Document> results = new ArrayList<>();

        for (Map.Entry<Long, List<ChunkInfo>> entry : store.entrySet()) {
            for (ChunkInfo chunk : entry.getValue()) {
                if (!chunk.userId.equals(userId)) continue;

                String text = chunk.text.toLowerCase();
                int matchCount = 0;
                for (String kw : keywords) {
                    if (kw.length() < 2) continue;
                    if (text.contains(kw)) matchCount++;
                }

                if (matchCount > 0) {
                    Document doc = new Document(chunk.text, Map.of(
                            "userId", userId.toString(),
                            "materialId", entry.getKey().toString(),
                            "source", chunk.source
                    ));
                    // 用匹配数作为分数
                    doc.getMetadata().put("score", (double) matchCount / keywords.length);
                    results.add(doc);
                }
            }
        }

        // 按匹配度排序
        results.sort((a, b) -> {
            double sa = (double) a.getMetadata().getOrDefault("score", 0.0);
            double sb = (double) b.getMetadata().getOrDefault("score", 0.0);
            return Double.compare(sb, sa);
        });

        return results.stream().limit(topK).collect(Collectors.toList());
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
