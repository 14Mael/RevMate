package com.team.study.extractor;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 提取器路由器 — 按文件类型分发到对应的提取器
 */
@Component
@RequiredArgsConstructor
public class ExtractorRouter {

    private final List<ContentExtractor> extractors;
    private final Map<String, ContentExtractor> typeMapping = new HashMap<>();

    @PostConstruct
    public void init() {
        // 为每个支持的 contentType 建立快速映射
        for (ContentExtractor extractor : extractors) {
            for (String type : List.of("txt", "pdf", "word", "image", "ppt", "excel", "audio")) {
                if (extractor.supports(type)) {
                    typeMapping.put(type, extractor);
                }
            }
        }
    }

    /**
     * 根据内容类型获取对应的提取器
     */
    public ContentExtractor getExtractor(String contentType) {
        ContentExtractor extractor = typeMapping.get(contentType);
        if (extractor == null) {
            throw new IllegalArgumentException("不支持的文件类型: " + contentType);
        }
        return extractor;
    }

    /**
     * 提取文本
     */
    public String extract(String contentType, Resource file) {
        return getExtractor(contentType).extract(file);
    }
}
