package com.team.study.extractor;

import com.team.study.service.OssAudioStorageService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 音频提取器 — 上传音频并调用专门的 ASR 服务转写为文字稿。
 */
@Component
public class AudioExtractor implements ContentExtractor {

    private final OssAudioStorageService ossAudioStorageService;
    private final DashScopeAsrClient dashScopeAsrClient;

    public AudioExtractor(OssAudioStorageService ossAudioStorageService, DashScopeAsrClient dashScopeAsrClient) {
        this.ossAudioStorageService = ossAudioStorageService;
        this.dashScopeAsrClient = dashScopeAsrClient;
    }

    @Override
    public boolean supports(String contentType) {
        return "audio".equals(contentType);
    }

    @Override
    public String extract(Resource file) {
        if (file == null) {
            throw new IllegalArgumentException("音频文件不能为空");
        }

        try {
            Path filePath = file.getFile().toPath();
            String audioUrl = ossAudioStorageService.uploadAndCreateSignedUrl(filePath, file.getFilename());
            String result = dashScopeAsrClient.transcribe(audioUrl);
            if (result == null || result.isBlank()) {
                throw new IllegalStateException("语音识别结果为空");
            }
            return result.trim();
        } catch (Exception e) {
            throw new RuntimeException("语音识别失败: " + file.getFilename(), e);
        }
    }
}
