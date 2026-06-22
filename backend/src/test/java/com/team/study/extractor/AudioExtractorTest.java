package com.team.study.extractor;

import com.team.study.service.OssAudioStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioExtractorTest {

    @Test
    void supportsAudioOnly() {
        AudioExtractor extractor = new AudioExtractor(fakeOss("https://bucket/audio.m4a"), fakeAsr("文字稿"));

        assertTrue(extractor.supports("audio"));
        assertFalse(extractor.supports("image"));
        assertFalse(extractor.supports("pdf"));
    }

    @Test
    void extractRejectsMissingFile() {
        AudioExtractor extractor = new AudioExtractor(fakeOss("https://bucket/audio.m4a"), fakeAsr("文字稿"));

        assertThrows(IllegalArgumentException.class, () -> extractor.extract(null));
    }

    @Test
    void extractUploadsAudioAndReturnsAsrTranscript() throws Exception {
        RecordingOssAudioStorageService ossAudioStorageService =
                new RecordingOssAudioStorageService("https://bucket.oss-cn-hangzhou.aliyuncs.com/audio/lecture.m4a?Signature=abc");
        RecordingDashScopeAsrClient dashScopeAsrClient =
                new RecordingDashScopeAsrClient("第一段文字\n第二段文字");
        AudioExtractor extractor = new AudioExtractor(ossAudioStorageService, dashScopeAsrClient);
        Path audioPath = Files.createTempFile("lecture", ".m4a");
        FileSystemResource audio = new FileSystemResource(audioPath);

        String transcript = extractor.extract(audio);

        assertThat(transcript).isEqualTo("第一段文字\n第二段文字");
        assertThat(ossAudioStorageService.file).isEqualTo(audioPath);
        assertThat(ossAudioStorageService.filename).isEqualTo(audio.getFilename());
        assertThat(dashScopeAsrClient.audioUrl)
                .isEqualTo("https://bucket.oss-cn-hangzhou.aliyuncs.com/audio/lecture.m4a?Signature=abc");
    }

    @Test
    void extractWrapsAsrFailureWithAudioFilename() throws Exception {
        OssAudioStorageService ossAudioStorageService = new RecordingOssAudioStorageService("unused") {
            @Override
            public String uploadAndCreateSignedUrl(Path file, String filename) {
                throw new IllegalStateException("OSS 上传失败");
            }
        };
        DashScopeAsrClient dashScopeAsrClient = fakeAsr("unused");
        AudioExtractor extractor = new AudioExtractor(ossAudioStorageService, dashScopeAsrClient);
        Path audioPath = Files.createTempFile("lecture", ".m4a");
        FileSystemResource audio = new FileSystemResource(audioPath);

        RuntimeException error = assertThrows(RuntimeException.class, () -> extractor.extract(audio));

        assertThat(error).hasMessageContaining("语音识别失败: lecture");
        assertThat(error).hasMessageContaining(".m4a");
        assertThat(error).hasRootCauseMessage("OSS 上传失败");
    }

    private OssAudioStorageService fakeOss(String signedUrl) {
        return new RecordingOssAudioStorageService(signedUrl);
    }

    private DashScopeAsrClient fakeAsr(String transcript) {
        return new RecordingDashScopeAsrClient(transcript);
    }

    private static class RecordingOssAudioStorageService extends OssAudioStorageService {
        private final String signedUrl;
        private Path file;
        private String filename;

        RecordingOssAudioStorageService(String signedUrl) {
            super(new RestTemplate(), "oss-cn-hangzhou.aliyuncs.com", "bucket", "id", "secret", Duration.ofMinutes(1));
            this.signedUrl = signedUrl;
        }

        @Override
        public String uploadAndCreateSignedUrl(Path file, String filename) {
            this.file = file;
            this.filename = filename;
            return signedUrl;
        }
    }

    private static class RecordingDashScopeAsrClient extends DashScopeAsrClient {
        private final String transcript;
        private String audioUrl;

        RecordingDashScopeAsrClient(String transcript) {
            super(new RestTemplate(), new com.fasterxml.jackson.databind.ObjectMapper(),
                    "sk-test", "paraformer-v2", Duration.ZERO, 1);
            this.transcript = transcript;
        }

        @Override
        public String transcribe(String audioUrl) {
            this.audioUrl = audioUrl;
            return transcript;
        }
    }
}
