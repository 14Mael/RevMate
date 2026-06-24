package com.team.study.dto.request;

import com.team.study.dto.response.ChatHistoryMessageDto;
import lombok.Data;

import java.util.List;

/**
 * 保存（upsert）一条聊天历史。会话 id 走路径参数，body 不含 id。
 */
@Data
public class ChatHistorySaveRequest {
    private String title;
    private List<ChatHistoryMessageDto> messages;
    private Long subjectId;
    private Long materialId;
    private String course;
}
