package com.team.study.service;

import com.team.study.dto.response.MaterialResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MaterialService {
    MaterialResponse upload(MultipartFile file, Long subjectId);
    List<MaterialResponse> list(Long subjectId);
    void delete(Long id);
    Resource getPreviewResource(Long id);
}
