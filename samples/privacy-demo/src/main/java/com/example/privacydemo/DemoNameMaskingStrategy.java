/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.core.MaskingContext;
import io.github.koyan9.privacy.core.MaskingStrategy;
import io.github.koyan9.privacy.core.SensitiveType;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Component
class DemoNameMaskingStrategy implements MaskingStrategy, Ordered {

    @Override
    public boolean supports(MaskingContext context) {
        return context.sensitiveType() == SensitiveType.NAME;
    }

    @Override
    public String mask(String value, MaskingContext context) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String maskChar = context.maskChar();
        String repeated = maskChar.repeat(Math.max(1, value.length() - 1));
        return "CUSTOM-" + value.charAt(0) + repeated;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
