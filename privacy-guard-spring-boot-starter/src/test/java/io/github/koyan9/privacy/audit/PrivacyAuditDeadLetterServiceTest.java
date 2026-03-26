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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyAuditDeadLetterServiceTest {

    @Test
    void returnsDeadLettersFromRepository() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "A", "one", Instant.now(), "READ", "Patient", "a", "actor", "OK", Map.of()));
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, event -> {
        });

        assertEquals(1, service.findAll().size());
    }

    @Test
    void replaysAndDeletesSingleDeadLetterEntryWithReplayMetadata() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "A", "one", Instant.now(), "READ", "Patient", "a", "actor", "OK", Map.of()));
        AtomicReference<PrivacyAuditEvent> replayed = new AtomicReference<>();
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, replayed::set);
        long id = repository.findAll().get(0).id();

        boolean result = service.replay(id);

        assertTrue(result);
        assertEquals("a", replayed.get().resourceId());
        assertEquals("DEAD_LETTER", replayed.get().details().get(PrivacyAuditDeadLetterEntry.REPLAY_SOURCE_KEY));
        assertEquals(String.valueOf(id), replayed.get().details().get(PrivacyAuditDeadLetterEntry.REPLAY_DEAD_LETTER_ID_KEY));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void preventsConcurrentReplayOfSameDeadLetter() throws Exception {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "A", "one", Instant.now(), "READ", "Patient", "a", "actor", "OK", Map.of()));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, event -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        long id = repository.findAll().get(0).id();
        AtomicReference<Boolean> secondResult = new AtomicReference<>();

        Thread first = new Thread(() -> service.replay(id));
        Thread second = new Thread(() -> secondResult.set(service.replay(id)));
        first.start();
        entered.await();
        second.start();
        second.join();
        release.countDown();
        first.join();

        assertFalse(Boolean.TRUE.equals(secondResult.get()));
    }

    @Test
    void returnsFalseWhenDeadLetterIsMissing() {
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(new InMemoryPrivacyAuditDeadLetterRepository(), event -> {
        });

        assertFalse(service.replay(999L));
    }

    @Test
    void keepsEntryWhenReplayFails() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "A", "one", Instant.now(), "READ", "Patient", "a", "actor", "OK", Map.of()));
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, event -> {
            throw new IllegalStateException("failed");
        });
        long id = repository.findAll().get(0).id();

        assertThrows(IllegalStateException.class, () -> service.replay(id));
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void replaysAllAndReportsFailures() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "A", "one", Instant.now(), "READ", "Patient", "a", "actor", "OK", Map.of()));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "B", "two", Instant.now(), "READ", "Patient", "b", "actor", "OK", Map.of()));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "C", "three", Instant.now(), "READ", "Patient", "c", "actor", "OK", Map.of()));
        List<String> replayedIds = new ArrayList<>();
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, event -> {
            replayedIds.add(event.resourceId());
            if ("b".equals(event.resourceId())) {
                throw new IllegalStateException("failed");
            }
        });

        PrivacyAuditDeadLetterReplayResult result = service.replayAll(3);

        assertEquals(3, result.requested());
        assertEquals(2, result.replayed());
        assertEquals(1, result.failed());
        assertThat(replayedIds).containsExactly("c", "b", "a");
        assertThat(repository.findAll()).singleElement().satisfies(entry -> assertEquals("b", entry.resourceId()));
    }

    @Test
    void replaysProvidedEntriesWithoutRefetchingThemById() {
        AtomicReference<String> replayed = new AtomicReference<>();
        AtomicReference<Long> deleted = new AtomicReference<>();
        PrivacyAuditDeadLetterRepository repository = new PrivacyAuditDeadLetterRepository() {
            @Override
            public void save(PrivacyAuditDeadLetterEntry entry) {
            }

            @Override
            public java.util.Optional<PrivacyAuditDeadLetterEntry> findById(long id) {
                throw new AssertionError("findById should not be called when replaying provided entries");
            }

            @Override
            public boolean deleteById(long id) {
                deleted.set(id);
                return true;
            }
        };
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, event -> replayed.set(event.resourceId()));
        PrivacyAuditDeadLetterEntry entry = new PrivacyAuditDeadLetterEntry(
                42L,
                Instant.now(),
                3,
                "TypeA",
                "failure",
                Instant.now(),
                "READ",
                "Patient",
                "tenant-selected",
                "actor",
                "OK",
                Map.of("tenant", "tenant-a")
        );

        PrivacyAuditDeadLetterReplayResult result = service.replayEntries(List.of(entry));

        assertEquals(1, result.requested());
        assertEquals(1, result.replayed());
        assertEquals(0, result.failed());
        assertEquals("tenant-selected", replayed.get());
        assertEquals(42L, deleted.get());
    }

    @Test
    void deletesByCriteria() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "TypeA", "one", Instant.now(), "READ", "Patient", "a", "actor", "OK", Map.of()));
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "TypeB", "two", Instant.now(), "WRITE", "Order", "b", "actor", "OK", Map.of()));
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, event -> {
        });

        int deleted = service.deleteByCriteria(new PrivacyAuditDeadLetterQueryCriteria(
                null, null,
                null, null,
                null, null,
                null, null,
                null, null,
                "TypeA", null,
                null, null,
                null, null,
                PrivacyAuditSortDirection.DESC,
                100,
                0
        ));

        assertEquals(1, deleted);
        assertThat(repository.findAll()).extracting("resourceId").containsExactly("b");
    }

    @Test
    void skipsPreselectedEntriesWithoutIdsDuringReplay() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        List<String> replayedIds = new ArrayList<>();
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, event -> replayedIds.add(event.resourceId()));

        PrivacyAuditDeadLetterReplayResult result = service.replayEntries(List.of(
                new PrivacyAuditDeadLetterEntry(
                        null,
                        Instant.now(),
                        3,
                        "TypeA",
                        "missing-id",
                        Instant.now(),
                        "READ",
                        "Patient",
                        "no-id",
                        "actor",
                        "OK",
                        Map.of()
                )
        ));

        assertEquals(1, result.requested());
        assertEquals(0, result.replayed());
        assertEquals(0, result.failed());
        assertThat(replayedIds).isEmpty();
    }
}
