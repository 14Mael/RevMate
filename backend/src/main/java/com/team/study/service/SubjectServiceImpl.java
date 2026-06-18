package com.team.study.service;

import com.team.study.dto.request.CreateSubjectRequest;
import com.team.study.dto.response.SubjectResponse;
import com.team.study.entity.Subject;
import com.team.study.repository.MaterialRepository;
import com.team.study.repository.SubjectRepository;
import com.team.study.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository subjectRepository;
    private final MaterialRepository materialRepository;

    @Override
    @Transactional
    public SubjectResponse create(CreateSubjectRequest request) {
        Long userId = currentUserId();
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("学科名称不能为空");
        }
        if (subjectRepository.existsByUserIdAndName(userId, name)) {
            throw new IllegalArgumentException("学科已存在");
        }

        Subject subject = new Subject();
        subject.setUserId(userId);
        subject.setName(name);
        return toResponse(subjectRepository.save(subject));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResponse> list() {
        Long userId = currentUserId();
        return subjectRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long userId = currentUserId();
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("学科不存在或无权访问"));
        if (!subject.getUserId().equals(userId)) {
            throw new IllegalArgumentException("学科不存在或无权访问");
        }
        if (materialRepository.existsBySubjectIdAndUserId(id, userId)) {
            throw new IllegalArgumentException("请先删除该学科下的资料");
        }
        subjectRepository.delete(subject);
    }

    private Long currentUserId() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        return userId;
    }

    private SubjectResponse toResponse(Subject subject) {
        return new SubjectResponse(subject.getId(), subject.getName(), subject.getCreatedAt());
    }
}
