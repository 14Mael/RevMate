package com.team.study.service;

import com.team.study.dto.request.WrongQuestionSaveRequest;
import com.team.study.dto.response.QuestionItem;
import com.team.study.dto.response.WrongQuestionResponse;

import java.util.List;

public interface WrongQuestionService {
    List<WrongQuestionResponse> list();

    List<WrongQuestionResponse> saveBatch(List<WrongQuestionSaveRequest> requests);

    WrongQuestionResponse save(WrongQuestionSaveRequest request);

    WrongQuestionResponse markMastered(Long id);

    void delete(Long id);

    List<QuestionItem> reinforce(Long id);
}
