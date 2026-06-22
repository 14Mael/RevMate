package com.team.study.controller;

import com.team.study.dto.request.WrongQuestionSaveRequest;
import com.team.study.dto.response.QuestionItem;
import com.team.study.dto.response.WrongQuestionResponse;
import com.team.study.service.WrongQuestionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WrongQuestionControllerTest {

    @Test
    void listWrapsCurrentUserWrongQuestions() {
        WrongQuestionController controller = new WrongQuestionController(new StubWrongQuestionService());

        var result = controller.list();

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().getFirst().stem()).isEqualTo("Java 封装通常使用什么修饰符？");
    }

    @Test
    void batchSaveWrapsSavedWrongQuestionCounts() {
        WrongQuestionController controller = new WrongQuestionController(new StubWrongQuestionService());

        var result = controller.saveBatch(List.of(new WrongQuestionSaveRequest()));

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getFirst().wrongCount()).isEqualTo(2);
    }

    @Test
    void singleSaveWrapsSavedWrongQuestion() {
        WrongQuestionController controller = new WrongQuestionController(new StubWrongQuestionService());

        var result = controller.save(new WrongQuestionSaveRequest());

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().id()).isEqualTo(11L);
    }

    @Test
    void masterDeleteAndReinforceExposeServiceResults() {
        WrongQuestionController controller = new WrongQuestionController(new StubWrongQuestionService());

        assertThat(controller.markMastered(11L).getData().mastered()).isTrue();
        assertThat(controller.delete(11L).getCode()).isEqualTo(0);
        assertThat(controller.reinforce(11L).getData()).hasSize(1);
    }

    private static class StubWrongQuestionService implements WrongQuestionService {
        @Override
        public List<WrongQuestionResponse> list() {
            return List.of(response(false));
        }

        @Override
        public List<WrongQuestionResponse> saveBatch(List<WrongQuestionSaveRequest> requests) {
            return List.of(response(false));
        }

        @Override
        public WrongQuestionResponse save(WrongQuestionSaveRequest request) {
            return response(false);
        }

        @Override
        public WrongQuestionResponse markMastered(Long id) {
            return response(true);
        }

        @Override
        public void delete(Long id) {
        }

        @Override
        public List<QuestionItem> reinforce(Long id) {
            return List.of(QuestionItem.builder()
                    .stem("Java 封装常见做法是什么？")
                    .options(List.of("A. 暴露字段", "B. private 字段并提供方法", "C. 删除类", "D. 不写构造器"))
                    .answer("B")
                    .analysis("封装通常隐藏内部字段。")
                    .build());
        }

        private WrongQuestionResponse response(boolean mastered) {
            return new WrongQuestionResponse(
                    11L,
                    3L,
                    "Java",
                    "single",
                    "Java 封装通常使用什么修饰符？",
                    List.of("A. public", "B. private", "C. final", "D. static"),
                    "B",
                    "封装通常使用 private 字段隐藏实现细节。",
                    "public",
                    2,
                    mastered,
                    LocalDateTime.now().minusDays(2),
                    LocalDateTime.now().minusDays(1)
            );
        }
    }
}
