package com.team.study.controller;

import com.team.study.common.Result;
import com.team.study.dto.request.QuizRequest;
import com.team.study.dto.response.QuestionItem;
import com.team.study.dto.response.QuizResponse;
import com.team.study.service.QuizService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizControllerTest {

    @Test
    void generateReturnsQuizResponse() {
        StubQuizService quizService = new StubQuizService();
        QuizController controller = new QuizController(quizService);

        QuizRequest request = new QuizRequest();
        request.setSubjectId(1L);
        request.setType("single");
        request.setCount(3);

        Result<QuizResponse> result = controller.generate(request);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getQuestions()).hasSize(3);
        assertThat(quizService.lastRequest.getType()).isEqualTo("single");
        assertThat(quizService.lastRequest.getCount()).isEqualTo(3);
        assertThat(quizService.lastRequest.getSubjectId()).isEqualTo(1L);
    }

    @Test
    void generateWithMaterialIdPassesItThrough() {
        StubQuizService quizService = new StubQuizService();
        QuizController controller = new QuizController(quizService);

        QuizRequest request = new QuizRequest();
        request.setSubjectId(1L);
        request.setMaterialId(99L);
        request.setType("fill");
        request.setCount(5);

        controller.generate(request);

        assertThat(quizService.lastRequest.getMaterialId()).isEqualTo(99L);
        assertThat(quizService.lastRequest.getType()).isEqualTo("fill");
    }

    private static class StubQuizService implements QuizService {
        QuizRequest lastRequest;

        @Override
        public QuizResponse generate(QuizRequest request) {
            lastRequest = request;
            List<QuestionItem> questions = java.util.stream.IntStream.range(0, request.getCount())
                    .mapToObj(i -> QuestionItem.builder()
                            .stem("题目 " + i)
                            .answer("A")
                            .analysis("解析 " + i)
                            .build())
                    .toList();
            return new QuizResponse(questions);
        }
    }
}
