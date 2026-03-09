/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

class LoggingPrivacyAuditDeadLetterAlertCallbackTest {

    @Test
    void logsWithoutThrowingForWarningDownAndRecovery() {
        LoggingPrivacyAuditDeadLetterAlertCallback callback = new LoggingPrivacyAuditDeadLetterAlertCallback();

        callback.handle(new PrivacyAuditDeadLetterAlertEvent(
                java.time.Instant.now(),
                new PrivacyAuditDeadLetterBacklogSnapshot(1, 1, 5, PrivacyAuditDeadLetterBacklogState.WARNING, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()),
                null
        ));
        callback.handle(new PrivacyAuditDeadLetterAlertEvent(
                java.time.Instant.now(),
                new PrivacyAuditDeadLetterBacklogSnapshot(5, 1, 5, PrivacyAuditDeadLetterBacklogState.DOWN, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()),
                new PrivacyAuditDeadLetterBacklogSnapshot(1, 1, 5, PrivacyAuditDeadLetterBacklogState.WARNING, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of())
        ));
        callback.handle(new PrivacyAuditDeadLetterAlertEvent(
                java.time.Instant.now(),
                new PrivacyAuditDeadLetterBacklogSnapshot(0, 1, 5, PrivacyAuditDeadLetterBacklogState.CLEAR, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()),
                new PrivacyAuditDeadLetterBacklogSnapshot(1, 1, 5, PrivacyAuditDeadLetterBacklogState.WARNING, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of())
        ));
    }
}
