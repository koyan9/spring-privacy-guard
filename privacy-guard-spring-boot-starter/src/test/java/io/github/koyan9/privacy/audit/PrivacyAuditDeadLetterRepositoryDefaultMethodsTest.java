/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PrivacyAuditDeadLetterRepositoryDefaultMethodsTest {

    @Test
    void defaultSaveAllDelegatesToSave() {
        List<String> savedIds = new ArrayList<>();
        PrivacyAuditDeadLetterRepository repository = new PrivacyAuditDeadLetterRepository() {
            @Override
            public void save(PrivacyAuditDeadLetterEntry entry) {
                savedIds.add(entry.resourceId());
            }
        };

        repository.saveAll(List.of(
                new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "A", "one", Instant.now(), "READ", "Patient", "a", "actor", "OK", Map.of()),
                new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "B", "two", Instant.now(), "READ", "Patient", "b", "actor", "OK", Map.of())
        ));

        assertEquals(List.of("a", "b"), savedIds);
    }

    @Test
    void defaultDeleteByIdReturnsFalse() {
        PrivacyAuditDeadLetterRepository repository = entry -> {
        };

        assertFalse(repository.deleteById(1L));
    }
}
