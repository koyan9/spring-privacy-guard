/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging.logback;

import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import org.slf4j.event.KeyValuePair;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class PrivacyLogbackRuntime {

    private static volatile PrivacyLogSanitizer privacyLogSanitizer;
    private static volatile Supplier<PrivacyLogbackSanitizerSettings> sanitizerSettingsSupplier =
            PrivacyLogbackSanitizerSettings::disabled;

    private PrivacyLogbackRuntime() {
    }

    public static void set(PrivacyLogSanitizer sanitizer) {
        privacyLogSanitizer = sanitizer;
    }

    public static void set(PrivacyLogSanitizer sanitizer, PrivacyLogbackSanitizerSettings settings) {
        set(sanitizer, () -> settings == null ? PrivacyLogbackSanitizerSettings.disabled() : settings);
    }

    public static void set(PrivacyLogSanitizer sanitizer, Supplier<PrivacyLogbackSanitizerSettings> settingsSupplier) {
        privacyLogSanitizer = sanitizer;
        sanitizerSettingsSupplier = settingsSupplier == null ? PrivacyLogbackSanitizerSettings::disabled : settingsSupplier;
    }

    public static void clear() {
        privacyLogSanitizer = null;
        sanitizerSettingsSupplier = PrivacyLogbackSanitizerSettings::disabled;
    }

    public static String sanitize(String message) {
        PrivacyLogSanitizer sanitizer = privacyLogSanitizer;
        if (sanitizer == null || message == null) {
            return message;
        }
        return sanitizer.sanitize(message);
    }

    public static boolean containsSensitiveData(String message, Object[] arguments) {
        PrivacyLogSanitizer sanitizer = privacyLogSanitizer;
        if (sanitizer == null) {
            return false;
        }
        return sanitizer.containsSensitiveData(message) || sanitizer.containsSensitiveData(arguments);
    }

    public static Map<String, String> sanitizeMdc(Map<String, String> mdc) {
        PrivacyLogSanitizer sanitizer = privacyLogSanitizer;
        PrivacyLogbackSanitizerSettings settings = resolveSettings();
        if (sanitizer == null || mdc == null || settings == null || !settings.isMdcEnabled()) {
            return mdc;
        }
        Map<String, String> sanitized = new LinkedHashMap<>(mdc.size());
        for (Map.Entry<String, String> entry : mdc.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (shouldSanitizeKey(key, settings.getMdcIncludeKeys(), settings.getMdcExcludeKeys())) {
                sanitized.put(key, value == null ? null : sanitizer.sanitize(value));
            } else {
                sanitized.put(key, value);
            }
        }
        return sanitized;
    }

    public static List<KeyValuePair> sanitizeKeyValuePairs(List<KeyValuePair> pairs) {
        PrivacyLogSanitizer sanitizer = privacyLogSanitizer;
        PrivacyLogbackSanitizerSettings settings = resolveSettings();
        if (sanitizer == null || pairs == null || settings == null || !settings.isStructuredEnabled()) {
            return pairs;
        }
        java.util.ArrayList<KeyValuePair> sanitized = new java.util.ArrayList<>(pairs.size());
        for (KeyValuePair pair : pairs) {
            if (pair == null) {
                continue;
            }
            String key = pair.key;
            Object value = pair.value;
            if (shouldSanitizeKey(key, settings.getStructuredIncludeKeys(), settings.getStructuredExcludeKeys())) {
                sanitized.add(new KeyValuePair(key, sanitizer.sanitizeValue(value)));
            } else {
                sanitized.add(pair);
            }
        }
        return sanitized;
    }

    private static boolean shouldSanitizeKey(String key, java.util.Set<String> includeKeys, java.util.Set<String> excludeKeys) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (excludeKeys != null && excludeKeys.contains(key)) {
            return false;
        }
        if (includeKeys != null && !includeKeys.isEmpty()) {
            return includeKeys.contains(key);
        }
        return true;
    }

    private static PrivacyLogbackSanitizerSettings resolveSettings() {
        Supplier<PrivacyLogbackSanitizerSettings> supplier = sanitizerSettingsSupplier;
        if (supplier == null) {
            return PrivacyLogbackSanitizerSettings.disabled();
        }
        PrivacyLogbackSanitizerSettings settings = supplier.get();
        return settings == null ? PrivacyLogbackSanitizerSettings.disabled() : settings;
    }
}
