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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档处理服务实现：切片 + 向量入库 + 检索
 * 使用 SimpleVectorStore（文件版，零额外部署）
 */
@Service
@RequiredArgsConstructor
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionServiceImpl.class);

    private final EmbeddingModel embeddingModel;

    private VectorStore vectorStore;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** 向量存储文件路径 */
    private static final String VECTOR_STORE_FILE = "vector_store.json";

    @PostConstruct
    public void init() {
        // 初始化 SimpleVectorStore（文件持久化）
        Path vectorStorePath = Paths.get(uploadDir, VECTOR_STORE_FILE);
        this.vectorStore = SimpleVectorStore.builder(embeddingModel)
                .build();

        // 如果已有向量文件，尝试加载
        if (Files.exists(vectorStorePath)) {
            try {
                ((SimpleVectorStore) this.vectorStore).load(vectorStorePath.toFile());
            } catch (Exception e) {
                log.warn("无法加载已有向量库文件，将重新创建: {}", e.getMessage());
            }
        }
    }

    @Override
    public void ingest(Long userId, Long materialId, String sourceName, String text) {
        if (text == null || text.isBlank()) {
            log.warn("文本为空，跳过入库: materialId={}", materialId);
            return;
        }

        // 1. 将文本包装为 Document
        Document doc = new Document(text, Map.of(
                "userId", userId.toString(),
                "materialId", materialId.toString(),
                "source", sourceName
        ));

        // 2. 切片（每段 500 token，重叠 50 token）
        TokenTextSplitter splitter = new TokenTextSplitter(500, 50, 5, 1000, true);
        List<Document> chunks = splitter.apply(List.of(doc));

        // 3. 为每个切片注入 metadata
        List<Document> chunksWithMetadata = chunks.stream().peek(chunk -> {
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("userId", userId.toString());
            metadata.put("materialId", materialId.toString());
            metadata.put("source", sourceName);
            chunk.getMetadata().putAll(metadata);
        }).collect(Collectors.toList());

        // 4. 写入向量库
        vectorStore.add(chunksWithMetadata);

        // 5. 持久化到文件
        persistVectorStore();

        log.info("入库完成: materialId={}, 切片数={}", materialId, chunksWithMetadata.size());
    }

    @Override
    public List<Document> retrieve(Long userId, String query, int topK) {
        // 检索时按 userId 过滤，实现用户隔离
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
    public String getMaterialContext(Long userId, Long materialId, int maxChunks) {
        // 通过检索方式获取该资料的代表性切片
        // 用 materialId 作为查询条件
        List<Document> chunks = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query("")
                        .topK(maxChunks)
                        .filterExpression("materialId == '" + materialId + "'")
                        .build()
        );

        return chunks.stream()
                .map(chunk -> chunk.getText())
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    @Override
    public void removeByMaterial(Long materialId) {
        // SimpleVectorStore 不支持按 metadata 删除，
        // 目前只能重建（去掉该 material 的切片）
        // 实际项目中可换用 PGVector/RedisVectorStore 支持按条件删除
        log.warn("removeByMaterial: SimpleVectorStore 不支持按条件删除，跳过。materialId={}", materialId);
    }

    /**
     * 持久化向量库到文件
     */
    private void persistVectorStore() {
        try {
            Path path = Paths.get(uploadDir);
            Files.createDirectories(path);
            Path storePath = path.resolve(VECTOR_STORE_FILE);
            ((SimpleVectorStore) this.vectorStore).save(storePath.toFile());
        } catch (IOException e) {
            log.error("向量库持久化失败", e);
        }
    }
}
