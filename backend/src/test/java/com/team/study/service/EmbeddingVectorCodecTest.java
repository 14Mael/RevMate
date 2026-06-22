package com.team.study.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingVectorCodecTest {

    @Test
    void serializesAndDeserializesFloatVectors() {
        float[] vector = new float[]{0.25f, -0.5f, 1.0f};

        String serialized = EmbeddingVectorCodec.serialize(vector);
        float[] restored = EmbeddingVectorCodec.deserialize(serialized);

        assertThat(serialized).startsWith("[");
        assertThat(restored).containsExactly(0.25f, -0.5f, 1.0f);
    }

    @Test
    void cosineSimilarityRanksAlignedVectorsHigher() {
        float[] query = new float[]{1.0f, 0.0f};

        double aligned = EmbeddingVectorCodec.cosineSimilarity(query, new float[]{0.9f, 0.1f});
        double unrelated = EmbeddingVectorCodec.cosineSimilarity(query, new float[]{0.0f, 1.0f});

        assertThat(aligned).isGreaterThan(0.99);
        assertThat(unrelated).isEqualTo(0.0);
    }
}
