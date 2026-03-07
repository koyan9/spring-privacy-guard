/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging;

import org.slf4j.Logger;

public class PrivacyLogger {

    private final Logger delegate;
    private final PrivacyLogSanitizer sanitizer;

    public PrivacyLogger(Logger delegate, PrivacyLogSanitizer sanitizer) {
        this.delegate = delegate;
        this.sanitizer = sanitizer;
    }

    public void info(String message, Object... arguments) {
        delegate.info(sanitizer.sanitize(message), sanitizer.sanitizeArguments(arguments));
    }

    public void warn(String message, Object... arguments) {
        delegate.warn(sanitizer.sanitize(message), sanitizer.sanitizeArguments(arguments));
    }

    public void error(String message, Object... arguments) {
        delegate.error(sanitizer.sanitize(message), sanitizer.sanitizeArguments(arguments));
    }

    public void debug(String message, Object... arguments) {
        delegate.debug(sanitizer.sanitize(message), sanitizer.sanitizeArguments(arguments));
    }
}