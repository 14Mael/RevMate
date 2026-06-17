package com.team.study.service;

import com.team.study.dto.request.ChatRequest;
import com.team.study.dto.response.ChatResponse;

public interface RagService {
    ChatResponse chat(ChatRequest request);
}
