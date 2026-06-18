package com.team.study.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.study.dto.request.QuizRequest;
import com.team.study.dto.response.QuizResponse;
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

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private DocumentIngestionService documentIngestionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generateUsesMaterialContextFromUploadedDocument() {
        loginAs(7L);
        QuizServiceImpl quizService = new QuizServiceImpl(chatClient, objectMapper, documentIngestionService);
        QuizRequest request = request(99L, "single", 1);
        when(documentIngestionService.getMaterialContext(7L, 99L, 8))
                .thenReturn("这是用户上传资料中的专属考点：操作系统的进程调度。");
        var requestSpec = chatClient.prompt();
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("""
                        [
                          {
                            "stem": "进程调度属于哪个知识点？",
                            "options": ["A. 操作系统", "B. 数据库", "C. 网络", "D. 编译原理"],
                            "answer": "A",
                            "analysis": "资料中提到操作系统的进程调度。"
                          }
                        ]
                        """);
        clearInvocations(requestSpec);

        QuizResponse response = quizService.generate(request);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        verify(documentIngestionService).getMaterialContext(7L, 99L, 8);
        assertThat(promptCaptor.getValue()).contains("操作系统的进程调度");
        assertThat(promptCaptor.getValue()).doesNotContain("Transformer 架构于 2017 年由 Google 提出");
        assertThat(response.getQuestions()).hasSize(1);
        assertThat(response.getQuestions().getFirst().getStem()).isEqualTo("进程调度属于哪个知识点？");
    }

    @Test
    void generateRejectsEmptyMaterialContext() {
        loginAs(7L);
        QuizServiceImpl quizService = new QuizServiceImpl(chatClient, objectMapper, documentIngestionService);
        QuizRequest request = request(99L, "fill", 1);
        when(documentIngestionService.getMaterialContext(7L, 99L, 8)).thenReturn("  ");

        assertThatThrownBy(() -> quizService.generate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("资料未处理完成");
    }

    private void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()));
    }

    private QuizRequest request(Long materialId, String type, int count) {
        QuizRequest request = new QuizRequest();
        request.setMaterialId(materialId);
        request.setType(type);
        request.setCount(count);
        return request;
    }
}
