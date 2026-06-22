package com.team.study.service;

public interface EmbeddingService {
    boolean isEnabled();

    String modelName();

    float[] embed(String text);
}
