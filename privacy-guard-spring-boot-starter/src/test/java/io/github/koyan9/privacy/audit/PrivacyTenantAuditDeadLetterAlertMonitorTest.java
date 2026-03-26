/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyTenantAuditDeadLetterAlertMonitorTest {

    @Test
    void notifiesOnTenantWarningAndRecoveryTransitions() throws Exception {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        PrivacyTenantAuditDeadLetterObservationService observationService = new PrivacyTenantAuditDeadLetterObservationService(
                new PrivacyTenantAuditDeadLetterQueryService(
                        new PrivacyAuditDeadLetterService(repository, event -> {
                        }),
                        new PrivacyAuditDeadLetterStatsService(repository),
                        () -> "tenant-a",
                        tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant")
                ),
                () -> "tenant-a",
                1,
                2
        );
        RecordingTenantCallback callback = new RecordingTenantCallback();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        PrivacyTenantAuditDeadLetterAlertMonitor monitor = new PrivacyTenantAuditDeadLetterAlertMonitor(
                observationService,
                List.of(callback),
                List.of("tenant-a"),
                executor,
                Duration.ofMillis(25),
                true
        );
        try {
            repository.save(entry("dead-letter-a", "tenant-a"));
            RecordedTenantAlert warning = callback.await(Duration.ofSeconds(2));
            assertThat(warning.tenantId()).isEqualTo("tenant-a");
            assertThat(warning.event().currentSnapshot().state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.WARNING);

            repository.clear();
            RecordedTenantAlert recovery = callback.await(Duration.ofSeconds(2));
            assertThat(recovery.tenantId()).isEqualTo("tenant-a");
            assertThat(recovery.event().recovery()).isTrue();
            assertThat(recovery.event().currentSnapshot().state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.CLEAR);
        } finally {
            monitor.destroy();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void filtersCallbacksBySupportedTenant() throws Exception {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        PrivacyTenantAuditDeadLetterObservationService observationService = new PrivacyTenantAuditDeadLetterObservationService(
                new PrivacyTenantAuditDeadLetterQueryService(
                        new PrivacyAuditDeadLetterService(repository, event -> {
                        }),
                        new PrivacyAuditDeadLetterStatsService(repository),
                        () -> "tenant-a",
                        tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant")
                ),
                () -> "tenant-a",
                1,
                2
        );
        RecordingTenantCallback callback = new RecordingTenantCallback() {
            @Override
            public boolean supportsTenant(String tenantId) {
                return "tenant-b".equals(tenantId);
            }
        };
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        PrivacyTenantAuditDeadLetterAlertMonitor monitor = new PrivacyTenantAuditDeadLetterAlertMonitor(
                observationService,
                List.of(callback),
                List.of("tenant-a"),
                executor,
                Duration.ofMillis(25),
                true
        );
        try {
            repository.save(entry("dead-letter-a", "tenant-a"));
            assertThat(callback.await(Duration.ofMillis(250))).isNull();
        } finally {
            monitor.destroy();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void appliesTenantSpecificNotifyOnRecoveryAndRecordsTransitions() throws Exception {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        PrivacyTenantAuditDeadLetterObservationService observationService = new PrivacyTenantAuditDeadLetterObservationService(
                new PrivacyTenantAuditDeadLetterQueryService(
                        new PrivacyAuditDeadLetterService(repository, event -> {
                        }),
                        new PrivacyAuditDeadLetterStatsService(repository),
                        () -> "tenant-a",
                        tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant")
                ),
                () -> "tenant-a",
                tenantId -> new PrivacyTenantDeadLetterObservabilityPolicy(1L, 2L, Boolean.FALSE),
                1,
                2
        );
        RecordingTenantCallback callback = new RecordingTenantCallback();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PrivacyTenantAuditTelemetry telemetry = new MicrometerPrivacyTenantAuditTelemetry(registry);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        PrivacyTenantAuditDeadLetterAlertMonitor monitor = new PrivacyTenantAuditDeadLetterAlertMonitor(
                observationService,
                List.of(callback),
                List.of("tenant-a"),
                executor,
                Duration.ofMillis(25),
                tenantId -> new PrivacyTenantDeadLetterObservabilityPolicy(1L, 2L, Boolean.FALSE),
                true,
                telemetry
        );
        try {
            repository.save(entry("dead-letter-a", "tenant-a"));
            RecordedTenantAlert warning = callback.await(Duration.ofSeconds(2));
            assertThat(warning).isNotNull();

            repository.clear();
            assertThat(callback.await(Duration.ofMillis(250))).isNull();
            assertThat(registry.get("privacy.audit.deadletters.alert.tenant.transitions")
                    .tag("tenant", "tenant-a")
                    .tag("state", "warning")
                    .tag("recovery", "false")
                    .counter()
                    .count()).isEqualTo(1.0d);
            assertThat(registry.get("privacy.audit.deadletters.alert.tenant.transitions")
                    .tag("tenant", "tenant-a")
                    .tag("state", "clear")
                    .tag("recovery", "true")
                    .counter()
                    .count()).isEqualTo(1.0d);
        } finally {
            monitor.destroy();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private PrivacyAuditDeadLetterEntry entry(String resourceId, String tenantId) {
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
                Map.of("tenant", tenantId, "phone", "138****8000")
        );
    }

    static class RecordingTenantCallback implements PrivacyTenantAuditDeadLetterAlertCallback {

        private final LinkedBlockingQueue<RecordedTenantAlert> events = new LinkedBlockingQueue<>();

        @Override
        public void handle(String tenantId, PrivacyAuditDeadLetterAlertEvent event) {
            events.add(new RecordedTenantAlert(tenantId, event));
        }

        RecordedTenantAlert await(Duration timeout) throws InterruptedException {
            return events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    record RecordedTenantAlert(String tenantId, PrivacyAuditDeadLetterAlertEvent event) {
    }
}
