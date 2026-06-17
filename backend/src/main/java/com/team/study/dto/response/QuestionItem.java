package com.team.study.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionItem {
    private String stem;            // 题干
    private List<String> options;   // 单选才有，填空/简答为空
    private String answer;          // 答案
    private String analysis;        // 解析
}
