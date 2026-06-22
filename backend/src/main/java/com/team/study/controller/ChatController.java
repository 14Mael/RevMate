package com.team.study.controller;

import com.team.study.common.Result;
import com.team.study.dto.request.ChatRequest;
import com.team.study.dto.response.ChatResponse;
import com.team.study.dto.response.ChatStreamEvent;
import com.team.study.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagService ragService;

    @PostMapping
    public Result<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = ragService.chat(request);
        return Result.success(response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<ChatStreamEvent>>> chatStream(@Valid @RequestBody ChatRequest request) {
        Flux<ServerSentEvent<ChatStreamEvent>> events = ragService.chatStream(request)
                .map(event -> ServerSentEvent.<ChatStreamEvent>builder()
                        .event(event.getType())
                        .data(event)
                        .build());
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noCache().cachePrivate().noTransform())
                .header("X-Accel-Buffering", "no")
                .body(events);
    }
}
