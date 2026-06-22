package com.team.study.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.study.dto.request.WrongQuestionSaveRequest;
import com.team.study.entity.WrongQuestion;
import com.team.study.repository.WrongQuestionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WrongQuestionServiceImplTest {

    @Mock
    private WrongQuestionRepository wrongQuestionRepository;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void saveCreatesWrongQuestionForCurrentUser() {
        loginAs(7L);
        WrongQuestionServiceImpl service = service();
        WrongQuestionSaveRequest request = request("single", "Java 封装通常使用什么修饰符？", "public", false);
        when(wrongQuestionRepository.findByUserIdAndSubjectIdAndStem(7L, 3L, request.getStem()))
                .thenReturn(Optional.empty());
        when(wrongQuestionRepository.save(org.mockito.ArgumentMatchers.any(WrongQuestion.class)))
                .thenAnswer(invocation -> {
                    WrongQuestion saved = invocation.getArgument(0);
                    saved.setId(11L);
                    return saved;
                });

        var response = service.save(request);

        ArgumentCaptor<WrongQuestion> captor = ArgumentCaptor.forClass(WrongQuestion.class);
        verify(wrongQuestionRepository).save(captor.capture());
        WrongQuestion saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getSubjectId()).isEqualTo(3L);
        assertThat(saved.getCourse()).isEqualTo("Java");
        assertThat(saved.getType()).isEqualTo("single");
        assertThat(saved.getWrongAnswer()).isEqualTo("public");
        assertThat(saved.getWrongCount()).isEqualTo(1);
        assertThat(saved.getMastered()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getLastWrongAt()).isNotNull();
        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.wrongCount()).isEqualTo(1);
    }

    @Test
    void saveDuplicateWrongAnswerIncrementsWrongCountAndUpdatesLatestAnswer() {
        loginAs(7L);
        WrongQuestionServiceImpl service = service();
        WrongQuestion existing = existingQuestion(11L, 7L);
        existing.setWrongCount(2);
        existing.setWrongAnswer("public");
        existing.setLastWrongAt(LocalDateTime.now().minusDays(1));
        WrongQuestionSaveRequest request = request("single", existing.getStem(), "protected", false);
        when(wrongQuestionRepository.findByUserIdAndSubjectIdAndStem(7L, 3L, existing.getStem()))
                .thenReturn(Optional.of(existing));
        when(wrongQuestionRepository.save(existing)).thenReturn(existing);

        var response = service.save(request);

        assertThat(existing.getWrongCount()).isEqualTo(3);
        assertThat(existing.getWrongAnswer()).isEqualTo("protected");
        assertThat(existing.getLastWrongAt()).isAfter(existing.getCreatedAt());
        assertThat(existing.getMastered()).isFalse();
        assertThat(response.wrongCount()).isEqualTo(3);
    }

    @Test
    void manualSaveOfCorrectQuestionDoesNotIncrementExistingWrongCount() {
        loginAs(7L);
        WrongQuestionServiceImpl service = service();
        WrongQuestion existing = existingQuestion(11L, 7L);
        existing.setWrongCount(2);
        WrongQuestionSaveRequest request = request("fill", existing.getStem(), "", true);
        when(wrongQuestionRepository.findByUserIdAndSubjectIdAndStem(7L, 3L, existing.getStem()))
                .thenReturn(Optional.of(existing));
        when(wrongQuestionRepository.save(existing)).thenReturn(existing);

        service.save(request);

        assertThat(existing.getWrongCount()).isEqualTo(2);
        assertThat(existing.getWrongAnswer()).isNull();
    }

    @Test
    void listReturnsOnlyCurrentUsersQuestionsFromRepository() {
        loginAs(8L);
        WrongQuestionServiceImpl service = service();
        WrongQuestion question = existingQuestion(21L, 8L);
        when(wrongQuestionRepository.findByUserIdOrderByLastWrongAtDesc(8L)).thenReturn(List.of(question));

        var responses = service.list();

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(21L);
        verify(wrongQuestionRepository).findByUserIdOrderByLastWrongAtDesc(8L);
    }

    @Test
    void markMasteredRejectsQuestionOwnedByAnotherUser() {
        loginAs(8L);
        WrongQuestionServiceImpl service = service();
        when(wrongQuestionRepository.findByIdAndUserId(11L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markMastered(11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("错题不存在或无权访问");
    }

    @Test
    void deleteRejectsQuestionOwnedByAnotherUser() {
        loginAs(8L);
        WrongQuestionServiceImpl service = service();
        when(wrongQuestionRepository.existsByIdAndUserId(11L, 8L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("错题不存在或无权访问");
        verify(wrongQuestionRepository, never()).deleteByIdAndUserId(11L, 8L);
    }

    @Test
    void reinforceRejectsQuestionOwnedByAnotherUser() {
        loginAs(8L);
        WrongQuestionServiceImpl service = service();
        when(wrongQuestionRepository.findByIdAndUserId(11L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reinforce(11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("错题不存在或无权访问");
    }

    @Test
    void reinforceGeneratesSimilarQuestionsFromOwnedQuestion() {
        loginAs(7L);
        WrongQuestionServiceImpl service = service();
        WrongQuestion question = existingQuestion(11L, 7L);
        when(wrongQuestionRepository.findByIdAndUserId(11L, 7L)).thenReturn(Optional.of(question));
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("""
                        [
                          {
                            "stem": "Java 封装常见做法是什么？",
                            "options": ["A. 暴露字段", "B. private 字段并提供方法", "C. 删除类", "D. 不写构造器"],
                            "answer": "B",
                            "analysis": "封装通常隐藏内部字段。"
                          }
                        ]
                        """);

        var questions = service.reinforce(11L);

        assertThat(questions).hasSize(1);
        assertThat(questions.getFirst().getStem()).contains("Java");
        assertThat(questions.getFirst().getOptions()).hasSize(4);
    }

    private WrongQuestionServiceImpl service() {
        return new WrongQuestionServiceImpl(wrongQuestionRepository, objectMapper, chatClient);
    }

    private void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()));
    }

    private WrongQuestionSaveRequest request(String type, String stem, String wrongAnswer, boolean manual) {
        WrongQuestionSaveRequest request = new WrongQuestionSaveRequest();
        request.setSubjectId(3L);
        request.setCourse("Java");
        request.setType(type);
        request.setStem(stem);
        request.setOptions(List.of("A. public", "B. private", "C. final", "D. static"));
        request.setAnswer("B");
        request.setAnalysis("封装通常使用 private 字段隐藏实现细节。");
        request.setWrongAnswer(wrongAnswer);
        request.setManual(manual);
        return request;
    }

    private WrongQuestion existingQuestion(Long id, Long userId) {
        WrongQuestion question = new WrongQuestion();
        question.setId(id);
        question.setUserId(userId);
        question.setSubjectId(3L);
        question.setCourse("Java");
        question.setType("single");
        question.setStem("Java 封装通常使用什么修饰符？");
        question.setOptionsJson("[\"A. public\",\"B. private\",\"C. final\",\"D. static\"]");
        question.setAnswer("B");
        question.setAnalysis("封装通常使用 private 字段隐藏实现细节。");
        question.setWrongAnswer("public");
        question.setWrongCount(1);
        question.setMastered(false);
        question.setCreatedAt(LocalDateTime.now().minusDays(2));
        question.setLastWrongAt(LocalDateTime.now().minusDays(1));
        return question;
    }
}
