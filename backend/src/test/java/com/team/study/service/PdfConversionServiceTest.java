package com.team.study.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfConversionServiceTest {

    private final PdfConversionService service = new PdfConversionService(null);

    @Test
    void isConvertibleType_trueForOfficeTypes() {
        assertTrue(service.isConvertibleType("word"));
        assertTrue(service.isConvertibleType("ppt"));
        assertTrue(service.isConvertibleType("excel"));
    }

    @Test
    void isConvertibleType_falseForOthers() {
        assertFalse(service.isConvertibleType("pdf"));
        assertFalse(service.isConvertibleType("txt"));
        assertFalse(service.isConvertibleType("image"));
    }

    @Test
    void isConvertibleType_falseForNull() {
        assertFalse(service.isConvertibleType(null));
    }
}
