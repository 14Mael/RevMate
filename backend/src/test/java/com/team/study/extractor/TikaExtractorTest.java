package com.team.study.extractor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TikaExtractorTest {

    @Test
    void supports_pptAndExcel() {
        TikaExtractor extractor = new TikaExtractor(null);
        assertTrue(extractor.supports("ppt"));
        assertTrue(extractor.supports("excel"));
    }

    @Test
    void supports_existingTypes() {
        TikaExtractor extractor = new TikaExtractor(null);
        assertTrue(extractor.supports("txt"));
        assertTrue(extractor.supports("pdf"));
        assertTrue(extractor.supports("word"));
    }

    @Test
    void supports_rejectsUnknownTypes() {
        TikaExtractor extractor = new TikaExtractor(null);
        assertFalse(extractor.supports("video"));
        assertFalse(extractor.supports("audio"));
    }
}
