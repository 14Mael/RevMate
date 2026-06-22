package com.team.study.service;

import com.team.study.dto.request.ChatRequest;
import com.team.study.dto.response.ChatResponse;
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

    @Override
    public ChatResponse chat(ChatRequest request) {
        Long userId = validateUserAndSubject(request);

        String question = request.getQuestion();
        Long subjectId = request.getSubjectId();
        List<SourceItem> sources = new ArrayList<>();

        Long materialId = request.getMaterialId();
        log.info("RAG 请求: userId={}, subjectId={}, materialId={}", userId, subjectId, materialId);
        if (materialId != null) {
            validateMaterial(userId, subjectId, materialId);
            String context = documentIngestionService.getMaterialContext(userId, materialId, MATERIAL_CONTEXT_CHUNKS);
            int contextLength = context == null ? 0 : context.length();
            log.info("指定资料上下文: materialId={}, 上下文长度={}", materialId, contextLength);
            if (context == null || context.isBlank()) {
                log.warn("指定资料无可用切片，回落到无资料应答: materialId={}", materialId);
                return answerNoData(question);
            }
            sources.add(SourceItem.builder()
                    .type("material")
                    .title("当前选中资料")
                    .snippet(truncate(context, 150))
                    .materialId(materialId)
                    .build());
            return answerFromContext(question, context, sources);
        }

        // 1. 检索用户在当前学科下的资料
        List<Document> chunks = documentIngestionService.retrieve(userId, subjectId, question, TOP_K);
        log.info("未指定资料，走关键词检索: subjectId={}, 命中切片数={}", subjectId, chunks == null ? 0 : chunks.size());

        if (chunks != null && chunks.size() >= MIN_HITS) {
            return answerFromMaterials(question, chunks, sources);
        } else {
            log.warn("关键词检索未命中，回落到无资料应答: subjectId={}", subjectId);
            return answerNoData(question);
        }
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        Long userId = validateUserAndSubject(request);

        String question = request.getQuestion();
        Long subjectId = request.getSubjectId();
        Long materialId = request.getMaterialId();
        log.info("RAG 流式请求: userId={}, subjectId={}, materialId={}", userId, subjectId, materialId);

        if (materialId != null) {
            validateMaterial(userId, subjectId, materialId);
            String context = documentIngestionService.getMaterialContext(userId, materialId, MATERIAL_CONTEXT_CHUNKS);
            if (context == null || context.isBlank()) {
                log.warn("指定资料无可用切片，流式回落到无资料应答: materialId={}", materialId);
                return answerNoDataStream(question);
            }
            return answerFromContextStream(question, context);
        }

        List<Document> chunks = documentIngestionService.retrieve(userId, subjectId, question, TOP_K);
        log.info("未指定资料，流式关键词检索: subjectId={}, 命中切片数={}", subjectId, chunks == null ? 0 : chunks.size());
        if (chunks != null && chunks.size() >= MIN_HITS) {
            return answerFromMaterialsStream(question, chunks);
        }
        log.warn("关键词检索未命中，流式回落到无资料应答: subjectId={}", subjectId);
        return answerNoDataStream(question);
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
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            String sourceName = (String) chunk.getMetadata().getOrDefault("source", "未知资料");
            contextBuilder.append("【").append(sourceName).append("】\n")
                    .append(chunk.getText())
                    .append("\n\n");

            sources.add(SourceItem.builder()
                    .type("material")
                    .title((String) chunk.getMetadata().getOrDefault("source", "未知资料"))
                    .snippet(truncate(chunk.getText(), 150))
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

    private Flux<String> answerFromMaterialsStream(String question, List<Document> chunks) {
        StringBuilder contextBuilder = new StringBuilder();
        for (Document chunk : chunks) {
            String sourceName = (String) chunk.getMetadata().getOrDefault("source", "未知资料");
            contextBuilder.append("【").append(sourceName).append("】\n")
                    .append(chunk.getText())
                    .append("\n\n");
        }

        return answerFromContextStream(question, contextBuilder.toString());
    }

    private Flux<String> answerFromContextStream(String question, String context) {
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
                .content();
    }

    private Flux<String> answerNoDataStream(String question) {
        return chatClient.prompt()
                .system("你是一个智能助手。用户的问题目前没有相关的复习资料，请根据你的知识回答。")
                .user(question)
                .stream()
                .content();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
