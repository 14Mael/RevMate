package com.team.study.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 文档处理服务实现：切片 + 向量入库 + 检索
 * 使用 SimpleVectorStore（文件版，零额外部署）
 *
 * 线程安全：对 vectorStore 的访问通过 synchronized(this) 保护
 */
@Service
@RequiredArgsConstructor
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionServiceImpl.class);

    private final EmbeddingModel embeddingModel;

    private SimpleVectorStore vectorStore;

    /**
     * 维护 materialId → 文档ID列表 的映射，用于支持按资料删除向量
     */
    private final Map<Long, List<String>> materialDocIds = new ConcurrentHashMap<>();

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** 向量存储文件路径 */
    private static final String VECTOR_STORE_FILE = "vector_store.json";

    @PostConstruct
    public void init() {
        Path vectorStorePath = Paths.get(uploadDir, VECTOR_STORE_FILE);
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        if (Files.exists(vectorStorePath)) {
            try {
                this.vectorStore.load(vectorStorePath.toFile());
                log.info("已加载现有向量库: {}", vectorStorePath);
            } catch (Exception e) {
                log.warn("无法加载已有向量库文件，将重新创建: {}", e.getMessage());
            }
        }
    }

    @Override
    public synchronized void ingest(Long userId, Long materialId, String sourceName, String text) {
        if (text == null || text.isBlank()) {
            log.warn("文本为空，跳过入库: materialId={}", materialId);
            return;
        }

        String userIdStr = userId.toString();
        String materialIdStr = materialId.toString();

        // 1. 包装为 Document（metadata 一次性传入，避免重复）
        Document doc = new Document(text, Map.of(
                "userId", userIdStr,
                "materialId", materialIdStr,
                "source", sourceName
        ));

        // 2. 切片（每段 500 token，重叠 50 token）
        TokenTextSplitter splitter = new TokenTextSplitter(500, 50, 5, 1000, true);
        List<Document> chunks = splitter.apply(List.of(doc));

        // 3. 记录文档ID，用于后续删除
        List<String> docIds = chunks.stream()
                .peek(chunk -> chunk.getMetadata().put("userId", userIdStr))
                .peek(chunk -> chunk.getMetadata().put("materialId", materialIdStr))
                .map(Document::getId)
                .collect(Collectors.toList());
        materialDocIds.put(materialId, docIds);

        // 4. 写入向量库
        vectorStore.add(chunks);

        // 5. 持久化到文件（仅当有数据变更时）
        persistVectorStore();

        log.info("入库完成: materialId={}, 切片数={}", materialId, chunks.size());
    }

    @Override
    public synchronized List<Document> retrieve(Long userId, String query, int topK) {
        return vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(0.5)
                        .filterExpression("userId == '" + userId + "'")
                        .build()
        );
    }

    @Override
    public synchronized String getMaterialContext(Long userId, Long materialId, int maxChunks) {
        // 必须同时按 userId + materialId 过滤，防止越权访问
        List<Document> chunks = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query("")
                        .topK(maxChunks)
                        .filterExpression("userId == '" + userId + "' && materialId == '" + materialId + "'")
                        .build()
        );

        return chunks.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    @Override
    public synchronized void removeByMaterial(Long materialId) {
        List<String> docIds = materialDocIds.remove(materialId);
        if (docIds == null || docIds.isEmpty()) {
            log.warn("removeByMaterial: 未找到 materialId={} 的切片记录", materialId);
            return;
        }
        vectorStore.delete(docIds);
        persistVectorStore();
        log.info("已清理向量切片: materialId={}, 切片数={}", materialId, docIds.size());
    }

    /**
     * 持久化向量库到文件
     */
    private void persistVectorStore() {
        try {
            Path path = Paths.get(uploadDir);
            Files.createDirectories(path);
            Path storePath = path.resolve(VECTOR_STORE_FILE);
            vectorStore.save(storePath.toFile());
        } catch (IOException e) {
            log.error("向量库持久化失败", e);
        }
    }
}
