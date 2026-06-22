package com.team.study.controller;

import com.team.study.common.Result;
import com.team.study.dto.request.WrongQuestionSaveRequest;
import com.team.study.dto.response.QuestionItem;
import com.team.study.dto.response.WrongQuestionResponse;
import com.team.study.service.WrongQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wrong-questions")
@RequiredArgsConstructor
public class WrongQuestionController {

    private final WrongQuestionService wrongQuestionService;

    @GetMapping
    public Result<List<WrongQuestionResponse>> list() {
        return Result.success(wrongQuestionService.list());
    }

    @PostMapping("/batch")
    public Result<List<WrongQuestionResponse>> saveBatch(@Valid @RequestBody List<WrongQuestionSaveRequest> requests) {
        return Result.success(wrongQuestionService.saveBatch(requests));
    }

    @PostMapping
    public Result<WrongQuestionResponse> save(@Valid @RequestBody WrongQuestionSaveRequest request) {
        return Result.success(wrongQuestionService.save(request));
    }

    @PatchMapping("/{id}/master")
    public Result<WrongQuestionResponse> markMastered(@PathVariable Long id) {
        return Result.success(wrongQuestionService.markMastered(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wrongQuestionService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/reinforce")
    public Result<List<QuestionItem>> reinforce(@PathVariable Long id) {
        return Result.success(wrongQuestionService.reinforce(id));
    }
}
