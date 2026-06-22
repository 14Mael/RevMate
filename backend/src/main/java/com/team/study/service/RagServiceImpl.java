package com.team.study.service;

import com.team.study.dto.request.ChatRequest;
import com.team.study.dto.response.ChatResponse;
import com.team.study.dto.response.SourceItem;
import com.team.study.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 问答服务实现
 * 流程：
 * 1. 检索用户资料中的相关切片
 * 2. 命中足够 → 基于资料回答（带出处）
 * 3. 命中不足 → 直接由 AI 回答（资料不足提示）
 */
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final ChatClient chatClient;
    private final DocumentIngestionService documentIngestionService;

    private static final int TOP_K = 5;

    @Override
    public ChatResponse chat(ChatRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }

        String question = request.getQuestion();
        List<SourceItem> sources = new ArrayList<>();

        // 1. 检索用户自己的资料
        List<Document> chunks = documentIngestionService.retrieve(userId, question, TOP_K);

        if (chunks != null && !chunks.isEmpty()) {
            return answerFromMaterials(question, chunks, sources);
        } else {
            return answerNoData(question);
        }
    }

    private ChatResponse answerFromMaterials(String question, List<Document> chunks, List<SourceItem> sources) {
        StringBuilder contextBuilder = new StringBuilder();
        for (Document chunk : chunks) {
            String sourceName = (String) chunk.getMetadata().getOrDefault("source", "未知资料");
            contextBuilder.append("【").append(sourceName).append("】\n")
                    .append(chunk.getText())
                    .append("\n\n");

            sources.add(SourceItem.builder()
                    .type("material")
                    .title((String) chunk.getMetadata().getOrDefault("source", "未知资料"))
                    .snippet(truncate(chunk.getText()))
                    .materialId(chunk.getMetadata().get("materialId") != null
                            ? Long.valueOf(chunk.getMetadata().get("materialId").toString()) : null)
                    .build());
        }

        String answer = chatClient.prompt()
                .system("""
                        你是一个复习资料智能助手。请基于以下参考资料回答用户的问题。
                        如果参考资料中有相关内容，请优先使用资料中的信息。
                        在回答末尾注明信息来源的文件名。

                        参考资料：
                        %s
                        """.formatted(contextBuilder.toString()))
                .user(question)
                .call()
                .content();

        return new ChatResponse(answer != null ? answer : "", sources);
    }

    private ChatResponse answerNoData(String question) {
        String answer = chatClient.prompt()
                .system("你是一个智能助手。用户的问题目前没有相关的复习资料，请根据你的知识回答。")
                .user(question)
                .call()
                .content();

        return new ChatResponse(answer != null ? answer : "", List.of());
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() <= 150 ? text : text.substring(0, 150) + "...";
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Flux.error(new IllegalArgumentException("未登录"));
        }
        String question = request.getQuestion();
        List<Document> chunks = documentIngestionService.retrieve(userId, question, TOP_K);

        if (chunks != null && !chunks.isEmpty()) {
            return answerFromMaterialsStream(question, chunks);
        } else {
            return answerNoDataStream(question);
        }
    }

    private Flux<String> answerFromMaterialsStream(String question, List<Document> chunks) {
        StringBuilder contextBuilder = new StringBuilder();
        for (Document chunk : chunks) {
            String sourceName = (String) chunk.getMetadata().getOrDefault("source", "未知资料");
            contextBuilder.append("【").append(sourceName).append("】\n")
                    .append(chunk.getText())
                    .append("\n\n");
        }

        return chatClient.prompt()
                .system("""
                        你是一个复习资料智能助手。请基于以下参考资料回答用户的问题。
                        如果参考资料中有相关内容，请优先使用资料中的信息。
                        在回答末尾注明信息来源的文件名。

                        参考资料：
                        %s
                        """.formatted(contextBuilder.toString()))
                .user(question)
                .stream()
                .content();
    }

    private Flux<String> answerNoDataStream(String question) {
        return chatClient.prompt()
                .system("你是一个智能助手。用户的问题目前没有相关的复习资料，请根据你的知识回答。")
                .user(question)
                .stream()
                .content();
    }
}
