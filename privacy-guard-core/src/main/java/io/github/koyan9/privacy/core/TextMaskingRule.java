/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@StableSpi
public record TextMaskingRule(SensitiveType sensitiveType, Pattern pattern) {

    private static final Pattern DEFAULT_EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern DEFAULT_PHONE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern DEFAULT_ID_CARD_PATTERN = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");

    public TextMaskingRule {
        Objects.requireNonNull(sensitiveType, "sensitiveType must not be null");
        Objects.requireNonNull(pattern, "pattern must not be null");
    }

    public static List<TextMaskingRule> defaults() {
        return List.of(
                new TextMaskingRule(SensitiveType.EMAIL, DEFAULT_EMAIL_PATTERN),
                new TextMaskingRule(SensitiveType.PHONE, DEFAULT_PHONE_PATTERN),
                new TextMaskingRule(SensitiveType.ID_CARD, DEFAULT_ID_CARD_PATTERN)
        );
    }
}
