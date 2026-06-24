package com.team.study.service;

import com.team.study.dto.request.CourseRecommendRequest;
import com.team.study.dto.request.SavedCourseRequest;
import com.team.study.dto.response.CourseCard;
import com.team.study.dto.response.SavedCourseResponse;

import java.util.List;

public interface CourseRecommendService {

    List<String> keywords(Long materialId);

    List<CourseCard> recommend(CourseRecommendRequest request);

    SavedCourseResponse save(SavedCourseRequest request);

    List<SavedCourseResponse> listSaved();

    void deleteSaved(Long id);
}
