package com.team.study.controller;

import com.team.study.dto.request.CourseKeywordRequest;
import com.team.study.dto.request.CourseRecommendRequest;
import com.team.study.dto.request.SavedCourseRequest;
import com.team.study.dto.response.CourseCard;
import com.team.study.dto.response.SavedCourseResponse;
import com.team.study.service.CourseRecommendService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseControllerTest {

    @Test
    void keywordsWrapsServiceResult() {
        CourseController controller = new CourseController(new StubCourseRecommendService());

        var result = controller.keywords(new CourseKeywordRequest(99L));

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsExactly("Java 并发", "线程池");
    }

    @Test
    void recommendWrapsServiceResult() {
        CourseController controller = new CourseController(new StubCourseRecommendService());

        var result = controller.recommend(new CourseRecommendRequest(List.of("Java 并发")));

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getFirst().title()).isEqualTo("Java 并发课程");
    }

    @Test
    void saveAndListWrapSavedCourses() {
        CourseController controller = new CourseController(new StubCourseRecommendService());

        var saved = controller.save(new SavedCourseRequest(
                "Java 并发课程",
                "B站",
                "https://example.com/java",
                "覆盖线程池。",
                "进阶",
                3L));
        var list = controller.listSaved();

        assertThat(saved.getData().id()).isEqualTo(11L);
        assertThat(list.getData()).hasSize(1);
    }

    @Test
    void deleteReturnsSuccess() {
        CourseController controller = new CourseController(new StubCourseRecommendService());

        var result = controller.delete(11L);

        assertThat(result.getCode()).isEqualTo(0);
    }

    private static class StubCourseRecommendService implements CourseRecommendService {
        @Override
        public List<String> keywords(Long materialId) {
            return List.of("Java 并发", "线程池");
        }

        @Override
        public List<CourseCard> recommend(CourseRecommendRequest request) {
            return List.of(card());
        }

        @Override
        public SavedCourseResponse save(SavedCourseRequest request) {
            return saved();
        }

        @Override
        public List<SavedCourseResponse> listSaved() {
            return List.of(saved());
        }

        @Override
        public void deleteSaved(Long id) {
        }

        private CourseCard card() {
            return new CourseCard(
                    "Java 并发课程",
                    "B站",
                    "https://example.com/java",
                    "覆盖线程池。",
                    "进阶");
        }

        private SavedCourseResponse saved() {
            return new SavedCourseResponse(
                    11L,
                    "Java 并发课程",
                    "B站",
                    "https://example.com/java",
                    "覆盖线程池。",
                    "进阶",
                    3L,
                    LocalDateTime.of(2026, 6, 24, 10, 0));
        }
    }
}
