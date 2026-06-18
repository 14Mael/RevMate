package com.team.study.service;

import com.team.study.dto.request.CreateSubjectRequest;
import com.team.study.dto.response.SubjectResponse;

import java.util.List;

public interface SubjectService {
    SubjectResponse create(CreateSubjectRequest request);
    List<SubjectResponse> list();
    void delete(Long id);
}
