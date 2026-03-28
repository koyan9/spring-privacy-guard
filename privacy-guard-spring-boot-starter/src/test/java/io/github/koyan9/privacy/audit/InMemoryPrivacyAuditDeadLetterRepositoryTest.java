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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void returnsTenantNativeFilteredDeadLettersAndStats() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T00:00:00Z"), 3, "TypeA", "one", Instant.parse("2026-03-05T23:00:00Z"), "READ", "Patient", "a1", "alice", "OK", Map.of("tenant", "tenant-a")));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T01:00:00Z"), 3, "TypeA", "two", Instant.parse("2026-03-06T00:30:00Z"), "READ", "Patient", "a2", "alice", "OK", Map.of("tenant", "tenant-a")));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T02:00:00Z"), 4, "TypeB", "three", Instant.parse("2026-03-06T01:30:00Z"), "WRITE", "Order", "b1", "bob", "DENIED", Map.of("tenant", "tenant-b")));

        List<PrivacyAuditDeadLetterEntry> entries = repository.findByCriteria("tenant-a", "tenant", PrivacyAuditDeadLetterQueryCriteria.recent(10));
        PrivacyAuditDeadLetterStats stats = repository.computeStats("tenant-a", "tenant", PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertEquals(List.of("a2", "a1"), entries.stream().map(PrivacyAuditDeadLetterEntry::resourceId).toList());
        assertEquals(2, stats.total());
        assertEquals(2L, stats.byAction().get("READ"));
        assertEquals(2L, stats.byErrorType().get("TypeA"));
    }

    @Test
    void findsTenantNativeDeadLetterById() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T00:00:00Z"), 3, "TypeA", "one", Instant.parse("2026-03-05T23:00:00Z"), "READ", "Patient", "a1", "alice", "OK", Map.of("tenant", "tenant-a")));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T01:00:00Z"), 3, "TypeA", "two", Instant.parse("2026-03-06T00:30:00Z"), "READ", "Patient", "b1", "alice", "OK", Map.of("tenant", "tenant-b")));
        long tenantAId = repository.findAll().stream()
                .filter(entry -> "a1".equals(entry.resourceId()))
                .findFirst()
                .orElseThrow()
                .id();

        assertTrue(repository.findById("tenant-a", "tenant", tenantAId).isPresent());
        assertThat(repository.findById("tenant-b", "tenant", tenantAId)).isEmpty();
    }

    @Test
    void deletesTenantNativeFilteredDeadLetters() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T00:00:00Z"), 3, "TypeA", "one", Instant.parse("2026-03-05T23:00:00Z"), "READ", "Patient", "a1", "alice", "OK", Map.of("tenant", "tenant-a")));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T01:00:00Z"), 3, "TypeA", "two", Instant.parse("2026-03-06T00:30:00Z"), "READ", "Patient", "a2", "alice", "OK", Map.of("tenant", "tenant-a")));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T02:00:00Z"), 4, "TypeB", "three", Instant.parse("2026-03-06T01:30:00Z"), "WRITE", "Order", "b1", "bob", "DENIED", Map.of("tenant", "tenant-b")));

        int deleted = repository.deleteByCriteria(
                "tenant-a",
                "tenant",
                new PrivacyAuditDeadLetterQueryCriteria(
                        "READ", null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        PrivacyAuditSortDirection.DESC,
                        1,
                        0
                )
        );

        assertEquals(1, deleted);
        assertThat(repository.findAll()).extracting(PrivacyAuditDeadLetterEntry::resourceId).containsExactly("b1", "a1");
    }

    @Test
    void deletesTenantNativeDeadLetterById() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T00:00:00Z"), 3, "TypeA", "one", Instant.parse("2026-03-05T23:00:00Z"), "READ", "Patient", "a1", "alice", "OK", Map.of("tenant", "tenant-a")));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T01:00:00Z"), 3, "TypeA", "two", Instant.parse("2026-03-06T00:30:00Z"), "READ", "Patient", "b1", "alice", "OK", Map.of("tenant", "tenant-b")));
        long tenantAId = repository.findAll().stream()
                .filter(entry -> "a1".equals(entry.resourceId()))
                .findFirst()
                .orElseThrow()
                .id();

        assertThat(repository.deleteById("tenant-b", "tenant", tenantAId)).isFalse();
        assertThat(repository.deleteById("tenant-a", "tenant", tenantAId)).isTrue();
        assertThat(repository.findAll()).extracting(PrivacyAuditDeadLetterEntry::resourceId).containsExactly("b1");
    }

    @Test
    void replaysTenantNativeFilteredDeadLetters() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T00:00:00Z"), 3, "TypeA", "one", Instant.parse("2026-03-05T23:00:00Z"), "READ", "Patient", "a1", "alice", "OK", Map.of("tenant", "tenant-a")));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T01:00:00Z"), 3, "TypeA", "two", Instant.parse("2026-03-06T00:30:00Z"), "READ", "Patient", "a2", "alice", "OK", Map.of("tenant", "tenant-a")));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T02:00:00Z"), 4, "TypeB", "three", Instant.parse("2026-03-06T01:30:00Z"), "WRITE", "Order", "b1", "bob", "DENIED", Map.of("tenant", "tenant-b")));

        PrivacyAuditDeadLetterReplayResult result = repository.replayByCriteria(
                "tenant-a",
                "tenant",
                PrivacyAuditDeadLetterQueryCriteria.recent(10),
                entry -> !"a1".equals(entry.resourceId())
        );

        assertEquals(2, result.requested());
        assertEquals(1, result.replayed());
        assertEquals(1, result.failed());
        assertThat(result.replayedIds()).hasSize(1);
        assertThat(result.failedIds()).hasSize(1);
        assertThat(repository.findAll()).extracting(PrivacyAuditDeadLetterEntry::resourceId).containsExactly("b1", "a1");
    }

    @Test
    void replaysTenantNativeDeadLetterById() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T00:00:00Z"), 3, "TypeA", "one", Instant.parse("2026-03-05T23:00:00Z"), "READ", "Patient", "a1", "alice", "OK", Map.of("tenant", "tenant-a")));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T01:00:00Z"), 3, "TypeA", "two", Instant.parse("2026-03-06T00:30:00Z"), "READ", "Patient", "b1", "alice", "OK", Map.of("tenant", "tenant-b")));
        long tenantBId = repository.findAll().stream()
                .filter(entry -> "b1".equals(entry.resourceId()))
                .findFirst()
                .orElseThrow()
                .id();

        assertThat(repository.replayById("tenant-a", "tenant", tenantBId, entry -> true)).isFalse();
        assertThat(repository.replayById("tenant-b", "tenant", tenantBId, entry -> true)).isTrue();
        assertThat(repository.findAll()).extracting(PrivacyAuditDeadLetterEntry::resourceId).containsExactly("a1");
    }

    @Test
    void materializesTenantDetailsForTenantAwareBulkWrites() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();

        repository.saveAllTenantAware(List.of(
                new PrivacyTenantAuditDeadLetterWriteRequest(
                        new PrivacyAuditDeadLetterEntry(
                                null,
                                Instant.parse("2026-03-06T00:00:00Z"),
                                3,
                                "TypeA",
                                "failure",
                                Instant.parse("2026-03-05T23:00:00Z"),
                                "READ",
                                "Patient",
                                "tenant-aware",
                                "alice",
                                "OK",
                                Map.of("phone", "138****8000")
                        ),
                        "tenant-a",
                        "tenant"
                )
        ));

        List<PrivacyAuditDeadLetterEntry> entries = repository.findByCriteria("tenant-a", "tenant", PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertEquals(List.of("tenant-aware"), entries.stream().map(PrivacyAuditDeadLetterEntry::resourceId).toList());
        assertEquals("tenant-a", repository.findAll().get(0).details().get("tenant"));
    }
}
