/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging;

import org.slf4j.LoggerFactory;

public class PrivacyLoggerFactory {

    private final PrivacyLogSanitizer sanitizer;

    public PrivacyLoggerFactory(PrivacyLogSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public PrivacyLogger getLogger(Class<?> type) {
        return new PrivacyLogger(LoggerFactory.getLogger(type), sanitizer);
    }
}