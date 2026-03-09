/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import java.util.List;
import java.util.Objects;

public class MaskingService {

    private final String fallbackMaskChar;
    private final List<MaskingStrategy> maskingStrategies;

    public MaskingService() {
        this("*", List.of());
    }

    public MaskingService(String fallbackMaskChar) {
        this(fallbackMaskChar, List.of());
    }

    public MaskingService(List<MaskingStrategy> maskingStrategies) {
        this("*", maskingStrategies);
    }

    public MaskingService(String fallbackMaskChar, List<MaskingStrategy> maskingStrategies) {
        this.fallbackMaskChar = sanitizeMaskChar(fallbackMaskChar);
        this.maskingStrategies = maskingStrategies == null ? List.of() : List.copyOf(maskingStrategies);
    }

    public String mask(String value, SensitiveData sensitiveData) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String maskChar = sanitizeMaskChar(sensitiveData.maskChar());
        MaskingContext context = new MaskingContext(
                sensitiveData.type(),
                maskChar,
                sensitiveData.leftVisible(),
                sensitiveData.rightVisible()
        );
        if (context.hasExplicitVisibility()) {
            int left = Math.max(0, sensitiveData.leftVisible());
            int right = Math.max(0, sensitiveData.rightVisible());
            return maskRange(value, left, right, maskChar);
        }

        String customMasked = maskWithStrategies(value, context);
        if (customMasked != null) {
            return customMasked;
        }
        return maskBuiltIn(value, sensitiveData.type(), maskChar);
    }

    public String mask(String value, SensitiveType sensitiveType) {
        if (value == null || value.isBlank()) {
            return value;
        }

        MaskingContext context = new MaskingContext(sensitiveType, fallbackMaskChar, -1, -1);
        String customMasked = maskWithStrategies(value, context);
        if (customMasked != null) {
            return customMasked;
        }
        return maskBuiltIn(value, sensitiveType, fallbackMaskChar);
    }

    private String maskWithStrategies(String value, MaskingContext context) {
        for (MaskingStrategy maskingStrategy : maskingStrategies) {
            if (maskingStrategy.supports(context)) {
                return Objects.requireNonNull(maskingStrategy.mask(value, context), "MaskingStrategy must not return null");
            }
        }
        return null;
    }

    private String maskBuiltIn(String value, SensitiveType sensitiveType, String maskChar) {
        return switch (sensitiveType) {
            case NAME -> maskName(value, maskChar);
            case PHONE -> maskRange(value, 3, 4, maskChar);
            case EMAIL -> maskEmail(value, maskChar);
            case ID_CARD -> maskRange(value, 4, 4, maskChar);
            case ADDRESS -> maskRange(value, Math.min(6, value.length()), 0, maskChar);
            case GENERIC -> maskRange(value, 1, 1, maskChar);
        };
    }

    private String maskName(String value, String maskChar) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() == 1) {
            return repeat(maskChar, 1);
        }
        if (value.length() == 2) {
            return value.charAt(0) + repeat(maskChar, 1);
        }
        return value.charAt(0) + repeat(maskChar, value.length() - 2) + value.charAt(value.length() - 1);
    }

    private String maskEmail(String value, String maskChar) {
        if (value == null || value.isBlank()) {
            return value;
        }

        int atIndex = value.indexOf('@');
        if (atIndex <= 0) {
            return maskRange(value, 1, 0, maskChar);
        }

        String localPart = value.substring(0, atIndex);
        String domainPart = value.substring(atIndex);
        return maskRange(localPart, 1, 0, maskChar) + domainPart;
    }

    private String maskRange(String value, int leftVisible, int rightVisible, String maskChar) {
        if (value == null || value.isBlank()) {
            return value;
        }

        int normalizedLeft = Math.max(0, leftVisible);
        int normalizedRight = Math.max(0, rightVisible);
        if (normalizedLeft + normalizedRight >= value.length()) {
            return repeat(maskChar, value.length());
        }

        int maskedLength = value.length() - normalizedLeft - normalizedRight;
        return value.substring(0, normalizedLeft)
                + repeat(maskChar, maskedLength)
                + value.substring(value.length() - normalizedRight);
    }

    private String sanitizeMaskChar(String value) {
        if (value == null || value.isBlank()) {
            return "*";
        }
        return value;
    }

    private String repeat(String maskChar, int count) {
        return maskChar.repeat(Math.max(0, count));
    }
}
