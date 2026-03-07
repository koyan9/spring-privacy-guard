/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging.logback;

import ch.qos.logback.classic.LoggerContext;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

public class PrivacyLogbackConfigurer implements DisposableBean {

    private final PrivacyBlockingTurboFilter privacyBlockingTurboFilter;
    private final boolean turboFilterInstalled;

    public PrivacyLogbackConfigurer(PrivacyLogSanitizer privacyLogSanitizer, boolean installTurboFilter, boolean blockUnsafeMessages) {
        PrivacyLogbackRuntime.set(privacyLogSanitizer);
        if (installTurboFilter && LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext) {
            PrivacyBlockingTurboFilter turboFilter = new PrivacyBlockingTurboFilter();
            turboFilter.setBlockUnsafeMessages(blockUnsafeMessages);
            turboFilter.setContext(loggerContext);
            turboFilter.start();
            loggerContext.addTurboFilter(turboFilter);
            this.privacyBlockingTurboFilter = turboFilter;
            this.turboFilterInstalled = true;
            return;
        }
        this.privacyBlockingTurboFilter = null;
        this.turboFilterInstalled = false;
    }

    @Override
    public void destroy() {
        if (turboFilterInstalled && LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext) {
            loggerContext.getTurboFilterList().remove(privacyBlockingTurboFilter);
            privacyBlockingTurboFilter.stop();
        }
        PrivacyLogbackRuntime.clear();
    }
}