package com.team.study.service;

import com.team.study.dto.response.MaterialResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MaterialService {
    MaterialResponse upload(MultipartFile file);
    List<MaterialResponse> list();
    void delete(Long id);
}
