package com.team.study.service;

import com.team.study.dto.response.MaterialResponse;
import com.team.study.entity.Material;
import com.team.study.extractor.ExtractorRouter;
import com.team.study.repository.MaterialRepository;
import com.team.study.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private static final Logger log = LoggerFactory.getLogger(MaterialServiceImpl.class);

    private final MaterialRepository materialRepository;
    private final ExtractorRouter extractorRouter;
    private final DocumentIngestionService documentIngestionService;
    private final FileProcessingService fileProcessingService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** 允许的文件 MIME 类型前缀（基于文件内容校验） */
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "text/plain",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "image/"
    );

    private final Tika tika = new Tika();

    @Override
    public MaterialResponse upload(MultipartFile file) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }

        // 1. MIME 类型校验（基于文件内容，不信任扩展名）
        String mimeType = detectMimeType(file);
        if (!isAllowedMimeType(mimeType)) {
            log.warn("不支持的文件类型: mime={}, filename={}", mimeType, file.getOriginalFilename());
            throw new IllegalArgumentException("不支持的文件类型: " + file.getOriginalFilename());
        }
        String type = mimeTypeToContentType(mimeType);

        // 2. 安全化文件名
        String safeFilename = sanitizeFilename(file.getOriginalFilename());

        // 3. 保存文件到本地
        try {
            String storedFilename = UUID.randomUUID() + "_" + safeFilename;
            Path userDir = Paths.get(uploadDir, userId.toString()).toAbsolutePath();
            Files.createDirectories(userDir);
            Path targetPath = userDir.resolve(storedFilename);
            file.transferTo(targetPath.toFile());

            // 4. 记录元信息（初始状态 PROCESSING）
            Material material = new Material();
            material.setUserId(userId);
            material.setFilename(safeFilename);
            material.setType(type);
            material.setStoragePath(targetPath.toString());
            material.setStatus(Material.Status.PROCESSING);
            materialRepository.save(material);

            // 5. 异步处理：提取 → 切片 → 向量入库（不阻塞 HTTP 响应）
            fileProcessingService.processFile(userId, material.getId(), safeFilename, type, targetPath);

            return toResponse(material);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public List<MaterialResponse> list() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        return materialRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("资料不存在"));

        if (!material.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除该资料");
        }

        documentIngestionService.removeByMaterial(material.getId());
        deletePhysicalFile(material);
        materialRepository.delete(material);
    }

    /** 删除磁盘上的物理文件，best-effort：文件缺失或删除失败不阻断删库 */
    private void deletePhysicalFile(Material material) {
        String storagePath = material.getStoragePath();
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(storagePath));
        } catch (IOException e) {
            log.warn("删除物理文件失败: {}", storagePath, e);
        }
    }

    private MaterialResponse toResponse(Material material) {
        return new MaterialResponse(
                material.getId(),
                material.getFilename(),
                material.getType(),
                material.getStatus().name(),
                material.getCreatedAt()
        );
    }

    /**
     * 基于文件内容检测 MIME 类型
     */
    private String detectMimeType(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return tika.detect(is, file.getOriginalFilename());
        } catch (IOException e) {
            // 降级：用扩展名推断
            String ext = getExtension(file.getOriginalFilename());
            return switch (ext) {
                case "txt" -> "text/plain";
                case "pdf" -> "application/pdf";
                case "doc" -> "application/msword";
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "bmp" -> "image/bmp";
                case "webp" -> "image/webp";
                default -> "application/octet-stream";
            };
        }
    }

    private boolean isAllowedMimeType(String mimeType) {
        return mimeType != null && ALLOWED_MIME_TYPES.stream().anyMatch(mimeType::startsWith);
    }

    private String mimeTypeToContentType(String mimeType) {
        if (mimeType == null) return "unknown";
        if (mimeType.startsWith("text/plain")) return "txt";
        if (mimeType.startsWith("application/pdf")) return "pdf";
        if (mimeType.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml") ||
            mimeType.startsWith("application/msword")) return "word";
        if (mimeType.startsWith("image/")) return "image";
        return "unknown";
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return UUID.randomUUID().toString();
        String safe = filename.replaceAll("[/\\\\:<>\"|?*]", "_");
        if (safe.length() > 200) {
            safe = safe.substring(0, 200);
        }
        return safe;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
