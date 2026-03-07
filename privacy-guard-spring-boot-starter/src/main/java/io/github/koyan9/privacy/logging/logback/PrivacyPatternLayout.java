/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging.logback;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class PrivacyPatternLayout extends PatternLayout {

    @Override
    public String doLayout(ILoggingEvent event) {
        return PrivacyLogbackRuntime.sanitize(super.doLayout(event));
    }
}