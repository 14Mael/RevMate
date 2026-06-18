package com.team.study.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSubjectRequest {
    @NotBlank(message = "学科名称不能为空")
    private String name;
}
