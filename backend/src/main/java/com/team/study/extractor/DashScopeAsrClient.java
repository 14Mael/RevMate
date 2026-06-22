package com.team.study.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class DashScopeAsrClient {

    private static final String TRANSCRIPTION_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcription";
    private static final String TASK_URL_PREFIX = "https://dashscope.aliyuncs.com/api/v1/tasks/";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final Duration pollInterval;
    private final int maxPollAttempts;

    public DashScopeAsrClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${dashscope.api-key:${OPENAI_API_KEY:}}") String apiKey,
            @Value("${dashscope.asr-model:paraformer-v2}") String model,
            @Value("${dashscope.poll-interval:PT2S}") Duration pollInterval,
            @Value("${dashscope.max-poll-attempts:300}") int maxPollAttempts) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.pollInterval = pollInterval;
        this.maxPollAttempts = maxPollAttempts;
    }

    public String transcribe(String audioUrl) {
        requireConfigured(apiKey, "DashScope API Key 未配置");
        requireConfigured(audioUrl, "音频 URL 不能为空");

        String taskId = submit(audioUrl);
        JsonNode finishedTask = pollUntilFinished(taskId);
        String transcriptionUrl = resolveTranscriptionUrl(finishedTask);
        return downloadTranscript(transcriptionUrl);
    }

    private String submit(String audioUrl) {
        Map<String, Object> body = Map.of(
                "model", model,
                "input", Map.of("file_urls", List.of(audioUrl)),
                "parameters", Map.of(
                        "language_hints", List.of("zh", "en"),
                        "disfluency_removal_enabled", false));
        JsonNode response = postJson(TRANSCRIPTION_URL, body, true);
        String taskId = textAt(response, "/output/task_id");
        if (taskId == null) {
            throw new IllegalStateException("DashScope 提交任务响应缺少 task_id");
        }
        return taskId;
    }

    private JsonNode pollUntilFinished(String taskId) {
        for (int attempt = 0; attempt < maxPollAttempts; attempt++) {
            JsonNode task = postJson(TASK_URL_PREFIX + taskId, null, false);
            String status = textAt(task, "/output/task_status");
            if ("SUCCEEDED".equals(status)) {
                return task;
            }
            if ("FAILED".equals(status)) {
                String message = textAt(task, "/output/message");
                throw new IllegalStateException("DashScope 语音识别任务失败: " + nullToDefault(message, status));
            }
            sleepBeforeNextPoll();
        }
        throw new IllegalStateException("DashScope 语音识别任务超时: " + taskId);
    }

    private String resolveTranscriptionUrl(JsonNode task) {
        JsonNode results = task.at("/output/results");
        if (!results.isArray() || results.isEmpty()) {
            throw new IllegalStateException("DashScope 语音识别结果缺少 results");
        }

        JsonNode result = results.get(0);
        String subtaskStatus = textAt(result, "/subtask_status");
        if ("FAILED".equals(subtaskStatus)) {
            String code = textAt(result, "/code");
            String message = textAt(result, "/message");
            throw new IllegalStateException("DashScope 语音识别子任务失败: "
                    + nullToDefault(code, "UNKNOWN") + " " + nullToDefault(message, ""));
        }
        String transcriptionUrl = textAt(result, "/transcription_url");
        if (transcriptionUrl == null) {
            throw new IllegalStateException("DashScope 语音识别结果缺少 transcription_url");
        }
        return transcriptionUrl;
    }

    private String downloadTranscript(String transcriptionUrl) {
        try {
            String json = restTemplate.getForObject(URI.create(transcriptionUrl), String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode transcripts = root.path("transcripts");
            if (!transcripts.isArray()) {
                throw new IllegalStateException("语音识别结果缺少 transcripts");
            }
            String transcript = StreamSupport.stream(transcripts.spliterator(), false)
                    .map(node -> node.path("text").asText(""))
                    .map(String::trim)
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining("\n"));
            if (transcript.isBlank()) {
                throw new IllegalStateException("语音识别结果为空");
            }
            return transcript;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("下载或解析语音识别结果失败", e);
        }
    }

    private JsonNode postJson(String url, Object body, boolean async) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            if (async) {
                headers.set("X-DashScope-Async", "enable");
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            String json = restTemplate.exchange(URI.create(url), HttpMethod.POST, entity, String.class).getBody();
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("调用 DashScope 语音识别接口失败", e);
        }
    }

    private String textAt(JsonNode node, String pointer) {
        JsonNode value = node.at(pointer);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    private void sleepBeforeNextPoll() {
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            return;
        }
        try {
            Thread.sleep(pollInterval.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 DashScope 语音识别任务时被中断", e);
        }
    }

    private void requireConfigured(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }

    private String nullToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
