package com.team.study.service;

import com.team.study.dto.request.CreateSubjectRequest;
import com.team.study.dto.response.SubjectResponse;
import com.team.study.entity.Subject;
import com.team.study.repository.MaterialRepository;
import com.team.study.repository.SubjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceImplTest {

    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private MaterialRepository materialRepository;
    @InjectMocks
    private SubjectServiceImpl subjectService;

    private void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTrimsNameAndSavesForCurrentUser() {
        loginAs(5L);
        when(subjectRepository.existsByUserIdAndName(5L, "Java")).thenReturn(false);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> {
            Subject subject = invocation.getArgument(0);
            subject.setId(10L);
            return subject;
        });

        SubjectResponse response = subjectService.create(request("  Java  "));

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("Java");
        verify(subjectRepository).save(argThat(s ->
                s.getUserId().equals(5L) && s.getName().equals("Java")));
    }

    @Test
    void createRejectsBlankName() {
        loginAs(5L);

        assertThatThrownBy(() -> subjectService.create(request("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("学科名称不能为空");
    }

    @Test
    void createRejectsDuplicateNameForCurrentUser() {
        loginAs(5L);
        when(subjectRepository.existsByUserIdAndName(5L, "Java")).thenReturn(true);

        assertThatThrownBy(() -> subjectService.create(request("Java")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("学科已存在");
    }

    @Test
    void listReturnsOnlyCurrentUsersSubjects() {
        loginAs(5L);
        Subject subject = subject(1L, 5L, "Java");
        when(subjectRepository.findByUserIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(subject));

        List<SubjectResponse> result = subjectService.list();

        assertThat(result).extracting(SubjectResponse::getName).containsExactly("Java");
    }

    @Test
    void updateTrimsNameAndSavesForCurrentUser() {
        loginAs(5L);
        Subject subject = subject(1L, 5L, "Java");
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(subjectRepository.findByUserIdAndName(5L, "数据结构")).thenReturn(Optional.empty());
        when(subjectRepository.save(subject)).thenReturn(subject);

        SubjectResponse response = subjectService.update(1L, request("  数据结构  "));

        assertThat(response.getName()).isEqualTo("数据结构");
        verify(subjectRepository).save(argThat(s ->
                s.getId().equals(1L) && s.getName().equals("数据结构")));
    }

    @Test
    void updateAllowsKeepingSameName() {
        loginAs(5L);
        Subject subject = subject(1L, 5L, "Java");
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(subjectRepository.findByUserIdAndName(5L, "Java")).thenReturn(Optional.of(subject));
        when(subjectRepository.save(subject)).thenReturn(subject);

        SubjectResponse response = subjectService.update(1L, request(" Java "));

        assertThat(response.getName()).isEqualTo("Java");
    }

    @Test
    void updateRejectsDuplicateNameForCurrentUser() {
        loginAs(5L);
        Subject subject = subject(1L, 5L, "Java");
        Subject duplicate = subject(2L, 5L, "数据结构");
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(subjectRepository.findByUserIdAndName(5L, "数据结构")).thenReturn(Optional.of(duplicate));

        assertThatThrownBy(() -> subjectService.update(1L, request("数据结构")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("学科已存在");
        verify(subjectRepository, never()).save(any(Subject.class));
    }

    @Test
    void updateRejectsOtherUsersSubject() {
        loginAs(5L);
        Subject subject = subject(1L, 9L, "Java");
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));

        assertThatThrownBy(() -> subjectService.update(1L, request("数据结构")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("学科不存在或无权访问");
    }

    @Test
    void deleteRejectsNonEmptySubject() {
        loginAs(5L);
        Subject subject = subject(1L, 5L, "Java");
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(materialRepository.existsBySubjectIdAndUserId(1L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> subjectService.delete(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请先删除该学科下的资料");
        verify(subjectRepository, never()).delete(subject);
    }

    @Test
    void deleteEmptySubject() {
        loginAs(5L);
        Subject subject = subject(1L, 5L, "Java");
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
        when(materialRepository.existsBySubjectIdAndUserId(1L, 5L)).thenReturn(false);

        subjectService.delete(1L);

        verify(subjectRepository).delete(subject);
    }

    @Test
    void deleteRejectsOtherUsersSubject() {
        loginAs(5L);
        Subject subject = subject(1L, 9L, "Java");
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));

        assertThatThrownBy(() -> subjectService.delete(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("学科不存在或无权访问");
    }

    private CreateSubjectRequest request(String name) {
        CreateSubjectRequest request = new CreateSubjectRequest();
        request.setName(name);
        return request;
    }

    private Subject subject(Long id, Long userId, String name) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setUserId(userId);
        subject.setName(name);
        return subject;
    }
}
