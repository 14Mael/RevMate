package com.team.study.service;

import com.team.study.dto.request.ChatRequest;
import com.team.study.dto.response.ChatResponse;
import reactor.core.publisher.Flux;

public interface RagService {
    ChatResponse chat(ChatRequest request);

    Flux<String> chatStream(ChatRequest request);
}
