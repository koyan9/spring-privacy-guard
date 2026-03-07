/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextMaskingServiceTest {

    private final TextMaskingService textMaskingService = new TextMaskingService(new MaskingService());

    @Test
    void sanitizesFreeTextLogPayloads() {
        String sanitized = textMaskingService.sanitize("patient phone=13800138000 email=alice@example.com id=110101199001011234");

        assertEquals("patient phone=138****8000 email=a****@example.com id=1101**********1234", sanitized);
    }
}