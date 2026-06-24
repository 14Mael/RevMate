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

@Service
@ConditionalOnProperty(name = "search.provider", havingValue = "bocha", matchIfMissing = true)
public class BochaSearchServiceImpl implements WebSearchService {

    private static final String UNAVAILABLE_MESSAGE = "搜索服务暂不可用";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;

    public BochaSearchServiceImpl(
            RestTemplate restTemplate,
            @Value("${bocha.api-key:}") String apiKey,
            @Value("${bocha.base-url:https://api.bochaai.com}") String baseUrl) {
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
        headers.setBearerAuth(apiKey.trim());
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "query", query.trim(),
                "count", count);

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
                ? "https://api.bochaai.com"
                : baseUrl.trim();
        return normalized.replaceAll("/+$", "") + "/v1/web-search";
    }

    private List<WebSearchResult> parseResults(Map<?, ?> body, int count) {
        Object value = nested(body, "data", "webPages", "value");
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            return List.of();
        }

        List<WebSearchResult> results = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String title = text(map.get("name"));
            String url = text(map.get("url"));
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

    private Object nested(Map<?, ?> map, String... keys) {
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(key);
        }
        return current;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
