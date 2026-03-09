/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyAuditDeadLetterEntryTest {

    @Test
    void copiesDetailsMapDefensively() {
        Map<String, String> details = new HashMap<>();
        details.put("phone", "138****8000");

        PrivacyAuditDeadLetterEntry entry = new PrivacyAuditDeadLetterEntry(
                1L,
                Instant.now(), 3, "type", "message",
                Instant.now(), "READ", "Patient", "demo", "actor", "OK", details
        );
        details.put("phone", "changed");

        assertEquals("138****8000", entry.details().get("phone"));
    }

    @Test
    void buildsDeadLetterEntryFromAuditEvent() {
        PrivacyAuditEvent event = new PrivacyAuditEvent(
                Instant.parse("2026-03-06T00:00:00Z"),
                "READ", "Patient", "demo", "actor", "OK", Map.of("phone", "138****8000")
        );

        PrivacyAuditDeadLetterEntry entry = PrivacyAuditDeadLetterEntry.from(event, 4, new IllegalStateException("failed"));

        assertEquals(4, entry.attempts());
        assertEquals("java.lang.IllegalStateException", entry.errorType());
        assertEquals("failed", entry.errorMessage());
        assertEquals("demo", entry.resourceId());
        assertEquals("138****8000", entry.details().get("phone"));
        assertTrue(!entry.failedAt().isBefore(event.occurredAt()));
    }

    @Test
    void addsReplayMetadataWhenConvertingBackToAuditEvent() {
        PrivacyAuditDeadLetterEntry entry = new PrivacyAuditDeadLetterEntry(
                42L,
                Instant.parse("2026-03-06T01:00:00Z"),
                3,
                "java.lang.IllegalStateException",
                "failed",
                Instant.parse("2026-03-06T00:00:00Z"),
                "READ",
                "Patient",
                "demo",
                "actor",
                "OK",
                Map.of("phone", "138****8000")
        );

        PrivacyAuditEvent replayEvent = entry.toReplayAuditEvent();

        assertEquals("DEAD_LETTER", replayEvent.details().get(PrivacyAuditDeadLetterEntry.REPLAY_SOURCE_KEY));
        assertEquals("42", replayEvent.details().get(PrivacyAuditDeadLetterEntry.REPLAY_DEAD_LETTER_ID_KEY));
        assertEquals("3", replayEvent.details().get(PrivacyAuditDeadLetterEntry.REPLAY_ATTEMPTS_KEY));
        assertEquals("java.lang.IllegalStateException", replayEvent.details().get(PrivacyAuditDeadLetterEntry.REPLAY_ERROR_TYPE_KEY));
        assertEquals("138****8000", replayEvent.details().get("phone"));
    }
}
