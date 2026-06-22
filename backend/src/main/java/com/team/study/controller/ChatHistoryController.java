package com.team.study.controller;

import com.team.study.common.Result;
import com.team.study.dto.request.ChatHistorySaveRequest;
import com.team.study.dto.response.ChatHistoryResponse;
import com.team.study.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 聊天历史接口（按当前登录用户隔离）。
 */
@RestController
@RequestMapping("/api/chat/history")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    @GetMapping
    public Result<List<ChatHistoryResponse>> list() {
        return Result.success(chatHistoryService.list());
    }

    @PutMapping("/{id}")
    public Result<ChatHistoryResponse> save(@PathVariable String id,
                                            @RequestBody ChatHistorySaveRequest request) {
        return Result.success(chatHistoryService.save(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        chatHistoryService.delete(id);
        return Result.success();
    }

    @DeleteMapping
    public Result<Void> clearAll() {
        chatHistoryService.clearAll();
        return Result.success();
    }
}
