package com.team.study.service;

import com.team.study.dto.request.ChatRequest;
import com.team.study.dto.response.ChatResponse;
import com.team.study.dto.response.SourceItem;
import com.team.study.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 问答服务 — 桩实现，后续由成员2 实现真正逻辑
 */
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    @Override
    public ChatResponse chat(ChatRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }

        // TODO 成员2 实现真正的 RAG 检索 + AI 问答 + 联网兜底
        // 1. 对问题做 embedding，按 userId 过滤检索 Top-K 切片
        // 2. 命中足够 → 组装 prompt → ChatClient 生成回答 + 原文出处
        // 3. 命中不足 → 调用通义 enable_search 联网作答
        // 4. 返回 ChatResponse(answer, sources)

        return new ChatResponse(
                "这是 RAG 问答的桩返回。成员2 实现后将基于您的资料给出带出处的回答。",
                List.of(new SourceItem("material", "示例资料", "这是原文片段...", null, 1L, "1"))
        );
    }
}
