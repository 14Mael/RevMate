package com.team.study.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("calibration")
@EnabledIfEnvironmentVariable(named = "RAG_CALIBRATION_ENABLED", matches = "true")
class RagRetrievalCalibrationTest {

    private static final int TOP_K = 5;

    @Autowired
    private DocumentIngestionService documentIngestionService;

    @Test
    void printsVectorScoreDistributionForThresholdCalibration() {
        Long userId = requiredLongEnv("RAG_CALIBRATION_USER_ID");
        Long subjectId = requiredLongEnv("RAG_CALIBRATION_SUBJECT_ID");
        List<QueryCase> cases = List.of(
                new QueryCase("操作系统的死锁条件是什么", true),
                new QueryCase("讲一下进程调度算法", true),
                new QueryCase("你是什么模型", false),
                new QueryCase("今天天气怎么样", false),
                new QueryCase("帮我写一首诗", false)
        );

        List<CalibrationResult> results = cases.stream()
                .map(queryCase -> calibrate(userId, subjectId, queryCase))
                .toList();

        double lowestExpectedHit = results.stream()
                .filter(result -> result.expectedHit)
                .map(CalibrationResult::bestVector)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        double highestExpectedMiss = results.stream()
                .filter(result -> !result.expectedHit)
                .map(CalibrationResult::bestVector)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        double suggestedThreshold = (lowestExpectedHit + highestExpectedMiss) / 2.0;

        System.out.printf("建议阈值 = (相关最低 %.4f + 无关最高 %.4f) / 2 = %.4f%n",
                lowestExpectedHit, highestExpectedMiss, suggestedThreshold);

        assertThat(lowestExpectedHit)
                .as("相关问题的最低 bestVector 应高于无关问题的最高 bestVector，才适合用单阈值切分")
                .isGreaterThan(highestExpectedMiss);
    }

    private CalibrationResult calibrate(Long userId, Long subjectId, QueryCase queryCase) {
        List<Document> chunks = documentIngestionService.retrieve(userId, subjectId, queryCase.query, TOP_K);
        double bestVector = chunks.stream()
                .mapToDouble(chunk -> metadataScore(chunk, "vectorScore"))
                .max()
                .orElse(0.0);
        System.out.printf("[%s] %-24s bestVector=%.4f%n",
                queryCase.expectedHit ? "HIT " : "MISS", queryCase.query, bestVector);
        return new CalibrationResult(queryCase.query, queryCase.expectedHit, bestVector);
    }

    private double metadataScore(Document chunk, String key) {
        Object score = chunk.getMetadata().get(key);
        if (score instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private Long requiredLongEnv(String name) {
        String value = System.getenv(name);
        assertThat(value).as(name + " must be set when running calibration").isNotBlank();
        return Long.valueOf(value);
    }

    private record QueryCase(String query, boolean expectedHit) {
    }

    private record CalibrationResult(String query, boolean expectedHit, double bestVector) {
    }
}
