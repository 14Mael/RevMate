package com.team.study.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class EmbeddingVectorCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EmbeddingVectorCodec() {
    }

    public static String serialize(float[] vector) {
        try {
            return OBJECT_MAPPER.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("embedding 序列化失败", e);
        }
    }

    public static float[] deserialize(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return new float[0];
        }
        try {
            return OBJECT_MAPPER.readValue(serialized, float[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("embedding 反序列化失败", e);
        }
    }

    public static double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0 || left.length != right.length) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
