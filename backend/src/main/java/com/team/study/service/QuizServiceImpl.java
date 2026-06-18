package com.team.study.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.study.dto.request.QuizRequest;
import com.team.study.dto.response.QuestionItem;
import com.team.study.dto.response.QuizResponse;
import com.team.study.repository.MaterialRepository;
import com.team.study.repository.SubjectRepository;
import com.team.study.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 出题服务 — 基于 LLM 的智能出题
 */
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizServiceImpl.class);
    private static final int MATERIAL_CONTEXT_CHUNKS = 8;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final DocumentIngestionService documentIngestionService;
    private final SubjectRepository subjectRepository;
    private final MaterialRepository materialRepository;

    // ---- 三套 Prompt 模板 ----

    private static final String PROMPT_SINGLE = """
            你是一位经验丰富的出题老师。请根据以下复习资料，生成 {count} 道单选题。
            
            【复习资料】
            {context}
            
            【要求】
            1. 每题含 4 个选项（A/B/C/D），有且仅有一个正确答案
            2. 题目应覆盖资料中的核心知识点，选项要有干扰性
            3. 答案需标注正确选项字母
            4. 解析需说明每道题的解题思路或知识点
            5. 严格输出 JSON 数组，不要任何多余文字，格式如下：
            [
              {
                "stem": "题目题干",
                "options": ["A. 选项A", "B. 选项B", "C. 选项C", "D. 选项D"],
                "answer": "A",
                "analysis": "解析说明"
              }
            ]
            """;

    private static final String PROMPT_FILL = """
            你是一位经验丰富的出题老师。请根据以下复习资料，生成 {count} 道填空题。
            
            【复习资料】
            {context}
            
            【要求】
            1. 题目为填空形式，题干中用 ______ 表示空缺
            2. 答案应为简短的词或短语（一般不超过 10 个字）
            3. 解析需说明该知识点在资料中的出处或关联知识
            4. 严格输出 JSON 数组，不要任何多余文字，格式如下：
            [
              {
                "stem": "题目题干（含 ______ 占位符）",
                "answer": "填空答案",
                "analysis": "解析说明"
              }
            ]
            """;

    private static final String PROMPT_QA = """
            你是一位经验丰富的出题老师。请根据以下复习资料，生成 {count} 道简答题。
            
            【复习资料】
            {context}
            
            【要求】
            1. 题目应围绕资料中的核心概念、原理或重要结论
            2. 答案应简明扼要，点出关键要点
            3. 解析需补充答案的背景知识或易错点
            4. 严格输出 JSON 数组，不要任何多余文字，格式如下：
            [
              {
                "stem": "简答题题目",
                "answer": "参考答案",
                "analysis": "解析说明"
              }
            ]
            """;

    @Override
    public QuizResponse generate(QuizRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }

        Long subjectId = request.getSubjectId();
        if (subjectId == null || !subjectRepository.existsByIdAndUserId(subjectId, userId)) {
            throw new IllegalArgumentException("学科不存在或无权访问");
        }

        String context;
        Long materialId = request.getMaterialId();
        if (materialId != null) {
            if (!materialRepository.existsByIdAndUserIdAndSubjectId(materialId, userId, subjectId)) {
                throw new IllegalArgumentException("资料不存在或不属于该学科");
            }
            context = documentIngestionService.getMaterialContext(
                    userId, materialId, MATERIAL_CONTEXT_CHUNKS);
        } else {
            context = documentIngestionService.getSubjectContext(
                    userId, subjectId, MATERIAL_CONTEXT_CHUNKS);
        }
        if (context == null || context.isBlank()) {
            throw new IllegalArgumentException("资料未处理完成或没有可用文本，请稍后再试");
        }

        // 选择 prompt 模板
        String promptTemplate = switch (request.getType()) {
            case "single" -> PROMPT_SINGLE;
            case "fill" -> PROMPT_FILL;
            case "qa" -> PROMPT_QA;
            default -> throw new IllegalArgumentException("不支持的题型: " + request.getType() +
                    "，可选值: single / fill / qa");
        };

        // 组装完整 prompt
        String prompt = promptTemplate
                .replace("{count}", String.valueOf(request.getCount()))
                .replace("{context}", context);

        log.info("出题请求: type={}, count={}", request.getType(), request.getCount());
        log.debug("Prompt: {}", prompt);

        // 调用 LLM
        String llmOutput = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        log.debug("LLM 原始输出: {}", llmOutput);

        // 清洗并解析 JSON
        List<QuestionItem> questions = parseQuestions(llmOutput);

        // 截断到用户要求的数量
        if (questions.size() > request.getCount()) {
            questions = questions.subList(0, request.getCount());
        }

        log.info("出题完成: 实际生成 {} 道", questions.size());
        return new QuizResponse(questions);
    }

    /**
     * 从 LLM 输出中提取并解析题目 JSON。
     * 处理 LLM 偶尔输出的多余内容（如 markdown 代码块、解释文字等）。
     */
    private List<QuestionItem> parseQuestions(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            log.warn("LLM 返回空内容");
            return Collections.emptyList();
        }

        String json = extractJson(llmOutput);
        try {
            // 先用 List<Map> 解析以容忍多余字段，再转换为 QuestionItem
            List<Map<String, Object>> rawList = objectMapper.readValue(
                    json, new TypeReference<List<Map<String, Object>>>() {});
            return rawList.stream().map(this::mapToQuestionItem).toList();
        } catch (JsonProcessingException e) {
            log.error("解析 LLM 输出失败，原始内容: {}", llmOutput, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从 LLM 输出中提取纯 JSON 部分。
     * 处理常见情况：```json ... ```、``` ... ```、前后文字夹杂。
     */
    private String extractJson(String raw) {
        String trimmed = raw.trim();

        // 情况1: ```json ... ``` 或 ``` ... ```
        if (trimmed.contains("```")) {
            int start = trimmed.indexOf("```");
            int contentStart = trimmed.indexOf('\n', start) + 1;
            int end = trimmed.indexOf("```", contentStart);
            if (end > start && contentStart > start) {
                trimmed = trimmed.substring(contentStart, end).trim();
            }
        }

        // 情况2: 以 [ 开头，找最后一个 ] 作为结束（处理末尾多余文字）
        int arrayStart = trimmed.indexOf('[');
        int arrayEnd = trimmed.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            trimmed = trimmed.substring(arrayStart, arrayEnd + 1);
        }

        return trimmed;
    }

    /**
     * 将 Map 转换为 QuestionItem，兼容 LLM 输出中可能的字段差异。
     */
    private QuestionItem mapToQuestionItem(Map<String, Object> map) {
        // options 可能是 List<String>，也可能是 JSON 解析后的格式
        @SuppressWarnings("unchecked")
        List<String> options = map.containsKey("options") && map.get("options") != null
                ? ((List<?>) map.get("options")).stream().map(Object::toString).toList()
                : null;

        return QuestionItem.builder()
                .stem(toString(map.get("stem")))
                .options(options)
                .answer(toString(map.get("answer")))
                .analysis(toString(map.get("analysis")))
                .build();
    }

    private String toString(Object obj) {
        return obj != null ? obj.toString() : "";
    }
}
