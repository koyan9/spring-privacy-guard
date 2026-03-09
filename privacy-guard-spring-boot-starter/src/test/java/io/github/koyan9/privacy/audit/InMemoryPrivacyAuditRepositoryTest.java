/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryPrivacyAuditRepositoryTest {

    @Test
    void storesAndClearsEvents() {
        InMemoryPrivacyAuditRepository repository = new InMemoryPrivacyAuditRepository();
        PrivacyAuditEvent event = new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of());

        repository.save(event);
        assertEquals(1, repository.findAll().size());

        repository.clear();
        assertEquals(0, repository.findAll().size());
    }

    @Test
    void storesEventsInBulk() {
        InMemoryPrivacyAuditRepository repository = new InMemoryPrivacyAuditRepository();

        repository.saveAll(List.of(
                new PrivacyAuditEvent(Instant.parse("2026-03-06T00:00:00Z"), "READ", "Patient", "a", "actor", "OK", Map.of()),
                new PrivacyAuditEvent(Instant.parse("2026-03-06T01:00:00Z"), "WRITE", "Patient", "b", "actor", "OK", Map.of())
        ));

        assertEquals(List.of("a", "b"), repository.findAll().stream().map(PrivacyAuditEvent::resourceId).toList());
    }

    @Test
    void returnsFilteredSortedAndPagedEvents() {
        InMemoryPrivacyAuditRepository repository = new InMemoryPrivacyAuditRepository();
        repository.save(new PrivacyAuditEvent(Instant.parse("2026-03-06T00:00:00Z"), "READ", "Patient", "older", "alice-1", "OK", Map.of()));
        repository.save(new PrivacyAuditEvent(Instant.parse("2026-03-06T01:00:00Z"), "READ", "Patient", "newer", "alice-2", "OK", Map.of()));
        repository.save(new PrivacyAuditEvent(Instant.parse("2026-03-06T02:00:00Z"), "WRITE", "Order", "other", "bob", "DENIED", Map.of()));

        PrivacyAuditQueryCriteria criteria = new PrivacyAuditQueryCriteria(
                "READ", null,
                "Patient", null,
                null, null,
                null, "alice",
                "OK", null,
                Instant.parse("2026-03-06T00:30:00Z"),
                Instant.parse("2026-03-06T01:30:00Z"),
                PrivacyAuditSortDirection.ASC,
                10,
                0
        );

        assertEquals("newer", repository.findByCriteria(criteria).get(0).resourceId());
    }

    @Test
    void computesStatsForFilteredEvents() {
        InMemoryPrivacyAuditRepository repository = new InMemoryPrivacyAuditRepository();
        repository.save(new PrivacyAuditEvent(Instant.parse("2026-03-06T00:00:00Z"), "READ", "Patient", "a", "alice", "OK", Map.of()));
        repository.save(new PrivacyAuditEvent(Instant.parse("2026-03-06T01:00:00Z"), "READ", "Patient", "b", "alice", "OK", Map.of()));
        repository.save(new PrivacyAuditEvent(Instant.parse("2026-03-06T02:00:00Z"), "WRITE", "Order", "c", "bob", "DENIED", Map.of()));

        PrivacyAuditQueryStats stats = repository.computeStats(new PrivacyAuditQueryCriteria(
                null, null,
                null, null,
                null, null,
                null, "alice",
                null, null,
                null, null,
                PrivacyAuditSortDirection.DESC,
                100,
                0
        ));

        assertEquals(2, stats.total());
        assertEquals(2L, stats.byAction().get("READ"));
        assertEquals(2L, stats.byResourceType().get("Patient"));
    }
}
