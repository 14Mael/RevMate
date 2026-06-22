package com.team.study.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record WrongQuestionResponse(
        Long id,
        Long subjectId,
        String course,
        String type,
        String stem,
        List<String> options,
        String answer,
        String analysis,
        String wrongAnswer,
        Integer wrongCount,
        Boolean mastered,
        LocalDateTime createdAt,
        LocalDateTime lastWrongAt
) {
}
