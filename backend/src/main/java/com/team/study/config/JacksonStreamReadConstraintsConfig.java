package com.team.study.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonStreamReadConstraintsConfig {

    private static final int MAX_STRING_LENGTH = 120_000_000;

    @PostConstruct
    public void configureStreamReadConstraints() {
        StreamReadConstraints.overrideDefaultStreamReadConstraints(
                StreamReadConstraints.builder()
                        .maxStringLength(MAX_STRING_LENGTH)
                        .build());
    }
}
