package com.team.study.service;

import com.team.study.dto.request.ChatRequest;
import com.team.study.dto.response.ChatResponse;
import com.team.study.repository.MaterialRepository;
import com.team.study.repository.SubjectRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;
    @Mock
    private DocumentIngestionService documentIngestionService;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private MaterialRepository materialRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chatUsesSelectedMaterialContextWhenMaterialIdProvided() {
        loginAs(7L);
        RagServiceImpl service = new RagServiceImpl(
                chatClient, documentIngestionService, subjectRepository, materialRepository);
        ChatRequest request = new ChatRequest();
        request.setSubjectId(4L);
        request.setMaterialId(16L);
        request.setQuestion("这份音频主要讲了什么？");
        when(subjectRepository.existsByIdAndUserId(4L, 7L)).thenReturn(true);
        when(materialRepository.existsByIdAndUserIdAndSubjectId(16L, 7L, 4L)).thenReturn(true);
        when(documentIngestionService.getMaterialContext(7L, 16L, 8))
                .thenReturn("信息系统课程介绍了输入、处理、输出以及管理信息系统的基本组成。");
        var requestSpec = chatClient.prompt();
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("这份音频主要介绍信息系统的基本组成。");
        clearInvocations(requestSpec);

        ChatResponse response = service.chat(request);

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        verify(documentIngestionService).getMaterialContext(7L, 16L, 8);
        assertThat(systemCaptor.getValue()).contains("信息系统课程介绍了输入、处理、输出");
        assertThat(response.getAnswer()).contains("信息系统");
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources().getFirst().getMaterialId()).isEqualTo(16L);
    }

    private void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()));
    }
}
