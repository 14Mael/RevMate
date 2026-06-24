package com.team.study.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.study.dto.request.ChatHistorySaveRequest;
import com.team.study.dto.response.ChatHistoryMessageDto;
import com.team.study.dto.response.ChatHistoryResponse;
import com.team.study.entity.ChatHistory;
import com.team.study.repository.ChatHistoryRepository;
import com.team.study.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 聊天历史服务：按当前登录用户隔离，消息列表以 JSON 形式整段存取。
 */
@Service
@RequiredArgsConstructor
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private static final int MAX_TITLE_LEN = 200;
    private static final TypeReference<List<ChatHistoryMessageDto>> MESSAGE_LIST_TYPE = new TypeReference<>() {};

    private final ChatHistoryRepository chatHistoryRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ChatHistoryResponse> list() {
        Long userId = currentUserId();
        return chatHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ChatHistoryResponse save(String id, ChatHistorySaveRequest request) {
        Long userId = currentUserId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("会话 id 不能为空");
        }

        ChatHistory entity = chatHistoryRepository.findByIdAndUserId(id, userId)
                .orElseGet(() -> {
                    ChatHistory created = new ChatHistory();
                    created.setId(id);
                    created.setUserId(userId);
                    return created;
                });

        entity.setTitle(truncate(request.getTitle()));
        entity.setSubjectId(request.getSubjectId());
        entity.setMaterialId(request.getMaterialId());
        entity.setCourse(request.getCourse());
        entity.setMessagesJson(writeMessages(request.getMessages()));
        // 每次保存刷新时间，使活跃会话置顶（与前端原 localStorage 行为一致）
        entity.setCreatedAt(LocalDateTime.now());

        return toResponse(chatHistoryRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(String id) {
        Long userId = currentUserId();
        chatHistoryRepository.deleteByIdAndUserId(id, userId);
    }

    @Override
    @Transactional
    public void clearAll() {
        Long userId = currentUserId();
        chatHistoryRepository.deleteByUserId(userId);
    }

    private Long currentUserId() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        return userId;
    }

    private String truncate(String title) {
        String safe = title == null || title.isBlank() ? "新对话" : title.trim();
        return safe.length() <= MAX_TITLE_LEN ? safe : safe.substring(0, MAX_TITLE_LEN);
    }

    private String writeMessages(List<ChatHistoryMessageDto> messages) {
        List<ChatHistoryMessageDto> safe = messages == null ? Collections.emptyList() : messages;
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("聊天历史序列化失败", e);
        }
    }

    private List<ChatHistoryMessageDto> readMessages(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, MESSAGE_LIST_TYPE);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private ChatHistoryResponse toResponse(ChatHistory entity) {
        return new ChatHistoryResponse(
                entity.getId(),
                entity.getTitle(),
                readMessages(entity.getMessagesJson()),
                entity.getCreatedAt(),
                entity.getSubjectId(),
                entity.getMaterialId(),
                entity.getCourse()
        );
    }
}
