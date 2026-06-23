package com.team.study.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.study.dto.response.ChatHistoryResponse;
import com.team.study.entity.ChatHistory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatHistoryServiceImplTest {

    private final ChatHistoryServiceImpl chatHistoryService =
            new ChatHistoryServiceImpl(null, new ObjectMapper());

    @Test
    void toResponseReturnsMaterialIdForMaterialScopedHistory() {
        ChatHistory history = new ChatHistory();
        history.setId("session-1");
        history.setUserId(5L);
        history.setTitle("资料问答");
        history.setSubjectId(7L);
        history.setMaterialId(11L);
        history.setMessagesJson("[]");
        history.setCreatedAt(LocalDateTime.now());

        ChatHistoryResponse response = ReflectionTestUtils.invokeMethod(
                chatHistoryService,
                "toResponse",
                history);

        assertThat(response).isNotNull();
        assertThat(response.getMaterialId()).isEqualTo(11L);
        assertThat(response.getSubjectId()).isEqualTo(7L);
    }
}
