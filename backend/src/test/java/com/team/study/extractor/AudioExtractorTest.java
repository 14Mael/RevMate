package com.team.study.extractor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioExtractorTest {

    @Test
    void supportsAudioOnly() {
        AudioExtractor extractor = new AudioExtractor(null);

        assertTrue(extractor.supports("audio"));
        assertFalse(extractor.supports("image"));
        assertFalse(extractor.supports("pdf"));
    }

    @Test
    void extractRejectsMissingChatModel() {
        AudioExtractor extractor = new AudioExtractor(null);

        assertThrows(IllegalStateException.class, () -> extractor.extract(null));
    }
}
