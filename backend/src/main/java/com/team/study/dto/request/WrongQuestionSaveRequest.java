package com.team.study.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class WrongQuestionSaveRequest {
    @NotNull(message = "subjectId 不能为空")
    private Long subjectId;

    @NotBlank(message = "课程名称不能为空")
    private String course;

    @NotBlank(message = "题型不能为空")
    private String type;

    @NotBlank(message = "题干不能为空")
    private String stem;

    private List<String> options;

    @NotBlank(message = "正确答案不能为空")
    private String answer;

    private String analysis;

    private String wrongAnswer;

    private boolean manual;
}
