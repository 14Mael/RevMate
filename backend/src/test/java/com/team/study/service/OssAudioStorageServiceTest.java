package com.team.study.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OssAudioStorageServiceTest {

    @Test
    void uploadAndCreateSignedUrlUploadsToSafeObjectKeyAndReturnsGetSignedUrl() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        OssAudioStorageService service = new OssAudioStorageService(
                restTemplate,
                "oss-cn-hangzhou.aliyuncs.com",
                "study-bucket",
                "access-id",
                "access-secret",
                Duration.ofMinutes(60));
        Path audio = Files.createTempFile("软件测试", ".m4a");
        Files.writeString(audio, "fake audio");

        server.expect(requestTo(allOf(
                        containsString("https://study-bucket.oss-cn-hangzhou.aliyuncs.com/audio/"),
                        containsString(".m4a?"),
                        containsString("OSSAccessKeyId=access-id"),
                        containsString("Signature="),
                        not(containsString(audio.toString().replace("\\", "/"))))))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        String signedUrl = service.uploadAndCreateSignedUrl(audio, "D:\\records\\软件测试.m4a");

        assertThat(signedUrl)
                .startsWith("https://study-bucket.oss-cn-hangzhou.aliyuncs.com/audio/")
                .contains(".m4a?")
                .contains("OSSAccessKeyId=access-id")
                .contains("Signature=")
                .doesNotContain("D:")
                .doesNotContain("records");
        server.verify();
    }
}
