package com.team.study.service;

import com.team.study.dto.response.WebSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 Serper.dev（Google 搜索 API）的联网搜索实现。
 *
 * <p>相比博查，Google 对 B站/MOOC 等平台索引更好。通过 {@code search.provider=serper} 启用。
 */
@Service
@ConditionalOnProperty(name = "search.provider", havingValue = "serper")
public class SerperSearchServiceImpl implements WebSearchService {

    private static final String UNAVAILABLE_MESSAGE = "搜索服务暂不可用";
    /** 面向中文学习场景，固定走中国大陆 + 简体中文结果 */
    private static final String COUNTRY = "cn";
    private static final String LANGUAGE = "zh-cn";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;

    public SerperSearchServiceImpl(
            RestTemplate restTemplate,
            @Value("${serper.api-key:}") String apiKey,
            @Value("${serper.base-url:https://google.serper.dev}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    public List<WebSearchResult> search(String query, int count) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(UNAVAILABLE_MESSAGE);
        }
        if (query == null || query.isBlank() || count <= 0) {
            return List.of();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey.trim());
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "q", query.trim(),
                "gl", COUNTRY,
                "hl", LANGUAGE,
                "num", count);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);
            return parseResults(response.getBody(), count);
        } catch (RestClientException e) {
            throw new IllegalStateException(UNAVAILABLE_MESSAGE, e);
        }
    }

    private String endpoint() {
        String normalized = baseUrl == null || baseUrl.isBlank()
                ? "https://google.serper.dev"
                : baseUrl.trim();
        return normalized.replaceAll("/+$", "") + "/search";
    }

    private List<WebSearchResult> parseResults(Map<?, ?> body, int count) {
        if (body == null || !(body.get("organic") instanceof List<?> items) || items.isEmpty()) {
            return List.of();
        }

        List<WebSearchResult> results = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String title = text(map.get("title"));
            String url = text(map.get("link"));
            String snippet = text(map.get("snippet"));
            if (!title.isBlank() && !url.isBlank()) {
                results.add(new WebSearchResult(title, url, snippet));
            }
            if (results.size() >= count) {
                break;
            }
        }
        return results;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
