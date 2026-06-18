package com.team.study.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuizRequest {
    @NotNull(message = "subjectId 不能为空")
    private Long subjectId;

    private Long materialId;

    @NotBlank(message = "题型不能为空")
    private String type;        // single / fill / qa

    @Min(value = 1, message = "数量最少 1 道")
    @Max(value = 20, message = "数量最多 20 道")
    private int count = 5;
}
