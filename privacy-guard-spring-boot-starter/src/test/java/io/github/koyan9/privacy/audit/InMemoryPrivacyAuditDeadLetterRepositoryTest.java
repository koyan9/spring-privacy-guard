/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InMemoryPrivacyAuditDeadLetterRepositoryTest {

    @Test
    void storesAndClearsDeadLetterEntries() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        PrivacyAuditDeadLetterEntry entry = new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "failure",
                Instant.now(),
                "READ",
                "Patient",
                "demo",
                "actor",
                "OK",
                Map.of("phone", "138****8000")
        );

        repository.save(entry);
        assertEquals(1, repository.findAll().size());
        assertNotNull(repository.findAll().get(0).id());

        repository.clear();
        assertEquals(0, repository.findAll().size());
    }

    @Test
    void storesDeadLetterEntriesInBulk() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();

        repository.saveAll(List.of(
                new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "A", "one", Instant.now(), "READ", "Patient", "a", "actor", "OK", Map.of()),
                new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "B", "two", Instant.now(), "WRITE", "Patient", "b", "actor", "OK", Map.of())
        ));

        assertEquals(List.of("a", "b"), repository.findAll().stream().map(PrivacyAuditDeadLetterEntry::resourceId).sorted().toList());
        assertThat(repository.findAll().stream().map(PrivacyAuditDeadLetterEntry::id).toList()).allMatch(java.util.Objects::nonNull);
    }

    @Test
    void returnsFilteredSortedAndPagedDeadLetters() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T00:00:00Z"), 3, "TypeA", "first failure", Instant.parse("2026-03-05T23:00:00Z"), "READ", "Patient", "older", "alice-1", "OK", Map.of()));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T01:00:00Z"), 4, "TypeA", "second failure", Instant.parse("2026-03-06T00:30:00Z"), "READ", "Patient", "newer", "alice-2", "OK", Map.of()));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T02:00:00Z"), 5, "TypeB", "other", Instant.parse("2026-03-06T01:30:00Z"), "WRITE", "Order", "other", "bob", "DENIED", Map.of()));

        PrivacyAuditDeadLetterQueryCriteria criteria = new PrivacyAuditDeadLetterQueryCriteria(
                "READ", null,
                "Patient", null,
                null, null,
                null, "alice",
                "OK", null,
                "TypeA", "failure",
                Instant.parse("2026-03-06T00:30:00Z"),
                Instant.parse("2026-03-06T01:30:00Z"),
                Instant.parse("2026-03-06T00:00:00Z"),
                Instant.parse("2026-03-06T01:00:00Z"),
                PrivacyAuditSortDirection.ASC,
                10,
                0
        );

        assertEquals("newer", repository.findByCriteria(criteria).get(0).resourceId());
    }

    @Test
    void computesStatsForFilteredDeadLetters() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T00:00:00Z"), 3, "TypeA", "one", Instant.parse("2026-03-05T23:00:00Z"), "READ", "Patient", "a", "alice", "OK", Map.of()));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T01:00:00Z"), 3, "TypeA", "two", Instant.parse("2026-03-06T00:30:00Z"), "READ", "Patient", "b", "alice", "OK", Map.of()));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T02:00:00Z"), 4, "TypeB", "three", Instant.parse("2026-03-06T01:30:00Z"), "WRITE", "Order", "c", "bob", "DENIED", Map.of()));

        PrivacyAuditDeadLetterStats stats = repository.computeStats(new PrivacyAuditDeadLetterQueryCriteria(
                null, null,
                null, null,
                null, null,
                null, "alice",
                null, null,
                null, null,
                null, null,
                null, null,
                PrivacyAuditSortDirection.DESC,
                100,
                0
        ));

        assertEquals(2, stats.total());
        assertEquals(2L, stats.byAction().get("READ"));
        assertEquals(2L, stats.byResourceType().get("Patient"));
        assertEquals(2L, stats.byErrorType().get("TypeA"));
    }
}
