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
    int reindex(Long id);

    /** 获取音频原文件（用于前端播放器），含内容类型 */
    AudioResource getAudioResource(Long id);

    /** 获取资料的转写文字稿（音频）/提取文本 */
    String getTranscript(Long id);

    record AudioResource(Resource resource, String contentType, String filename) {}
}
