package com.team.study.controller;

import com.team.study.common.Result;
import com.team.study.dto.request.CreateSubjectRequest;
import com.team.study.dto.response.SubjectResponse;
import com.team.study.service.SubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @PostMapping
    public Result<SubjectResponse> create(@Valid @RequestBody CreateSubjectRequest request) {
        return Result.success(subjectService.create(request));
    }

    @GetMapping
    public Result<List<SubjectResponse>> list() {
        return Result.success(subjectService.list());
    }

    @PutMapping("/{id}")
    public Result<SubjectResponse> update(@PathVariable Long id, @Valid @RequestBody CreateSubjectRequest request) {
        return Result.success(subjectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return Result.success();
    }
}
