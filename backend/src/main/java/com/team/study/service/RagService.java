package com.team.study.service;

import com.team.study.dto.request.ChatRequest;
import com.team.study.dto.response.ChatResponse;
import com.team.study.dto.response.ChatStreamEvent;
import reactor.core.publisher.Flux;

public interface RagService {
    ChatResponse chat(ChatRequest request);

    Flux<ChatStreamEvent> chatStream(ChatRequest request);
}
