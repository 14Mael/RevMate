package com.team.study.service;

import com.team.study.entity.Material;
import com.team.study.extractor.ExtractorRouter;
import com.team.study.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * 异步文件处理服务：提取 → 切片 → 向量入库
 * 独立为 Bean 以便 @Async 代理生效
 */
@Service
@RequiredArgsConstructor
public class FileProcessingService {

    private static final Logger log = LoggerFactory.getLogger(FileProcessingService.class);
    private static final int MAX_PREVIEW_MESSAGE_LENGTH = 255;

    private final MaterialRepository materialRepository;
    private final ExtractorRouter extractorRouter;
    private final DocumentIngestionService documentIngestionService;
    private final PdfConversionService pdfConversionService;

    @Async
    public void processFile(Long userId, Long materialId, String filename, String type, Path filePath) {
        try {
            FileSystemResource resource = new FileSystemResource(filePath);

            // 提取文本
            String extractedText = extractorRouter.extract(type, resource);

            // 切片 + 向量入库
            documentIngestionService.ingest(userId, materialId, filename, extractedText);

            // 计算预览 PDF 路径
            PreviewResult preview = resolvePreview(type, filename, filePath);

            // 更新状态为 READY 并保存 previewPath
            materialRepository.findById(materialId).ifPresent(material -> {
                material.setStatus(Material.Status.READY);
                material.setPreviewPath(preview.path());
                material.setPreviewStatus(preview.status());
                material.setPreviewMessage(preview.message());
                materialRepository.save(material);
            });

            log.info("资料处理完成: materialId={}, previewable={}, status=READY",
                    materialId, preview.path() != null);
        } catch (Exception e) {
            log.error("资料处理失败: materialId={}", materialId, e);
            materialRepository.findById(materialId).ifPresent(material -> {
                material.setStatus(Material.Status.FAILED);
                material.setPreviewStatus(Material.PreviewStatus.FAILED);
                material.setPreviewMessage(buildFailureMessage(e));
                materialRepository.save(material);
            });
        }
    }

    private String buildFailureMessage(Exception e) {
        String detail = deepestMessage(e);
        String message = detail == null || detail.isBlank()
                ? "资料处理失败，未生成预览"
                : "资料处理失败: " + detail;
        return truncate(message, MAX_PREVIEW_MESSAGE_LENGTH);
    }

    private String deepestMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    /**
     * 计算预览 PDF 路径:
     * pdf 直接用原文件;word/ppt/excel 转换;其他或失败返回 null。
     */
    private PreviewResult resolvePreview(String type, String filename, Path filePath) {
        if ("pdf".equals(type)) {
            return new PreviewResult(filePath.toString(), Material.PreviewStatus.READY, null);
        }
        if (pdfConversionService.isConvertibleType(type)) {
            try {
                return new PreviewResult(
                        pdfConversionService.convertToPdf(filePath, filename).toString(),
                        Material.PreviewStatus.READY,
                        null);
            } catch (Exception e) {
                log.warn("生成预览 PDF 失败,跳过预览: type={}, file={}, reason={}",
                        type, filename, e.getMessage());
                return new PreviewResult(
                        null,
                        Material.PreviewStatus.FAILED,
                        "PDF 预览生成失败，请确认已安装 LibreOffice 且文件未损坏");
            }
        }
        return new PreviewResult(null, Material.PreviewStatus.NONE, null);
    }

    private record PreviewResult(String path, Material.PreviewStatus status, String message) {
    }
}
