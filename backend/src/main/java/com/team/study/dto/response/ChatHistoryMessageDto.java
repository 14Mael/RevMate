package com.team.study.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条聊天消息，请求与响应共用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryMessageDto {
    private String role;
    private String content;
    private String timestamp;
}
