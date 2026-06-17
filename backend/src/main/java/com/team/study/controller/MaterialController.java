package com.team.study.controller;

import com.team.study.common.Result;
import com.team.study.dto.response.MaterialResponse;
import com.team.study.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<MaterialResponse> upload(@RequestParam("file") MultipartFile file) {
        MaterialResponse response = materialService.upload(file);
        return Result.success(response);
    }

    @GetMapping
    public Result<List<MaterialResponse>> list() {
        List<MaterialResponse> list = materialService.list();
        return Result.success(list);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return Result.success();
    }
}
