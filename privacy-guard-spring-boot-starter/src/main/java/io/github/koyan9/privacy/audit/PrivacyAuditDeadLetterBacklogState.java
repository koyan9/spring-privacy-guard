/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

public enum PrivacyAuditDeadLetterBacklogState {
    CLEAR,
    WARNING,
    DOWN;

    public String metricTagValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
