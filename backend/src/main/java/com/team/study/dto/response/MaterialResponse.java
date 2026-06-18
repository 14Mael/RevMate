package com.team.study.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MaterialResponse {
    private Long id;
    private Long subjectId;
    private String filename;
    private String type;
    private String status;
    private LocalDateTime createdAt;
    private boolean previewable;
    private String previewStatus;
    private String previewMessage;
}
