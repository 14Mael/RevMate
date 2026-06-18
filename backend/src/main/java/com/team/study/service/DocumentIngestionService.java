package com.team.study.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档处理服务：提取 → 切片 → 向量入库
 * 供 MaterialService 上传后调用，以及 QuizService 出题时获取上下文
 */
public interface DocumentIngestionService {

    /**
     * 将文本切片并向量化入库
     * @param userId      用户 ID（用于隔离）
     * @param materialId  资料 ID
     * @param sourceName  来源名称（文件名）
     * @param text        提取后的纯文本
     */
    void ingest(Long userId, Long materialId, String sourceName, String text);

    /**
     * 按用户和查询检索相关切片
     * @param userId 用户 ID（隔离）
     * @param query  查询文本
     * @param topK   返回条数
     */
    List<Document> retrieve(Long userId, String query, int topK);

    /**
     * 按资料获取代表性切片上下文（供出题使用）
     * @param userId     用户 ID
     * @param materialId 资料 ID
     * @param maxChunks  最大切片数
     */
    String getMaterialContext(Long userId, Long materialId, int maxChunks);

    /**
     * 删除资料时清理对应的向量切片
     * @param materialId 资料 ID
     */
    void removeByMaterial(Long materialId);
}
