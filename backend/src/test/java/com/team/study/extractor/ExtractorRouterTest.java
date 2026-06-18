package com.team.study.extractor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtractorRouterTest {

    @Test
    void routesAudioToAudioExtractor() {
        ContentExtractor audioExtractor = mock(ContentExtractor.class);
        when(audioExtractor.supports("audio")).thenReturn(true);
        ExtractorRouter router = new ExtractorRouter(List.of(audioExtractor));

        router.init();

        assertThat(router.getExtractor("audio")).isSameAs(audioExtractor);
    }
}
