package com.team.study.extractor;

import org.apache.tika.Tika;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Apache Tika 提取器 — 支持 txt / pdf / word
 */
@Component
public class TikaExtractor implements ContentExtractor {

    private final Tika tika = new Tika();

    @Override
    public boolean supports(String contentType) {
        return "txt".equals(contentType)
                || "pdf".equals(contentType)
                || "word".equals(contentType);
    }

    @Override
    public String extract(Resource file) {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.parseToString(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("Tika 提取文本失败: " + file.getFilename(), e);
        }
    }
}
