package com.team.study.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DashScopeAsrClientTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    private final DashScopeAsrClient client = new DashScopeAsrClient(
            restTemplate,
            new ObjectMapper(),
            "sk-test",
            "paraformer-v2",
            Duration.ZERO,
            3);

    @Test
    void transcribeSubmitsPollsDownloadsAndJoinsTranscriptText() {
        server.expect(requestTo("https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcription"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(header("X-DashScope-Async", "enable"))
                .andExpect(jsonPath("$.model").value("paraformer-v2"))
                .andExpect(jsonPath("$.input.file_urls[0]").value("https://bucket/audio.m4a?Signature=abc"))
                .andExpect(jsonPath("$.parameters.language_hints[0]").value("zh"))
                .andExpect(jsonPath("$.parameters.language_hints[1]").value("en"))
                .andRespond(withSuccess("""
                        {"output":{"task_id":"task-1","task_status":"PENDING"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://dashscope.aliyuncs.com/api/v1/tasks/task-1"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andRespond(withSuccess("""
                        {
                          "output": {
                            "task_id": "task-1",
                            "task_status": "SUCCEEDED",
                            "results": [
                              {
                                "subtask_status": "SUCCEEDED",
                                "transcription_url": "https://dashscope-result/transcription.json?Expires=1&Signature=abc"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://dashscope-result/transcription.json?Expires=1&Signature=abc"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "transcripts": [
                            {"text": "第一段文字"},
                            {"text": "第二段文字"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        String transcript = client.transcribe("https://bucket/audio.m4a?Signature=abc");

        assertThat(transcript).isEqualTo("第一段文字\n第二段文字");
        server.verify();
    }

    @Test
    void transcribeThrowsClearErrorWhenSubtaskFails() {
        server.expect(requestTo("https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcription"))
                .andRespond(withSuccess("""
                        {"output":{"task_id":"task-2","task_status":"PENDING"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://dashscope.aliyuncs.com/api/v1/tasks/task-2"))
                .andRespond(withSuccess("""
                        {
                          "output": {
                            "task_status": "SUCCEEDED",
                            "results": [
                              {
                                "subtask_status": "FAILED",
                                "code": "InvalidFile.DownloadFailed",
                                "message": "The audio file cannot be downloaded."
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.transcribe("https://bucket/audio.m4a?Signature=abc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DashScope 语音识别子任务失败")
                .hasMessageContaining("InvalidFile.DownloadFailed")
                .hasMessageContaining("The audio file cannot be downloaded.");
    }

    @Test
    void transcribeThrowsWhenTranscriptIsBlank() {
        server.expect(requestTo("https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcription"))
                .andRespond(withSuccess("""
                        {"output":{"task_id":"task-3","task_status":"PENDING"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://dashscope.aliyuncs.com/api/v1/tasks/task-3"))
                .andRespond(withSuccess("""
                        {
                          "output": {
                            "task_status": "SUCCEEDED",
                            "results": [
                              {
                                "subtask_status": "SUCCEEDED",
                                "transcription_url": "https://dashscope-result/blank.json"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://dashscope-result/blank.json"))
                .andRespond(withSuccess("""
                        {"transcripts":[{"text":"   "}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.transcribe("https://bucket/audio.m4a?Signature=abc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("语音识别结果为空");
    }
}
