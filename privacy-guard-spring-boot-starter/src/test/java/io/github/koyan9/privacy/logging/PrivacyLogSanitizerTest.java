/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging;

import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.TextMaskingService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class PrivacyLogSanitizerTest {

    private final PrivacyLogSanitizer sanitizer = new PrivacyLogSanitizer(new TextMaskingService(new MaskingService()));

    @Test
    void sanitizesNestedCollectionsAndArrays() {
        Object[] sanitized = sanitizer.sanitizeArguments(
                Map.of("phone", "13800138000"),
                List.of("alice@example.com", "110101199001011234"),
                new String[]{"13800138000", "alice@example.com"}
        );

        Map<?, ?> first = assertInstanceOf(Map.class, sanitized[0]);
        List<?> second = assertInstanceOf(List.class, sanitized[1]);
        List<?> third = assertInstanceOf(List.class, sanitized[2]);

        assertEquals("138****8000", first.get("phone"));
        assertEquals("a****@example.com", second.get(0));
        assertEquals("1101**********1234", second.get(1));
        assertEquals("138****8000", third.get(0));
        assertEquals("a****@example.com", third.get(1));
    }

    @Test
    void preservesTrailingThrowable() {
        RuntimeException throwable = new RuntimeException("boom 13800138000");

        Object[] sanitized = sanitizer.sanitizeArguments("13800138000", throwable);

        assertEquals("138****8000", sanitized[0]);
        assertSame(throwable, sanitized[1]);
    }
}