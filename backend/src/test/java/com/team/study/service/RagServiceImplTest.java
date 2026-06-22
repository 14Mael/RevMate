package com.team.study.service;

import com.team.study.dto.request.ChatRequest;
import com.team.study.dto.request.ChatHistoryMessage;
import com.team.study.dto.response.ChatResponse;
import com.team.study.dto.response.ChatStreamEvent;
import com.team.study.repository.MaterialRepository;
import com.team.study.repository.SubjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;
    @Mock
    private DocumentIngestionService documentIngestionService;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private MaterialRepository materialRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reliableMaterialScoreAcceptsVectorScoreAboveThreshold() {
        Document chunk = ragChunk(Map.of("vectorScore", 0.6, "keywordScore", 0.0));

        assertThat(RagServiceImpl.hasReliableMaterialScore(List.of(chunk))).isTrue();
    }

    @Test
    void reliableMaterialScoreRejectsVectorScoreBelowThreshold() {
        Document chunk = ragChunk(Map.of("vectorScore", 0.25, "keywordScore", 1.0));

        assertThat(RagServiceImpl.hasReliableMaterialScore(List.of(chunk))).isFalse();
    }

    @Test
    void reliableMaterialScoreFallsBackToStrongKeywordScoreWithoutVectorSignal() {
        Document chunk = ragChunk(Map.of("vectorScore", 0.0, "keywordScore", 1.0));

        assertThat(RagServiceImpl.hasReliableMaterialScore(List.of(chunk))).isTrue();
    }

    @Test
    void reliableMaterialScoreRejectsWeakKeywordScoreWithoutVectorSignal() {
        Document chunk = ragChunk(Map.of("vectorScore", 0.0, "keywordScore", 0.1));

        assertThat(RagServiceImpl.hasReliableMaterialScore(List.of(chunk))).isFalse();
    }

    @Test
    void reliableMaterialScoreAcceptsVectorScoreAtThreshold() {
        Document chunk = ragChunk(Map.of("vectorScore", 0.4, "keywordScore", 0.0));

        assertThat(RagServiceImpl.hasReliableMaterialScore(List.of(chunk))).isTrue();
    }

    @Test
    void filterRelevantChunksDropsLowScoreOffTopicChunk() {
        // 模拟「帮我总结第6章内容」：第6章切片向量分高，第3章切片被字符 n-gram 误召回、分低
        Document chapter6 = ragChunk(Map.of("source", "第6章 缺陷报告与测试评估.pdf",
                "vectorScore", 0.55, "keywordScore", 0.4));
        Document chapter3 = ragChunk(Map.of("source", "第3章 黑盒测试.pdf",
                "vectorScore", 0.22, "keywordScore", 0.2));

        List<Document> relevant = RagServiceImpl.filterRelevantChunks(List.of(chapter6, chapter3));

        assertThat(relevant).extracting(d -> d.getMetadata().get("source"))
                .containsExactly("第6章 缺陷报告与测试评估.pdf");
    }

    @Test
    void filterRelevantChunksKeepsTopChunkWhenAllBelowThreshold() {
        Document best = ragChunk(Map.of("source", "a.pdf", "vectorScore", 0.39, "keywordScore", 0.1));
        Document worse = ragChunk(Map.of("source", "b.pdf", "vectorScore", 0.30, "keywordScore", 0.1));

        List<Document> relevant = RagServiceImpl.filterRelevantChunks(List.of(best, worse));

        assertThat(relevant).extracting(d -> d.getMetadata().get("source")).containsExactly("a.pdf");
    }

    @Test
    void filterRelevantChunksUsesKeywordScoreWithoutVectorSignal() {
        Document strong = ragChunk(Map.of("source", "a.pdf", "vectorScore", 0.0, "keywordScore", 1.0));
        Document weak = ragChunk(Map.of("source", "b.pdf", "vectorScore", 0.0, "keywordScore", 0.05));

        List<Document> relevant = RagServiceImpl.filterRelevantChunks(List.of(strong, weak));

        assertThat(relevant).extracting(d -> d.getMetadata().get("source")).containsExactly("a.pdf");
    }

    @Test
    void chatUsesSelectedMaterialContextWhenMaterialIdProvided() {
        loginAs(7L);
        RagServiceImpl service = new RagServiceImpl(
                chatClient, documentIngestionService, subjectRepository, materialRepository);
        ChatRequest request = new ChatRequest();
        request.setSubjectId(4L);
        request.setMaterialId(16L);
        request.setQuestion("这份音频主要讲了什么？");
        when(subjectRepository.existsByIdAndUserId(4L, 7L)).thenReturn(true);
        when(materialRepository.existsByIdAndUserIdAndSubjectId(16L, 7L, 4L)).thenReturn(true);
        when(documentIngestionService.getMaterialContext(7L, 16L, "这份音频主要讲了什么？", 8))
                .thenReturn("信息系统课程介绍了输入、处理、输出以及管理信息系统的基本组成。");
        var requestSpec = chatClient.prompt();
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("这份音频主要介绍信息系统的基本组成。");
        clearInvocations(requestSpec);

        ChatResponse response = service.chat(request);

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        verify(documentIngestionService).getMaterialContext(7L, 16L, "这份音频主要讲了什么？", 8);
        assertThat(systemCaptor.getValue()).contains("信息系统课程介绍了输入、处理、输出");
        assertThat(response.getAnswer()).contains("信息系统");
        assertThat(response.getAnswerMode()).isEqualTo("material");
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources().getFirst().getMaterialId()).isEqualTo(16L);
    }

    @Test
    void chatUsesHistoryForRetrievalAndConversationContext() {
        loginAs(7L);
        RagServiceImpl service = new RagServiceImpl(
                chatClient, documentIngestionService, subjectRepository, materialRepository);
        ChatRequest request = new ChatRequest();
        request.setSubjectId(4L);
        request.setQuestion("它有什么优缺点？");
        request.setHistory(List.of(
                new ChatHistoryMessage("user", "刚才那页讲的是轮转调度算法"),
                new ChatHistoryMessage("assistant", "轮转调度按时间片公平分配 CPU")
        ));
        when(subjectRepository.existsByIdAndUserId(4L, 7L)).thenReturn(true);
        when(documentIngestionService.retrieve(7L, 4L, "刚才那页讲的是轮转调度算法\n轮转调度按时间片公平分配 CPU\n它有什么优缺点？", 5))
                .thenReturn(List.of(new Document("轮转调度优点是公平，缺点是时间片过小会增加切换开销。",
                        Map.of("source", "os.pdf", "materialId", "11",
                                "score", 0.6, "keywordScore", 0.3, "vectorScore", 0.6))));
        var requestSpec = chatClient.prompt();
        when(chatClient.prompt().system(anyString()).user(contains("刚才那页讲的是轮转调度算法")).call().content())
                .thenReturn("轮转调度较公平，但时间片过小会增加上下文切换开销。");
        clearInvocations(requestSpec);

        ChatResponse response = service.chat(request);

        verify(documentIngestionService).retrieve(7L, 4L,
                "刚才那页讲的是轮转调度算法\n轮转调度按时间片公平分配 CPU\n它有什么优缺点？", 5);
        assertThat(response.getAnswer()).contains("轮转调度");
        assertThat(response.getAnswerMode()).isEqualTo("material");
    }

    @Test
    void chatReturnsGeneralAnswerModeWhenNoMaterialContextFound() {
        loginAs(7L);
        RagServiceImpl service = new RagServiceImpl(
                chatClient, documentIngestionService, subjectRepository, materialRepository);
        ChatRequest request = new ChatRequest();
        request.setSubjectId(4L);
        request.setQuestion("量子力学是什么？");
        when(subjectRepository.existsByIdAndUserId(4L, 7L)).thenReturn(true);
        when(documentIngestionService.retrieve(7L, 4L, "量子力学是什么？", 5)).thenReturn(List.of());
        var requestSpec = chatClient.prompt();
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("没有在资料中找到可靠上下文。量子力学是研究微观粒子的理论。");
        clearInvocations(requestSpec);

        ChatResponse response = service.chat(request);

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        assertThat(systemCaptor.getValue()).contains("没有找到可靠的资料上下文");
        assertThat(response.getAnswerMode()).isEqualTo("general");
        assertThat(response.getSources()).isEmpty();
    }

    @Test
    void chatReturnsGeneralAnswerModeWhenRetrievedScoreIsTooLow() {
        loginAs(7L);
        RagServiceImpl service = new RagServiceImpl(
                chatClient, documentIngestionService, subjectRepository, materialRepository);
        ChatRequest request = new ChatRequest();
        request.setSubjectId(4L);
        request.setQuestion("请总结并发控制和 CPU 分配的核心知识点");
        org.springframework.ai.document.Document weakMatch = new org.springframework.ai.document.Document(
                "这段资料只零散提到了操作系统。",
                Map.of("source", "os.pdf", "materialId", "11",
                        "score", 0.45, "keywordScore", 1.0, "vectorScore", 0.25)
        );
        when(subjectRepository.existsByIdAndUserId(4L, 7L)).thenReturn(true);
        when(documentIngestionService.retrieve(7L, 4L, "请总结并发控制和 CPU 分配的核心知识点", 5))
                .thenReturn(List.of(weakMatch));
        var requestSpec = chatClient.prompt();
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("没有在资料中找到可靠上下文。并发控制和 CPU 分配分别对应同步与调度相关知识。");
        clearInvocations(requestSpec);

        ChatResponse response = service.chat(request);

        assertThat(response.getAnswerMode()).isEqualTo("general");
        assertThat(response.getSources()).isEmpty();
    }

    @Test
    void chatStreamEmitsDeltaChunksAndDoneEventWithSources() {
        loginAs(7L);
        RagServiceImpl service = new RagServiceImpl(
                chatClient, documentIngestionService, subjectRepository, materialRepository);
        ChatRequest request = new ChatRequest();
        request.setSubjectId(4L);
        request.setMaterialId(16L);
        request.setQuestion("这份资料讲了什么？");
        when(subjectRepository.existsByIdAndUserId(4L, 7L)).thenReturn(true);
        when(materialRepository.existsByIdAndUserIdAndSubjectId(16L, 7L, 4L)).thenReturn(true);
        when(documentIngestionService.getMaterialContext(7L, 16L, "这份资料讲了什么？", 8))
                .thenReturn("资料介绍了操作系统调度。");
        when(chatClient.prompt().system(anyString()).user(anyString()).stream().content())
                .thenReturn(reactor.core.publisher.Flux.just("资料", "讲了调度"));

        List<ChatStreamEvent> events = service.chatStream(request).collectList().block();

        assertThat(events).extracting(ChatStreamEvent::getType)
                .containsExactly("delta", "delta", "done");
        assertThat(events).extracting(ChatStreamEvent::getText)
                .containsExactly("资料", "讲了调度", null);
        ChatStreamEvent done = events.getLast();
        assertThat(done.getAnswerMode()).isEqualTo("material");
        assertThat(done.getSources()).hasSize(1);
        assertThat(done.getSources().getFirst().getMaterialId()).isEqualTo(16L);
    }

    private void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()));
    }

    private Document ragChunk(Map<String, Object> metadata) {
        return new Document("资料切片", metadata);
    }
}
