package com.team.study.service;

import com.team.study.dto.request.QuizRequest;
import com.team.study.dto.response.QuestionItem;
import com.team.study.dto.response.QuizResponse;
import com.team.study.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 出题服务 — 桩实现，后续由成员3 实现真正逻辑
 */
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    @Override
    public QuizResponse generate(QuizRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }

        // TODO 成员3 实现真正的出题逻辑
        // 1. 调成员2 的 getMaterialContext(userId, materialId, maxChunks) 取上下文
        // 2. 根据 type 选择对应 prompt 模板
        // 3. 调 LLM 出题，解析 JSON 返回

        return new QuizResponse(List.of(
                QuestionItem.builder()
                        .stem("这是桩返回的示例题目。成员3 实现后将基于您的资料生成真实题目。")
                        .options(List.of("A. 选项1", "B. 选项2", "C. 选项3", "D. 选项4"))
                        .answer("A")
                        .analysis("这是答案解析")
                        .build()
        ));
    }
}
