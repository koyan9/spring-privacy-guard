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

class PrivacyAuditRepositoryDefaultMethodsTest {

    @Test
    void defaultSaveAllDelegatesToSave() {
        List<String> savedIds = new ArrayList<>();
        PrivacyAuditRepository repository = new PrivacyAuditRepository() {
            @Override
            public void save(PrivacyAuditEvent event) {
                savedIds.add(event.resourceId());
            }
        };

        repository.saveAll(List.of(
                new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "a", "actor", "OK", Map.of()),
                new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "b", "actor", "OK", Map.of())
        ));

        assertEquals(List.of("a", "b"), savedIds);
    }
}
