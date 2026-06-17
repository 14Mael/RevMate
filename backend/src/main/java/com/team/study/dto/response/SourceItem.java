package com.team.study.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceItem {
    private String type;        // material / web
    private String title;
    private String snippet;
    private String url;         // web 来源必带
    private Long materialId;    // 资料来源带
    private String page;        // 有则填
}
