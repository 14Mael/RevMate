package com.team.study.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void preflightAllowsPatchRequests() throws Exception {
        CorsConfig config = new CorsConfig();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/wrong-questions/11/master");
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Access-Control-Request-Method", "PATCH");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
        };

        config.corsFilter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("PATCH");
    }
}
