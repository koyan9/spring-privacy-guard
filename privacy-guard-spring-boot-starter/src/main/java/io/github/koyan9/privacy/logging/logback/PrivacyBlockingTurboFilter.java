/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

public class PrivacyBlockingTurboFilter extends TurboFilter {

    private boolean blockUnsafeMessages = true;

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (!isStarted()) {
            return FilterReply.NEUTRAL;
        }
        if (!blockUnsafeMessages) {
            return FilterReply.NEUTRAL;
        }
        return PrivacyLogbackRuntime.containsSensitiveData(format, params) ? FilterReply.DENY : FilterReply.NEUTRAL;
    }

    public boolean isBlockUnsafeMessages() {
        return blockUnsafeMessages;
    }

    public void setBlockUnsafeMessages(boolean blockUnsafeMessages) {
        this.blockUnsafeMessages = blockUnsafeMessages;
    }
}