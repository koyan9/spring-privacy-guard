/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepositoryBackedPrivacyAuditDeadLetterHandlerTest {

    @Test
    void storesDeadLetterEntryInRepository() {
        AtomicReference<PrivacyAuditDeadLetterEntry> saved = new AtomicReference<>();
        RepositoryBackedPrivacyAuditDeadLetterHandler handler = new RepositoryBackedPrivacyAuditDeadLetterHandler(saved::set);
        PrivacyAuditEvent event = new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of());

        handler.handle(event, 3, new IllegalStateException("failed"));

        assertEquals("demo", saved.get().resourceId());
        assertEquals(3, saved.get().attempts());
        assertEquals("failed", saved.get().errorMessage());
    }
}
