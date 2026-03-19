/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.core.MaskingContext;
import io.github.koyan9.privacy.core.PrivacyTenantAwareMaskingStrategy;
import io.github.koyan9.privacy.core.SensitiveType;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Component
class DemoNameMaskingStrategy implements PrivacyTenantAwareMaskingStrategy, Ordered {

    @Override
    public boolean supports(String tenantId, MaskingContext context) {
        return context.sensitiveType() == SensitiveType.NAME;
    }

    @Override
    public String mask(String tenantId, String value, MaskingContext context) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String maskChar = context.maskChar();
        String repeated = maskChar.repeat(Math.max(1, value.length() - 1));
        if ("tenant-a".equalsIgnoreCase(tenantId)) {
            return "TENANT-A-" + value.charAt(0) + repeated;
        }
        if ("tenant-b".equalsIgnoreCase(tenantId)) {
            return "TENANT-B-" + value.charAt(0) + repeated;
        }
        return "CUSTOM-" + value.charAt(0) + repeated;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
