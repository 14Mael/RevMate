package com.team.study.dto.response;

import java.time.LocalDateTime;

public record SavedCourseResponse(
        Long id,
        String title,
        String platform,
        String url,
        String reason,
        String difficulty,
        Long subjectId,
        LocalDateTime createdAt) {
}
