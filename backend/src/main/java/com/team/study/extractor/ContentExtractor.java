package com.team.study.extractor;

import org.springframework.core.io.Resource;

/**
 * 内容提取器接口 — 可插拔，新增一种文件类型 = 新增一个实现类
 */
public interface ContentExtractor {

    /**
     * 是否支持该内容类型
     */
    boolean supports(String contentType);

    /**
     * 将文件提取为纯文本
     */
    String extract(Resource file);
}
