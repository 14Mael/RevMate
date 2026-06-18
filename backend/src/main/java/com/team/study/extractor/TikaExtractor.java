package com.team.study.extractor;

import org.apache.tika.Tika;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Apache Tika 提取器 — 支持 txt / pdf / word
 * PDF 提取为空时自动调用视觉模型 OCR 兜底
 */
@Component
public class TikaExtractor implements ContentExtractor {

    private final Tika tika = new Tika();
    private final PdfOcrService pdfOcrService;

    public TikaExtractor(PdfOcrService pdfOcrService) {
        this.pdfOcrService = pdfOcrService;
    }

    @Override
    public boolean supports(String contentType) {
        return "txt".equals(contentType)
                || "pdf".equals(contentType)
                || "word".equals(contentType)
                || "ppt".equals(contentType)
                || "excel".equals(contentType);
    }

    @Override
    public String extract(Resource file) {
        try (InputStream inputStream = file.getInputStream()) {
            String text = tika.parseToString(inputStream);

            // PDF 提取为空 → 调用视觉模型 OCR
            if (text == null || text.isBlank()) {
                String filename = file.getFilename();
                if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
                    return pdfOcrService.extractTextFromPdf(file.getFile().toPath());
                }
            }

            return text;
        } catch (Exception e) {
            throw new RuntimeException("Tika 提取文本失败: " + file.getFilename(), e);
        }
    }
}
