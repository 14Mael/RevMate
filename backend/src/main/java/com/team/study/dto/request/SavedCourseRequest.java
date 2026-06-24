package com.team.study.dto.request;

public record SavedCourseRequest(
        String title,
        String platform,
        String url,
        String reason,
        String difficulty,
        Long subjectId) {
}
