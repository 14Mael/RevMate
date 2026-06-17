package com.team.study.service;

import com.team.study.dto.request.QuizRequest;
import com.team.study.dto.response.QuizResponse;

public interface QuizService {
    QuizResponse generate(QuizRequest request);
}
