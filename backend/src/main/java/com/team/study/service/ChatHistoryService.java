package com.team.study.service;

import com.team.study.dto.request.ChatHistorySaveRequest;
import com.team.study.dto.response.ChatHistoryResponse;

import java.util.List;

public interface ChatHistoryService {
    List<ChatHistoryResponse> list();

    ChatHistoryResponse save(String id, ChatHistorySaveRequest request);

    void delete(String id);

    void clearAll();
}
