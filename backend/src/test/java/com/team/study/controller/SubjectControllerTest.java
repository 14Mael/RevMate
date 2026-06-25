package com.team.study.controller;

import com.team.study.common.Result;
import com.team.study.dto.request.CreateSubjectRequest;
import com.team.study.dto.response.SubjectResponse;
import com.team.study.service.SubjectService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectControllerTest {

    @Test
    void createReturnsNewSubject() {
        StubSubjectService subjectService = new StubSubjectService();
        SubjectController controller = new SubjectController(subjectService);

        CreateSubjectRequest request = new CreateSubjectRequest();
        request.setName("操作系统");

        Result<SubjectResponse> result = controller.create(request);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getName()).isEqualTo("操作系统");
        assertThat(subjectService.createCalled).isTrue();
    }

    @Test
    void listReturnsAllSubjects() {
        StubSubjectService subjectService = new StubSubjectService();
        SubjectController controller = new SubjectController(subjectService);

        Result<List<SubjectResponse>> result = controller.list();

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData().get(0).getName()).isEqualTo("Java");
        assertThat(result.getData().get(1).getName()).isEqualTo("操作系统");
    }

    @Test
    void deleteInvokesService() {
        StubSubjectService subjectService = new StubSubjectService();
        SubjectController controller = new SubjectController(subjectService);

        Result<Void> result = controller.delete(42L);

        assertThat(result.getCode()).isZero();
        assertThat(subjectService.deletedId).isEqualTo(42L);
    }

    private static class StubSubjectService implements SubjectService {
        boolean createCalled;
        Long deletedId;

        @Override
        public SubjectResponse create(CreateSubjectRequest request) {
            createCalled = true;
            return new SubjectResponse(1L, request.getName(), LocalDateTime.now());
        }

        @Override
        public List<SubjectResponse> list() {
            return List.of(
                    new SubjectResponse(1L, "Java", LocalDateTime.now()),
                    new SubjectResponse(2L, "操作系统", LocalDateTime.now())
            );
        }

        @Override
        public void delete(Long id) {
            deletedId = id;
        }
    }
}
