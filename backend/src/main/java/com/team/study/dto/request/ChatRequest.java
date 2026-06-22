package com.team.study.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatRequest {
    @NotNull(message = "subjectId 不能为空")
    private Long subjectId;

    private Long materialId;

    @NotBlank(message = "问题不能为空")
    private String question;
}
