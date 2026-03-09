/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

class LoggingPrivacyAuditDeadLetterHandlerTest {

    @Test
    void logsWithoutThrowing() {
        LoggingPrivacyAuditDeadLetterHandler handler = new LoggingPrivacyAuditDeadLetterHandler();
        PrivacyAuditEvent event = new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of());

        handler.handle(event, 3, new IllegalStateException("failed"));
    }
}
