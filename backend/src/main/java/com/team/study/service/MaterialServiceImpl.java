package com.team.study.service;

import com.team.study.dto.response.MaterialResponse;
import com.team.study.entity.Material;
import com.team.study.repository.MaterialRepository;
import com.team.study.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    private final MaterialRepository materialRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** 允许的文件类型 */
    private static final List<String> ALLOWED_TYPES = List.of("txt", "pdf", "word", "image");

    @Override
    public MaterialResponse upload(MultipartFile file) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        String type = mapToType(extension);

        if (!ALLOWED_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension);
        }

        // 保存文件到本地
        try {
            String storedFilename = UUID.randomUUID() + "_" + originalFilename;
            Path userDir = Paths.get(uploadDir, userId.toString());
            Files.createDirectories(userDir);
            Path targetPath = userDir.resolve(storedFilename);
            file.transferTo(targetPath.toFile());

            // 记录元信息
            Material material = new Material();
            material.setUserId(userId);
            material.setFilename(originalFilename);
            material.setType(type);
            material.setStoragePath(targetPath.toString());
            material.setStatus(Material.Status.PROCESSING);
            materialRepository.save(material);

            // TODO 交接点 A: 触发成员2 的 ingest(userId, materialId, file)

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

        // TODO 交接点 A: 调用成员2 的 removeByMaterial(materialId) 清理向量

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
            // 文件删除失败不应阻断业务，记录后继续删库
            System.err.println("删除物理文件失败: " + storagePath + " - " + e.getMessage());
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

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String mapToType(String extension) {
        return switch (extension) {
            case "txt" -> "txt";
            case "pdf" -> "pdf";
            case "doc", "docx" -> "word";
            case "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "image";
            case "mp3", "wav", "m4a", "ogg" -> "audio";
            default -> "unknown";
        };
    }
}
