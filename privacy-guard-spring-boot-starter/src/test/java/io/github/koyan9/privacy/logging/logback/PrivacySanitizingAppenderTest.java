/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.TextMaskingService;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacySanitizingAppenderTest {

    @AfterEach
    void clearRuntime() {
        PrivacyLogbackRuntime.clear();
    }

    @Test
    void sanitizesBeforeForwardingToDelegateAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger("privacy-appender-test");
        PrivacyLogbackRuntime.set(
                new PrivacyLogSanitizer(new TextMaskingService(new MaskingService())),
                new PrivacyLogbackSanitizerSettings(
                        true,
                        java.util.Set.of(),
                        java.util.Set.of(),
                        true,
                        java.util.Set.of(),
                        java.util.Set.of()
                )
        );

        CapturingAppender delegate = new CapturingAppender();
        delegate.setContext(context);
        delegate.start();

        PrivacySanitizingAppender appender = new PrivacySanitizingAppender();
        appender.setContext(context);
        appender.addAppender(delegate);
        appender.start();

        LoggingEvent event = new LoggingEvent(
                PrivacySanitizingAppenderTest.class.getName(),
                logger,
                Level.INFO,
                "phone=13800138000 email=alice@example.com",
                null,
                null
        );
        event.setMDCPropertyMap(Map.of(
                "email", "alice@example.com",
                "safe", "ok"
        ));
        event.setKeyValuePairs(List.of(
                new KeyValuePair("phone", "13800138000"),
                new KeyValuePair("safe", "ok")
        ));

        appender.doAppend(event);

        assertNotNull(delegate.lastEvent);
        assertTrue(delegate.lastEvent.getFormattedMessage().contains("138****8000"));
        assertTrue(delegate.lastEvent.getFormattedMessage().contains("a****@example.com"));
        assertTrue(delegate.lastEvent.getMDCPropertyMap().get("email").contains("a****@example.com"));
        assertTrue(delegate.lastEvent.getMDCPropertyMap().get("safe").equals("ok"));
        List<KeyValuePair> pairs = delegate.lastEvent.getKeyValuePairs();
        assertNotNull(pairs);
        assertTrue(pairs.get(0).value.toString().contains("138****8000"));
    }

    static class CapturingAppender extends AppenderBase<ILoggingEvent> {
        private ILoggingEvent lastEvent;
        @Override protected void append(ILoggingEvent eventObject) { this.lastEvent = eventObject; }
    }
}
