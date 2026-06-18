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

    @Async
    public void processFile(Long userId, Long materialId, String filename, String type, Path filePath) {
        try {
            FileSystemResource resource = new FileSystemResource(filePath);

            // 提取文本
            String extractedText = extractorRouter.extract(type, resource);

            // 切片 + 向量入库
            documentIngestionService.ingest(userId, materialId, filename, extractedText);

            // 更新状态为 READY
            materialRepository.findById(materialId).ifPresent(material -> {
                material.setStatus(Material.Status.READY);
                materialRepository.save(material);
            });

            log.info("资料处理完成: materialId={}, status=READY", materialId);
        } catch (Exception e) {
            log.error("资料处理失败: materialId={}", materialId, e);
            materialRepository.findById(materialId).ifPresent(material -> {
                material.setStatus(Material.Status.FAILED);
                materialRepository.save(material);
            });
        }
    }
}
