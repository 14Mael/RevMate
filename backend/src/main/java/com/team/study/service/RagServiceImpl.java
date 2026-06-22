package com.team.study.service;

import com.team.study.dto.request.ChatRequest;
import com.team.study.dto.response.ChatResponse;
import com.team.study.dto.response.ChatStreamEvent;
import com.team.study.dto.response.SourceItem;
import com.team.study.repository.MaterialRepository;
import com.team.study.repository.SubjectRepository;
import com.team.study.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RAG 问答服务实现
 *
 * 流程：
 * 1. 检索用户资料中的相关切片
 * 2. 命中足够 → 基于资料回答（带出处）
 * 3. 命中不足 → 直接由 AI 回答（资料不足提示）
 */
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);

    private final ChatClient chatClient;
    private final DocumentIngestionService documentIngestionService;
    private final SubjectRepository subjectRepository;
    private final MaterialRepository materialRepository;

    private static final int TOP_K = 5;
    private static final int MIN_HITS = 1;
    private static final int MATERIAL_CONTEXT_CHUNKS = 8;
    private static final double MIN_MATERIAL_SCORE = 0.12;

    @Override
    public ChatResponse chat(ChatRequest request) {
        Long userId = validateUserAndSubject(request);

        String question = request.getQuestion();
        Long subjectId = request.getSubjectId();
        List<SourceItem> sources = new ArrayList<>();
        String retrievalQuery = buildRetrievalQuery(request);
        String userPrompt = buildUserPrompt(request);

        Long materialId = request.getMaterialId();
        log.info("RAG 请求: userId={}, subjectId={}, materialId={}", userId, subjectId, materialId);
        if (materialId != null) {
            validateMaterial(userId, subjectId, materialId);
            String context = documentIngestionService.getMaterialContext(userId, materialId, retrievalQuery, MATERIAL_CONTEXT_CHUNKS);
            int contextLength = context == null ? 0 : context.length();
            log.info("指定资料上下文: materialId={}, 上下文长度={}", materialId, contextLength);
            if (context == null || context.isBlank()) {
                log.warn("指定资料无可用切片，回落到无资料应答: materialId={}", materialId);
                return answerNoData(userPrompt);
            }
            sources.add(SourceItem.builder()
                    .type("material")
                    .title("当前选中资料")
                    .snippet(truncate(context, 150))
                    .materialId(materialId)
                    .build());
            return answerFromContext(userPrompt, context, sources);
        }

        // 1. 检索用户在当前学科下的资料
        List<Document> chunks = documentIngestionService.retrieve(userId, subjectId, retrievalQuery, TOP_K);
        log.info("未指定资料，走关键词检索: subjectId={}, 命中切片数={}", subjectId, chunks == null ? 0 : chunks.size());

        if (chunks != null && chunks.size() >= MIN_HITS && hasReliableMaterialScore(chunks)) {
            return answerFromMaterials(userPrompt, chunks, sources);
        } else {
            log.warn("关键词检索未命中，回落到无资料应答: subjectId={}", subjectId);
            return answerNoData(userPrompt);
        }
    }

    @Override
    public Flux<ChatStreamEvent> chatStream(ChatRequest request) {
        Long userId = validateUserAndSubject(request);

        Long subjectId = request.getSubjectId();
        Long materialId = request.getMaterialId();
        String retrievalQuery = buildRetrievalQuery(request);
        String userPrompt = buildUserPrompt(request);
        List<SourceItem> sources = new ArrayList<>();
        log.info("RAG 流式请求: userId={}, subjectId={}, materialId={}", userId, subjectId, materialId);

        if (materialId != null) {
            validateMaterial(userId, subjectId, materialId);
            String context = documentIngestionService.getMaterialContext(userId, materialId, retrievalQuery, MATERIAL_CONTEXT_CHUNKS);
            if (context == null || context.isBlank()) {
                log.warn("指定资料无可用切片，流式回落到无资料应答: materialId={}", materialId);
                return answerNoDataStream(userPrompt);
            }
            sources.add(SourceItem.builder()
                    .type("material")
                    .title("当前选中资料")
                    .snippet(truncate(context, 150))
                    .materialId(materialId)
                    .build());
            return answerFromContextStream(userPrompt, context, sources);
        }

        List<Document> chunks = documentIngestionService.retrieve(userId, subjectId, retrievalQuery, TOP_K);
        log.info("未指定资料，流式关键词检索: subjectId={}, 命中切片数={}", subjectId, chunks == null ? 0 : chunks.size());
        if (chunks != null && chunks.size() >= MIN_HITS && hasReliableMaterialScore(chunks)) {
            return answerFromMaterialsStream(userPrompt, chunks, sources);
        }
        log.warn("关键词检索未命中，流式回落到无资料应答: subjectId={}", subjectId);
        return answerNoDataStream(userPrompt);
    }

    private Long validateUserAndSubject(ChatRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }

        Long subjectId = request.getSubjectId();
        if (subjectId == null || !subjectRepository.existsByIdAndUserId(subjectId, userId)) {
            throw new IllegalArgumentException("学科不存在或无权访问");
        }
        return userId;
    }

    private void validateMaterial(Long userId, Long subjectId, Long materialId) {
        if (!materialRepository.existsByIdAndUserIdAndSubjectId(materialId, userId, subjectId)) {
            throw new IllegalArgumentException("资料不存在或不属于该学科");
        }
    }

    private ChatResponse answerFromMaterials(String question, List<Document> chunks, List<SourceItem> sources) {
        StringBuilder contextBuilder = new StringBuilder();
        for (Document chunk : chunks) {
            String sourceName = (String) chunk.getMetadata().getOrDefault("source", "未知资料");
            contextBuilder.append("【").append(sourceName).append("】\n")
                    .append(chunk.getText())
                    .append("\n\n");
        }
        addMaterialSources(chunks, sources);

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

        return new ChatResponse(answer != null ? answer : "", sources, "material");
    }

    private ChatResponse answerFromContext(String question, String context, List<SourceItem> sources) {
        String answer = chatClient.prompt()
                .system("""
                        你是一个复习资料智能助手。请基于以下参考资料回答用户的问题。
                        如果参考资料中有相关内容，请优先使用资料中的信息。

                        参考资料：
                        %s
                        """.formatted(context))
                .user(question)
                .call()
                .content();

        return new ChatResponse(answer != null ? answer : "", sources, "material");
    }

    private ChatResponse answerNoData(String question) {
        String answer = chatClient.prompt()
                .system("你是一个智能助手。没有找到可靠的资料上下文，请明确说明这不是基于上传资料的回答，再根据你的通用知识回答。")
                .user(question)
                .call()
                .content();

        return new ChatResponse(answer != null ? answer : "", List.of(), "general");
    }

    private Flux<ChatStreamEvent> answerFromMaterialsStream(String question, List<Document> chunks, List<SourceItem> sources) {
        StringBuilder contextBuilder = new StringBuilder();
        for (Document chunk : chunks) {
            String sourceName = (String) chunk.getMetadata().getOrDefault("source", "未知资料");
            contextBuilder.append("【").append(sourceName).append("】\n")
                    .append(chunk.getText())
                    .append("\n\n");
        }
        addMaterialSources(chunks, sources);

        return answerFromContextStream(question, contextBuilder.toString(), sources);
    }

    /**
     * 同一份资料会被切成多个向量块，按 materialId（缺失时回退到文件名）去重，
     * 每份资料只保留一条来源卡片，取排名最高的那个块作为摘要。
     */
    private void addMaterialSources(List<Document> chunks, List<SourceItem> sources) {
        java.util.LinkedHashMap<Object, Document> firstByMaterial = new java.util.LinkedHashMap<>();
        for (Document chunk : chunks) {
            Object key = chunk.getMetadata().get("materialId");
            if (key == null) {
                key = chunk.getMetadata().getOrDefault("source", "未知资料");
            }
            firstByMaterial.putIfAbsent(key, chunk);
        }
        for (Document chunk : firstByMaterial.values()) {
            sources.add(SourceItem.builder()
                    .type("material")
                    .title((String) chunk.getMetadata().getOrDefault("source", "未知资料"))
                    .snippet(truncate(chunk.getText(), 150))
                    .materialId(chunk.getMetadata().get("materialId") != null
                            ? Long.valueOf(chunk.getMetadata().get("materialId").toString()) : null)
                    .build());
        }
    }

    private Flux<ChatStreamEvent> answerFromContextStream(String question, String context, List<SourceItem> sources) {
        return chatClient.prompt()
                .system("""
                        你是一个复习资料智能助手。请基于以下参考资料回答用户的问题。
                        如果参考资料中有相关内容，请优先使用资料中的信息。
                        在回答末尾注明信息来源的文件名。

                        参考资料：
                        %s
                        """.formatted(context))
                .user(question)
                .stream()
                .content()
                .map(ChatStreamEvent::delta)
                .concatWithValues(ChatStreamEvent.done(sources, "material"));
    }

    private Flux<ChatStreamEvent> answerNoDataStream(String question) {
        return chatClient.prompt()
                .system("你是一个智能助手。用户的问题目前没有相关的复习资料，请根据你的知识回答。")
                .user(question)
                .stream()
                .content()
                .map(ChatStreamEvent::delta)
                .concatWithValues(ChatStreamEvent.done(List.of(), "general"));
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String buildRetrievalQuery(ChatRequest request) {
        List<String> parts = new ArrayList<>();
        if (request.getHistory() != null) {
            request.getHistory().stream()
                    .filter(message -> message != null && message.getContent() != null && !message.getContent().isBlank())
                    .skip(Math.max(0, request.getHistory().size() - 6))
                    .map(message -> message.getContent().trim())
                    .forEach(parts::add);
        }
        parts.add(request.getQuestion().trim());
        return String.join("\n", parts);
    }

    private String buildUserPrompt(ChatRequest request) {
        if (request.getHistory() == null || request.getHistory().isEmpty()) {
            return request.getQuestion();
        }
        StringBuilder builder = new StringBuilder("最近对话：\n");
        request.getHistory().stream()
                .filter(message -> message != null && message.getContent() != null && !message.getContent().isBlank())
                .skip(Math.max(0, request.getHistory().size() - 6))
                .forEach(message -> builder
                        .append(normalizeRole(message.getRole()))
                        .append("：")
                        .append(message.getContent().trim())
                        .append('\n'));
        builder.append("\n本轮问题：").append(request.getQuestion());
        return builder.toString();
    }

    private String normalizeRole(String role) {
        if (role == null) return "用户";
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "assistant" -> "助手";
            case "user" -> "用户";
            default -> "消息";
        };
    }

    private boolean hasReliableMaterialScore(List<Document> chunks) {
        double bestScore = chunks.stream()
                .mapToDouble(chunk -> {
                    Object score = chunk.getMetadata().get("score");
                    if (score instanceof Number number) {
                        return number.doubleValue();
                    }
                    return 1.0;
                })
                .max()
                .orElse(0.0);
        return bestScore >= MIN_MATERIAL_SCORE;
    }
}
