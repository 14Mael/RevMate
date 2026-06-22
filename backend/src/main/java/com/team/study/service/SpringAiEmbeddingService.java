package com.team.study.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpringAiEmbeddingService implements EmbeddingService {

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    @Value("${app.rag.embedding.enabled:false}")
    private boolean enabled;

    @Value("${app.rag.embedding.model:}")
    private String modelName;

    @Override
    public boolean isEnabled() {
        return enabled && embeddingModelProvider.getIfAvailable() != null;
    }

    @Override
    public String modelName() {
        return modelName == null || modelName.isBlank() ? "unknown" : modelName;
    }

    @Override
    public float[] embed(String text) {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (!enabled || embeddingModel == null) {
            throw new IllegalStateException("embedding 未启用");
        }
        return embeddingModel.embed(text);
    }
}
