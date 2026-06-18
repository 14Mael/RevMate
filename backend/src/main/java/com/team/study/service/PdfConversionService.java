package com.team.study.service;

import org.jodconverter.core.DocumentConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * 通过 JODConverter + LibreOffice 将 Office 文档转换为 PDF。
 * 仅对 word/ppt/excel 类型生效。
 * DocumentConverter 为 null 时（LibreOffice 未安装）转换降级，不影响应用启动。
 */
@Service
public class PdfConversionService {

    private static final Logger log = LoggerFactory.getLogger(PdfConversionService.class);
    private static final Set<String> CONVERTIBLE = Set.of("word", "ppt", "excel");

    private DocumentConverter documentConverter;

    public PdfConversionService(@Autowired(required = false) DocumentConverter documentConverter) {
        this.documentConverter = documentConverter;
    }

    public boolean isConvertibleType(String type) {
        return type != null && CONVERTIBLE.contains(type);
    }

    /**
     * 将源文件转换为 PDF,输出到与源文件同目录的 preview/ 子目录。
     * @return 生成的 PDF 路径
     * @throws PdfConversionException 转换失败
     */
    public Path convertToPdf(Path source, String baseFilename) {
        if (documentConverter == null) {
            throw new PdfConversionException("JODConverter 不可用,请确认 LibreOffice 已安装", null);
        }
        try {
            Path previewDir = source.getParent().resolve("preview");
            Files.createDirectories(previewDir);
            String pdfName = stripExtension(baseFilename) + ".pdf";
            Path target = previewDir.resolve(pdfName);

            File targetFile = target.toFile();
            documentConverter.convert(source.toFile()).to(targetFile).execute();

            log.info("转换 PDF 成功: {} -> {}", source, target);
            return target;
        } catch (Exception e) {
            throw new PdfConversionException("转换 PDF 失败: " + source, e);
        }
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static class PdfConversionException extends RuntimeException {
        public PdfConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
