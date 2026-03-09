/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.time.Instant;

public record PrivacyAuditDeadLetterAlertEvent(
        Instant occurredAt,
        PrivacyAuditDeadLetterBacklogSnapshot currentSnapshot,
        PrivacyAuditDeadLetterBacklogSnapshot previousSnapshot
) {

    public boolean recovery() {
        return previousSnapshot != null
                && previousSnapshot.state() != PrivacyAuditDeadLetterBacklogState.CLEAR
                && currentSnapshot.state() == PrivacyAuditDeadLetterBacklogState.CLEAR;
    }
}
