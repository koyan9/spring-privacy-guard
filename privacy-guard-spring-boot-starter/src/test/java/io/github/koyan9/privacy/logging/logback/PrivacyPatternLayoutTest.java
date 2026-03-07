/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.TextMaskingService;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyPatternLayoutTest {

    @AfterEach
    void clearRuntime() {
        PrivacyLogbackRuntime.clear();
    }

    @Test
    void sanitizesRenderedLogbackMessage() {
        PrivacyLogbackRuntime.set(new PrivacyLogSanitizer(new TextMaskingService(new MaskingService())));

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        PrivacyPatternLayout layout = new PrivacyPatternLayout();
        layout.setContext(context);
        layout.setPattern("%msg");
        layout.start();

        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("demo");
        event.setLevel(Level.INFO);
        event.setMessage("phone=13800138000 email=alice@example.com");

        String rendered = layout.doLayout(event);
        assertTrue(rendered.contains("138****8000"));
        assertTrue(rendered.contains("a****@example.com"));
    }
}