/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.spi.FilterReply;
import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.TextMaskingService;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrivacyBlockingTurboFilterTest {

    @AfterEach
    void clearRuntime() {
        PrivacyLogbackRuntime.clear();
    }

    @Test
    void deniesUnsafeMessagesWhenEnabled() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger("privacy-filter-test");
        PrivacyLogbackRuntime.set(new PrivacyLogSanitizer(new TextMaskingService(new MaskingService())));

        PrivacyBlockingTurboFilter filter = new PrivacyBlockingTurboFilter();
        filter.setContext(context);
        filter.setBlockUnsafeMessages(true);
        filter.start();

        FilterReply reply = filter.decide(null, logger, Level.INFO, "phone={}", new Object[]{"13800138000"}, null);
        assertEquals(FilterReply.DENY, reply);
    }

    @Test
    void staysNeutralForSafeMessages() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger("privacy-filter-test-safe");
        PrivacyLogbackRuntime.set(new PrivacyLogSanitizer(new TextMaskingService(new MaskingService())));

        PrivacyBlockingTurboFilter filter = new PrivacyBlockingTurboFilter();
        filter.setContext(context);
        filter.setBlockUnsafeMessages(true);
        filter.start();

        FilterReply reply = filter.decide(null, logger, Level.INFO, "safe message", new Object[]{"ok"}, null);
        assertEquals(FilterReply.NEUTRAL, reply);
    }
}