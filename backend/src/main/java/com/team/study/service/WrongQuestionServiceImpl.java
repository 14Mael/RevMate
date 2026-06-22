package com.team.study.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.study.dto.request.WrongQuestionSaveRequest;
import com.team.study.dto.response.QuestionItem;
import com.team.study.dto.response.WrongQuestionResponse;
import com.team.study.entity.WrongQuestion;
import com.team.study.repository.WrongQuestionRepository;
import com.team.study.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WrongQuestionServiceImpl implements WrongQuestionService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> QUESTION_MAP_LIST_TYPE = new TypeReference<>() {};

    private final WrongQuestionRepository wrongQuestionRepository;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    @Override
    @Transactional(readOnly = true)
    public List<WrongQuestionResponse> list() {
        Long userId = currentUserId();
        return wrongQuestionRepository.findByUserIdOrderByLastWrongAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<WrongQuestionResponse> saveBatch(List<WrongQuestionSaveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        return requests.stream().map(this::save).toList();
    }

    @Override
    @Transactional
    public WrongQuestionResponse save(WrongQuestionSaveRequest request) {
        Long userId = currentUserId();
        validate(request);
        try {
            return toResponse(upsert(userId, request));
        } catch (DataIntegrityViolationException e) {
            return toResponse(updateExisting(userId, request));
        }
    }

    @Override
    @Transactional
    public WrongQuestionResponse markMastered(Long id) {
        Long userId = currentUserId();
        WrongQuestion question = ownedQuestion(id, userId);
        question.setMastered(true);
        return toResponse(wrongQuestionRepository.save(question));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long userId = currentUserId();
        if (!wrongQuestionRepository.existsByIdAndUserId(id, userId)) {
            throw new IllegalArgumentException("错题不存在或无权访问");
        }
        wrongQuestionRepository.deleteByIdAndUserId(id, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionItem> reinforce(Long id) {
        Long userId = currentUserId();
        WrongQuestion question = ownedQuestion(id, userId);
        String prompt = buildReinforcePrompt(question);
        String llmOutput = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        return parseQuestions(llmOutput).stream().limit(3).toList();
    }

    private WrongQuestion upsert(Long userId, WrongQuestionSaveRequest request) {
        return wrongQuestionRepository.findByUserIdAndSubjectIdAndStem(userId, request.getSubjectId(), request.getStem())
                .or(() -> wrongQuestionRepository.findByUserIdAndSubjectIdAndStemHash(
                        userId, request.getSubjectId(), stemHash(request.getStem())))
                .map(existing -> applyExisting(existing, request))
                .orElseGet(() -> createNew(userId, request));
    }

    private WrongQuestion updateExisting(Long userId, WrongQuestionSaveRequest request) {
        WrongQuestion existing = wrongQuestionRepository
                .findByUserIdAndSubjectIdAndStemHash(userId, request.getSubjectId(), stemHash(request.getStem()))
                .orElseThrow(() -> new IllegalStateException("错题保存冲突，请重试"));
        return applyExisting(existing, request);
    }

    private WrongQuestion applyExisting(WrongQuestion existing, WrongQuestionSaveRequest request) {
        existing.setCourse(request.getCourse().trim());
        existing.setType(request.getType());
        existing.setOptionsJson(writeOptions(request.getOptions()));
        existing.setAnswer(request.getAnswer().trim());
        existing.setAnalysis(trimToNull(request.getAnalysis()));
        if (request.isManual()) {
            existing.setWrongAnswer(null);
        } else {
            existing.setWrongAnswer(trimToNull(request.getWrongAnswer()));
            existing.setWrongCount(existing.getWrongCount() + 1);
            existing.setLastWrongAt(LocalDateTime.now());
            existing.setMastered(false);
        }
        return wrongQuestionRepository.save(existing);
    }

    private WrongQuestion createNew(Long userId, WrongQuestionSaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        WrongQuestion question = new WrongQuestion();
        question.setUserId(userId);
        question.setSubjectId(request.getSubjectId());
        question.setCourse(request.getCourse().trim());
        question.setType(request.getType());
        question.setStem(request.getStem().trim());
        question.setStemHash(stemHash(request.getStem()));
        question.setOptionsJson(writeOptions(request.getOptions()));
        question.setAnswer(request.getAnswer().trim());
        question.setAnalysis(trimToNull(request.getAnalysis()));
        question.setWrongAnswer(request.isManual() ? null : trimToNull(request.getWrongAnswer()));
        question.setWrongCount(request.isManual() ? 0 : 1);
        question.setMastered(false);
        question.setCreatedAt(now);
        question.setLastWrongAt(now);
        return wrongQuestionRepository.save(question);
    }

    private WrongQuestion ownedQuestion(Long id, Long userId) {
        return wrongQuestionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("错题不存在或无权访问"));
    }

    private void validate(WrongQuestionSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("错题不能为空");
        }
        if (!"single".equals(request.getType()) && !"fill".equals(request.getType())) {
            throw new IllegalArgumentException("只支持 single / fill 题型");
        }
        if ("single".equals(request.getType()) && (request.getOptions() == null || request.getOptions().isEmpty())) {
            throw new IllegalArgumentException("选择题选项不能为空");
        }
    }

    private Long currentUserId() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        return userId;
    }

    private String buildReinforcePrompt(WrongQuestion question) {
        return """
                你是一位经验丰富的出题老师。请基于下面这道错题，生成 2-3 道同题型、同知识点但题干不同的举一反三练习题。

                【题型】
                %s

                【原题】
                %s

                【选项】
                %s

                【正确答案】
                %s

                【解析】
                %s

                严格输出 JSON 数组，不要任何多余文字，格式如下：
                [
                  {
                    "stem": "题目题干",
                    "options": ["A. 选项A", "B. 选项B", "C. 选项C", "D. 选项D"],
                    "answer": "A",
                    "analysis": "解析说明"
                  }
                ]
                """.formatted(
                question.getType(),
                question.getStem(),
                question.getOptionsJson(),
                question.getAnswer(),
                question.getAnalysis() == null ? "" : question.getAnalysis()
        );
    }

    private List<QuestionItem> parseQuestions(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(extractJson(llmOutput), QUESTION_MAP_LIST_TYPE)
                    .stream()
                    .map(this::mapToQuestionItem)
                    .toList();
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.contains("```")) {
            int start = trimmed.indexOf("```");
            int contentStart = trimmed.indexOf('\n', start) + 1;
            int end = trimmed.indexOf("```", contentStart);
            if (end > start && contentStart > start) {
                trimmed = trimmed.substring(contentStart, end).trim();
            }
        }
        int arrayStart = trimmed.indexOf('[');
        int arrayEnd = trimmed.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            trimmed = trimmed.substring(arrayStart, arrayEnd + 1);
        }
        return trimmed;
    }

    private QuestionItem mapToQuestionItem(Map<String, Object> map) {
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

    private WrongQuestionResponse toResponse(WrongQuestion question) {
        return new WrongQuestionResponse(
                question.getId(),
                question.getSubjectId(),
                question.getCourse(),
                question.getType(),
                question.getStem(),
                readOptions(question.getOptionsJson()),
                question.getAnswer(),
                question.getAnalysis(),
                question.getWrongAnswer(),
                question.getWrongCount(),
                question.getMastered(),
                question.getCreatedAt(),
                question.getLastWrongAt()
        );
    }

    private String writeOptions(List<String> options) {
        if (options == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("选项序列化失败", e);
        }
    }

    private List<String> readOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(optionsJson, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private String stemHash(String stem) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(stem.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String toString(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
