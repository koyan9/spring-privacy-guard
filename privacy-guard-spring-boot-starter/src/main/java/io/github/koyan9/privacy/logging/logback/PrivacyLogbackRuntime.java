/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging.logback;

import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;

public final class PrivacyLogbackRuntime {

    private static volatile PrivacyLogSanitizer privacyLogSanitizer;

    private PrivacyLogbackRuntime() {
    }

    public static void set(PrivacyLogSanitizer sanitizer) {
        privacyLogSanitizer = sanitizer;
    }

    public static void clear() {
        privacyLogSanitizer = null;
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
}