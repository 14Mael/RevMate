package com.team.study.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.team.study.dto.request.ChatRequest;
import com.team.study.dto.response.ChatResponse;
import com.team.study.dto.response.SourceItem;
import com.team.study.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 问答服务实现
 *
 * 流程：
 * 1. 检索用户资料中的相关切片
 * 2. 命中足够 → 基于资料回答（带出处）
 * 3. 命中不足 → 联网搜索回答（带网页来源）
 */
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);

    private final ChatClient chatClient;
    private final DocumentIngestionService documentIngestionService;

    /** 检索返回的最相关切片数 */
    private static final int TOP_K = 5;

    /** 最低命中数：低于此值则走联网兜底 */
    private static final int MIN_HITS = 1;

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

        if (chunks != null && chunks.size() >= MIN_HITS) {
            // 2. 命中资料 → 基于资料回答
            return answerFromMaterials(question, chunks, sources);
        } else {
            // 3. 资料不足 → 联网搜索兜底
            return answerFromWeb(question);
        }
    }

    /**
     * 基于检索到的资料切片回答问题
     */
    private ChatResponse answerFromMaterials(String question, List<Document> chunks, List<SourceItem> sources) {
        // 构建上下文
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            contextBuilder.append("【片段").append(i + 1).append("】\n")
                    .append(chunk.getText())
                    .append("\n\n");

            // 构建来源信息
            sources.add(SourceItem.builder()
                    .type("material")
                    .title((String) chunk.getMetadata().getOrDefault("source", "未知资料"))
                    .snippet(truncate(chunk.getText(), 150))
                    .materialId(chunk.getMetadata().get("materialId") != null
                            ? Long.valueOf(chunk.getMetadata().get("materialId").toString()) : null)
                    .build());
        }

        String context = contextBuilder.toString();

        // 组装 prompt 并调用 AI
        String answer = chatClient.prompt()
                .system("""
                        你是一个复习资料智能助手。请基于以下参考资料回答用户的问题。
                        如果参考资料中有相关内容，请优先使用资料中的信息。
                        在回答末尾注明信息来源的片段编号。

                        参考资料：
                        %s
                        """.formatted(context))
                .user(question)
                .call()
                .content();

        return new ChatResponse(answer != null ? answer : "", sources);
    }

    /**
     * 资料不足时，联网搜索回答
     */
    private ChatResponse answerFromWeb(String question) {
        log.info("资料不足，启用联网搜索: question={}", question);

        // 启用 DashScope 的联网搜索能力
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withEnableSearch(true)
                .build();

        String answer = chatClient.prompt()
                .options(options)
                .system("你是一个智能助手。用户的问题超出了现有复习资料的范围，请使用联网搜索能力来回答。")
                .user(question)
                .call()
                .content();

        List<SourceItem> sources = List.of(
                SourceItem.builder()
                        .type("web")
                        .title("联网搜索")
                        .snippet("此回答来源于联网搜索，仅供参考")
                        .build()
        );

        return new ChatResponse(answer != null ? answer : "", sources);
    }

    /**
     * 截断文本到指定长度
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
