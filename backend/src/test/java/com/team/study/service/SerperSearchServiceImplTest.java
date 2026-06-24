package com.team.study.service;

import com.team.study.dto.response.WebSearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SerperSearchServiceImplTest {

    @Test
    void searchSendsApiKeyHeaderAndParsesOrganicResults() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        SerperSearchServiceImpl service = new SerperSearchServiceImpl(
                restTemplate,
                "serper-key",
                "https://google.serper.dev");

        server.expect(once(), requestTo("https://google.serper.dev/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-KEY", "serper-key"))
                .andRespond(withSuccess("""
                        {
                          "organic": [
                            {
                              "title": "Java 并发课程",
                              "link": "https://www.bilibili.com/video/BV1",
                              "snippet": "系统讲解线程、锁和并发工具。"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<WebSearchResult> results = service.search("Java 并发", 3);

        assertThat(results).containsExactly(new WebSearchResult(
                "Java 并发课程",
                "https://www.bilibili.com/video/BV1",
                "系统讲解线程、锁和并发工具。"));
        server.verify();
    }

    @Test
    void searchRejectsBlankApiKey() {
        SerperSearchServiceImpl service = new SerperSearchServiceImpl(
                new RestTemplate(),
                " ",
                "https://google.serper.dev");

        assertThatThrownBy(() -> service.search("Java", 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("搜索服务暂不可用");
    }

    @Test
    void searchReturnsEmptyListWhenNoOrganicResults() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        SerperSearchServiceImpl service = new SerperSearchServiceImpl(
                restTemplate,
                "serper-key",
                "https://google.serper.dev");

        server.expect(once(), requestTo("https://google.serper.dev/search"))
                .andRespond(withSuccess("{ \"organic\": [] }", MediaType.APPLICATION_JSON));

        assertThat(service.search("不存在的课程", 3)).isEmpty();
        server.verify();
    }
}
