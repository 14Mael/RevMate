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

class BochaSearchServiceImplTest {

    @Test
    void searchSendsBearerTokenAndParsesWebResults() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        BochaSearchServiceImpl service = new BochaSearchServiceImpl(
                restTemplate,
                "bocha-key",
                "https://api.bochaai.com");

        server.expect(once(), requestTo("https://api.bochaai.com/v1/web-search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer bocha-key"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "webPages": {
                              "value": [
                                {
                                  "name": "Java 并发课程",
                                  "url": "https://example.com/java",
                                  "snippet": "系统讲解线程、锁和并发工具。"
                                }
                              ]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<WebSearchResult> results = service.search("Java 并发", 3);

        assertThat(results).containsExactly(new WebSearchResult(
                "Java 并发课程",
                "https://example.com/java",
                "系统讲解线程、锁和并发工具。"));
        server.verify();
    }

    @Test
    void searchRejectsBlankApiKey() {
        BochaSearchServiceImpl service = new BochaSearchServiceImpl(
                new RestTemplate(),
                " ",
                "https://api.bochaai.com");

        assertThatThrownBy(() -> service.search("Java", 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("搜索服务暂不可用");
    }

    @Test
    void searchReturnsEmptyListWhenBochaHasNoResults() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        BochaSearchServiceImpl service = new BochaSearchServiceImpl(
                restTemplate,
                "bocha-key",
                "https://api.bochaai.com");

        server.expect(once(), requestTo("https://api.bochaai.com/v1/web-search"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "webPages": {
                              "value": []
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.search("不存在的课程", 3)).isEmpty();
        server.verify();
    }
}
