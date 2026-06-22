package com.team.study.controller;

import com.team.study.dto.request.ChatRequest;
import com.team.study.dto.response.ChatStreamEvent;
import com.team.study.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

class ChatControllerTest {

    @Test
    void chatStreamDisablesResponseBuffering() {
        RagService ragService = new StubRagService();
        ChatController controller = new ChatController(ragService);

        ResponseEntity<Flux<ServerSentEvent<ChatStreamEvent>>> response = controller.chatStream(new ChatRequest());

        assertThat(response.getHeaders().getCacheControl())
                .isEqualTo(CacheControl.noCache().cachePrivate().noTransform().getHeaderValue());
        assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
        assertThat(response.getBody()).isNotNull();
    }

    private static class StubRagService implements RagService {
        @Override
        public com.team.study.dto.response.ChatResponse chat(ChatRequest request) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public Flux<ChatStreamEvent> chatStream(ChatRequest request) {
            return Flux.just(ChatStreamEvent.delta("hello"));
        }
    }
}
