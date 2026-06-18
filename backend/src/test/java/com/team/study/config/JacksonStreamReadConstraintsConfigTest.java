package com.team.study.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonStreamReadConstraintsConfigTest {

    @Test
    void raisesDefaultMaxStringLengthForInlineAudioPayloads() {
        new JacksonStreamReadConstraintsConfig().configureStreamReadConstraints();

        assertThat(StreamReadConstraints.defaults().getMaxStringLength())
                .isGreaterThanOrEqualTo(120_000_000);
    }
}
