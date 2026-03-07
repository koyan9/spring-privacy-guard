/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaskingServiceTest {

    private final MaskingService maskingService = new MaskingService();

    @Test
    void masksBuiltInSensitiveTypes() {
        assertEquals("138****8000", maskingService.mask("13800138000", SensitiveType.PHONE));
        assertEquals("a****@example.com", maskingService.mask("alice@example.com", SensitiveType.EMAIL));
        assertEquals("A***e", maskingService.mask("Alice", SensitiveType.NAME));
    }

    @Test
    void respectsAnnotationOverrides() throws Exception {
        Field field = DemoRecord.class.getDeclaredField("customMaskedValue");
        SensitiveData sensitiveData = field.getAnnotation(SensitiveData.class);

        assertEquals("ab###f", maskingService.mask("abcdef", sensitiveData));
    }

    static class DemoRecord {

        @SensitiveData(type = SensitiveType.GENERIC, maskChar = "#", leftVisible = 2, rightVisible = 1)
        private String customMaskedValue;
    }
}