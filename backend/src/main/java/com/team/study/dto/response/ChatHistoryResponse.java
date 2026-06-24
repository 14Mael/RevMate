package com.team.study.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryResponse {
    private String id;
    private String title;
    private List<ChatHistoryMessageDto> messages;
    private LocalDateTime createdAt;
    private Long subjectId;
    private Long materialId;
    private String course;
}
