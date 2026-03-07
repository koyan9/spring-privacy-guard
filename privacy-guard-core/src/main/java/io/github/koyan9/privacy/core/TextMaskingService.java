/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextMaskingService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");

    private final MaskingService maskingService;

    public TextMaskingService(MaskingService maskingService) {
        this.maskingService = maskingService;
    }

    public String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String sanitized = replaceMatches(text, EMAIL_PATTERN, SensitiveType.EMAIL);
        sanitized = replaceMatches(sanitized, PHONE_PATTERN, SensitiveType.PHONE);
        sanitized = replaceMatches(sanitized, ID_CARD_PATTERN, SensitiveType.ID_CARD);
        return sanitized;
    }

    private String replaceMatches(String text, Pattern pattern, SensitiveType sensitiveType) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String masked = maskingService.mask(matcher.group(), sensitiveType);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(masked));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}