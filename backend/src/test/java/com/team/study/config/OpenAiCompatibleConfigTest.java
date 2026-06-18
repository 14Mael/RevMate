package com.team.study.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleConfigTest {

    @Test
    void pomUsesSpringAiOpenAiCompatibleStarter() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom).contains("<artifactId>spring-ai-starter-model-openai</artifactId>");
        assertThat(pom).doesNotContain("spring-ai-alibaba");
    }

    @Test
    void applicationConfigUsesProviderEnvironmentVariables() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml).contains("api-key: ${OPENAI_API_KEY}");
        assertThat(yaml).contains("base-url: ${OPENAI_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode}");
        assertThat(yaml).contains("model: ${OPENAI_MODEL:qwen3.5-omni-plus}");
        assertThat(yaml).contains("secret: ${JWT_SECRET}");
        assertThat(yaml).doesNotContain("JWT_SECRET:");
        assertThat(yaml).doesNotContain("RevMateDefaultSecretKeyForDevelopment");
        assertThat(yaml).doesNotContain("sk-ws-");
    }

    @Test
    void schemaIncludesPreviewPathColumn() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/schema.sql"));

        assertThat(schema).contains("preview_path VARCHAR(512)");
        assertThat(schema).contains("preview_status VARCHAR(20)");
        assertThat(schema).contains("preview_message VARCHAR(255)");
    }

    @Test
    void applicationConfigAllowsLargerAudioUploads() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        String exceptionHandler = Files.readString(Path.of("src/main/java/com/team/study/common/GlobalExceptionHandler.java"));

        assertThat(yaml).contains("max-file-size: 100MB");
        assertThat(yaml).contains("max-request-size: 100MB");
        assertThat(exceptionHandler).contains("最大 100MB");
    }

    @Test
    void readmeDocumentsOpenAiCompatibleRuntimeConfiguration() throws Exception {
        String readme = Files.readString(Path.of("../README.md"));

        assertThat(readme).contains("OPENAI_API_KEY");
        assertThat(readme).contains("OPENAI_BASE_URL");
        assertThat(readme).contains("OPENAI_MODEL");
        assertThat(readme).contains("JWT_SECRET");
        assertThat(readme).contains("LibreOffice");
    }
}
