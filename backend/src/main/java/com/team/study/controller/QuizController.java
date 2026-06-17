package com.team.study.controller;

import com.team.study.common.Result;
import com.team.study.dto.request.QuizRequest;
import com.team.study.dto.response.QuizResponse;
import com.team.study.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping
    public Result<QuizResponse> generate(@Valid @RequestBody QuizRequest request) {
        QuizResponse response = quizService.generate(request);
        return Result.success(response);
    }
}
