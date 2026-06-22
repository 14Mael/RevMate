package com.team.study.service;

import com.team.study.dto.response.MaterialResponse;
import com.team.study.entity.Material;
import com.team.study.extractor.ExtractorRouter;
import com.team.study.repository.MaterialRepository;
import com.team.study.repository.SubjectRepository;
import com.team.study.security.SecurityUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
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
    private final SubjectRepository subjectRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** 允许的文件 MIME 类型前缀（基于文件内容校验） */
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "text/plain",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "image/",
            "audio/"
    );

    private final Tika tika = new Tika();

    @PostConstruct
    public void init() {
        // 将相对路径转为绝对路径
        Path path = Paths.get(uploadDir);
        if (!path.isAbsolute()) {
            this.uploadDir = path.toAbsolutePath().normalize().toString();
        }
        log.info("上传目录: {}", uploadDir);
    }

    @Override
    public MaterialResponse upload(MultipartFile file, Long subjectId) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        validateSubject(userId, subjectId);

        // 1. 安全化文件名
        String safeFilename = sanitizeFilename(file.getOriginalFilename());

        // 2. 先保存文件到本地（再检测 MIME，避免流被消费）
        String storedFilename;
        Path targetPath;
        try {
            storedFilename = UUID.randomUUID() + "_" + safeFilename;
            Path userDir = Paths.get(uploadDir, userId.toString());
            Files.createDirectories(userDir);
            targetPath = userDir.resolve(storedFilename);
            file.transferTo(targetPath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }

        // 3. 从已保存的文件检测 MIME 类型
        String mimeType = detectMimeType(targetPath.toFile(), safeFilename);
        if (!isAllowedMimeType(mimeType)) {
            // 删除已保存的文件
            try { Files.deleteIfExists(targetPath); } catch (IOException ignored) {}
            log.warn("不支持的文件类型: mime={}, filename={}", mimeType, file.getOriginalFilename());
            throw new IllegalArgumentException("不支持的文件类型: " + file.getOriginalFilename());
        }
        String type = mimeTypeToContentType(mimeType);

        // 4. 记录元信息（初始状态 PROCESSING）
        Material material = new Material();
        material.setUserId(userId);
        material.setSubjectId(subjectId);
        material.setFilename(safeFilename);
        material.setType(type);
        material.setStoragePath(targetPath.toString());
        material.setStatus(Material.Status.PROCESSING);
        material.setPreviewStatus(initialPreviewStatus(type));
        materialRepository.save(material);

        // 5. 异步处理：提取 → 切片 → 向量入库（不阻塞 HTTP 响应）
        fileProcessingService.processFile(userId, material.getId(), safeFilename, type, targetPath);

        return toResponse(material);
    }

    @Override
    public List<MaterialResponse> list(Long subjectId) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        validateSubject(userId, subjectId);
        return materialRepository.findByUserIdAndSubjectIdOrderByCreatedAtDesc(userId, subjectId)
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

    @Override
    public int reindex(Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("资料不存在"));
        if (!material.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该资料");
        }
        return documentIngestionService.reindexMaterial(userId, material.getId());
    }

    @Override
    public org.springframework.core.io.Resource getPreviewResource(Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("资料不存在"));
        if (!material.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该资料");
        }
        String previewPath = material.getPreviewPath();
        if (previewPath == null || previewPath.isBlank()) {
            String message = material.getPreviewMessage();
            throw new IllegalArgumentException(
                    message == null || message.isBlank() ? "预览不可用" : message);
        }
        return new org.springframework.core.io.FileSystemResource(previewPath);
    }

    /** 删除磁盘上的物理文件（含预览 PDF），best-effort：文件缺失或删除失败不阻断删库 */
    private void deletePhysicalFile(Material material) {
        deleteQuietly(material.getStoragePath());
        String previewPath = material.getPreviewPath();
        // 当 previewPath != storagePath 时才单独删（pdf 类型两者相同，避免重复删）
        if (previewPath != null && !previewPath.equals(material.getStoragePath())) {
            deleteQuietly(previewPath);
        }
    }

    private void deleteQuietly(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.warn("删除物理文件失败: {}", path, e);
        }
    }

    private MaterialResponse toResponse(Material material) {
        return new MaterialResponse(
                material.getId(),
                material.getSubjectId(),
                material.getFilename(),
                material.getType(),
                material.getStatus().name(),
                material.getCreatedAt(),
                material.getPreviewPath() != null,
                material.getPreviewStatus() != null ? material.getPreviewStatus().name() : Material.PreviewStatus.NONE.name(),
                material.getPreviewMessage()
        );
    }

    private Material.PreviewStatus initialPreviewStatus(String type) {
        return switch (type) {
            case "pdf", "word", "ppt", "excel" -> Material.PreviewStatus.PROCESSING;
            default -> Material.PreviewStatus.NONE;
        };
    }

    private void validateSubject(Long userId, Long subjectId) {
        if (subjectId == null) {
            throw new IllegalArgumentException("subjectId 不能为空");
        }
        if (!subjectRepository.existsByIdAndUserId(subjectId, userId)) {
            throw new IllegalArgumentException("学科不存在或无权访问");
        }
    }

    /**
     * 基于已保存的文件内容检测 MIME 类型
     */
    private String detectMimeType(File file, String filename) {
        String extensionMimeType = detectMimeTypeByExtension(filename);
        if (extensionMimeType.startsWith("audio/")) {
            return extensionMimeType;
        }
        if (file == null || !file.isFile()) {
            return extensionMimeType;
        }
        try {
            return tika.detect(file);
        } catch (IOException e) {
            return extensionMimeType;
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
        if (mimeType.startsWith("application/vnd.openxmlformats-officedocument.presentationml") ||
            mimeType.startsWith("application/vnd.ms-powerpoint")) return "ppt";
        if (mimeType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml") ||
            mimeType.startsWith("application/vnd.ms-excel")) return "excel";
        if (mimeType.startsWith("image/")) return "image";
        if (mimeType.startsWith("audio/")) return "audio";
        return "unknown";
    }

    private String detectMimeTypeByExtension(String filename) {
        String ext = getExtension(filename);
        return switch (ext) {
            case "txt" -> "text/plain";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "m4a" -> "audio/mp4";
            case "webm" -> "audio/webm";
            case "ogg" -> "audio/ogg";
            default -> "application/octet-stream";
        };
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
