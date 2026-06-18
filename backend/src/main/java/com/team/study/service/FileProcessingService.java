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
            String previewPath = resolvePreviewPath(type, filename, filePath);

            // 更新状态为 READY 并保存 previewPath
            materialRepository.findById(materialId).ifPresent(material -> {
                material.setStatus(Material.Status.READY);
                material.setPreviewPath(previewPath);
                materialRepository.save(material);
            });

            log.info("资料处理完成: materialId={}, previewable={}, status=READY",
                    materialId, previewPath != null);
        } catch (Exception e) {
            log.error("资料处理失败: materialId={}", materialId, e);
            materialRepository.findById(materialId).ifPresent(material -> {
                material.setStatus(Material.Status.FAILED);
                materialRepository.save(material);
            });
        }
    }

    /**
     * 计算预览 PDF 路径:
     * pdf 直接用原文件;word/ppt/excel 转换;其他或失败返回 null。
     */
    private String resolvePreviewPath(String type, String filename, Path filePath) {
        if ("pdf".equals(type)) {
            return filePath.toString();
        }
        if (pdfConversionService.isConvertibleType(type)) {
            try {
                return pdfConversionService.convertToPdf(filePath, filename).toString();
            } catch (Exception e) {
                log.warn("生成预览 PDF 失败,跳过预览: type={}, file={}", type, filename, e);
                return null;
            }
        }
        return null;
    }
}
