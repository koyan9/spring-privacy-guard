/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyAuditDeadLetterAlertMonitorTest {

    @Test
    void notifiesOnWarningAndRecoveryTransitions() throws Exception {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        PrivacyAuditDeadLetterObservationService observationService = new PrivacyAuditDeadLetterObservationService(
                new PrivacyAuditDeadLetterStatsService(repository),
                1,
                2
        );
        RecordingCallback callback = new RecordingCallback();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        PrivacyAuditDeadLetterAlertMonitor monitor = new PrivacyAuditDeadLetterAlertMonitor(
                observationService,
                java.util.List.of(callback),
                executor,
                Duration.ofMillis(25),
                true
        );
        try {
            repository.save(entry("dead-letter-a"));
            PrivacyAuditDeadLetterAlertEvent warning = callback.await(Duration.ofSeconds(2));
            assertThat(warning.currentSnapshot().state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.WARNING);
            assertThat(warning.currentSnapshot().total()).isEqualTo(1L);
            assertThat(warning.previousSnapshot()).isNull();

            repository.clear();
            PrivacyAuditDeadLetterAlertEvent recovery = callback.await(Duration.ofSeconds(2));
            assertThat(recovery.recovery()).isTrue();
            assertThat(recovery.currentSnapshot().state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.CLEAR);
            assertThat(recovery.previousSnapshot().state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.WARNING);
        } finally {
            monitor.destroy();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void skipsRecoveryNotificationWhenDisabled() throws Exception {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        PrivacyAuditDeadLetterObservationService observationService = new PrivacyAuditDeadLetterObservationService(
                new PrivacyAuditDeadLetterStatsService(repository),
                1,
                2
        );
        RecordingCallback callback = new RecordingCallback();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        PrivacyAuditDeadLetterAlertMonitor monitor = new PrivacyAuditDeadLetterAlertMonitor(
                observationService,
                java.util.List.of(callback),
                executor,
                Duration.ofMillis(25),
                false
        );
        try {
            repository.save(entry("dead-letter-a"));
            PrivacyAuditDeadLetterAlertEvent warning = callback.await(Duration.ofSeconds(2));
            assertThat(warning.currentSnapshot().state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.WARNING);

            repository.clear();
            assertThat(callback.await(Duration.ofMillis(250))).isNull();
        } finally {
            monitor.destroy();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private PrivacyAuditDeadLetterEntry entry(String resourceId) {
        return new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "failure",
                Instant.parse("2026-03-08T00:00:00Z"),
                "READ",
                "Patient",
                resourceId,
                "actor",
                "OK",
                Map.of("phone", "138****8000")
        );
    }

    static class RecordingCallback implements PrivacyAuditDeadLetterAlertCallback {

        private final LinkedBlockingQueue<PrivacyAuditDeadLetterAlertEvent> events = new LinkedBlockingQueue<>();

        @Override
        public void handle(PrivacyAuditDeadLetterAlertEvent event) {
            events.add(event);
        }

        PrivacyAuditDeadLetterAlertEvent await(Duration timeout) throws InterruptedException {
            return events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
