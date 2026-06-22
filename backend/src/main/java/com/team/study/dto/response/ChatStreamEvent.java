package com.team.study.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamEvent {
    private String type;
    private String text;
    private List<SourceItem> sources;
    private String answerMode;

    public static ChatStreamEvent delta(String text) {
        return new ChatStreamEvent("delta", text, List.of(), null);
    }

    public static ChatStreamEvent done(List<SourceItem> sources, String answerMode) {
        return new ChatStreamEvent("done", null, sources, answerMode);
    }
}
