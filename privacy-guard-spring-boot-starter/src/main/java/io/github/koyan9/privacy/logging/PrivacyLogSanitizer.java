/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging;

import io.github.koyan9.privacy.core.TextMaskingService;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PrivacyLogSanitizer {

    private final TextMaskingService textMaskingService;

    public PrivacyLogSanitizer(TextMaskingService textMaskingService) {
        this.textMaskingService = textMaskingService;
    }

    public String sanitize(String message) {
        return textMaskingService.sanitize(message);
    }

    public boolean containsSensitiveData(String message) {
        if (message == null) {
            return false;
        }
        return !sanitize(message).equals(message);
    }

    public boolean containsSensitiveData(Object... arguments) {
        if (arguments == null || arguments.length == 0) {
            return false;
        }
        for (Object argument : arguments) {
            if (containsSensitiveValue(argument)) {
                return true;
            }
        }
        return false;
    }

    public Object[] sanitizeArguments(Object... arguments) {
        if (arguments == null || arguments.length == 0) {
            return new Object[0];
        }

        Object[] sanitized = new Object[arguments.length];
        int lastIndex = arguments.length - 1;
        for (int index = 0; index < arguments.length; index++) {
            Object argument = arguments[index];
            if (index == lastIndex && argument instanceof Throwable) {
                sanitized[index] = argument;
                continue;
            }
            sanitized[index] = sanitizeValueInternal(argument);
        }
        return sanitized;
    }

    public Object sanitizeValue(Object value) {
        return sanitizeValueInternal(value);
    }

    private boolean containsSensitiveValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof CharSequence sequence) {
            return containsSensitiveData(sequence.toString());
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (containsSensitiveValue(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsSensitiveValue(item)) {
                    return true;
                }
            }
            return false;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                if (containsSensitiveValue(Array.get(value, index))) {
                    return true;
                }
            }
            return false;
        }
        String text = String.valueOf(value);
        return containsSensitiveData(text);
    }

    private Object sanitizeValueInternal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence sequence) {
            return sanitize(sequence.toString());
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sanitized.put(entry.getKey(), sanitizeValueInternal(entry.getValue()));
            }
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : iterable) {
                sanitized.add(sanitizeValueInternal(item));
            }
            return sanitized;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> sanitized = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                sanitized.add(sanitizeValueInternal(Array.get(value, index)));
            }
            return sanitized;
        }
        return sanitize(String.valueOf(value));
    }
}
