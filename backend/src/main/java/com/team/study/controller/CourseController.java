package com.team.study.controller;

import com.team.study.common.Result;
import com.team.study.dto.request.CourseKeywordRequest;
import com.team.study.dto.request.CourseRecommendRequest;
import com.team.study.dto.request.SavedCourseRequest;
import com.team.study.dto.response.CourseCard;
import com.team.study.dto.response.SavedCourseResponse;
import com.team.study.service.CourseRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseRecommendService courseRecommendService;

    @PostMapping("/keywords")
    public Result<List<String>> keywords(@RequestBody CourseKeywordRequest request) {
        return Result.success(courseRecommendService.keywords(request.materialId()));
    }

    @PostMapping("/recommend")
    public Result<List<CourseCard>> recommend(@RequestBody CourseRecommendRequest request) {
        return Result.success(courseRecommendService.recommend(request));
    }

    @PostMapping("/saved")
    public Result<SavedCourseResponse> save(@RequestBody SavedCourseRequest request) {
        return Result.success(courseRecommendService.save(request));
    }

    @GetMapping("/saved")
    public Result<List<SavedCourseResponse>> listSaved() {
        return Result.success(courseRecommendService.listSaved());
    }

    @DeleteMapping("/saved/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        courseRecommendService.deleteSaved(id);
        return Result.success();
    }
}
