package com.team.study.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String answer;
    private List<SourceItem> sources;
    private String answerMode;

    public ChatResponse(String answer, List<SourceItem> sources) {
        this(answer, sources, "material");
    }
}
