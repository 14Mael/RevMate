package com.team.study.controller;

import com.team.study.common.Result;
import com.team.study.dto.response.MaterialResponse;
import com.team.study.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<MaterialResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subjectId") Long subjectId) {
        MaterialResponse response = materialService.upload(file, subjectId);
        return Result.success(response);
    }

    @GetMapping
    public Result<List<MaterialResponse>> list(@RequestParam("subjectId") Long subjectId) {
        List<MaterialResponse> list = materialService.list(subjectId);
        return Result.success(list);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        Resource resource = materialService.getPreviewResource(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }
}
