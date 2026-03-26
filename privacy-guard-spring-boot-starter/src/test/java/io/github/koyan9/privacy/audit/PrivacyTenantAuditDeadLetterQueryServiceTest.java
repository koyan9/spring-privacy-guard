/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyTenantAuditDeadLetterQueryServiceTest {

    @Test
    void filtersDeadLettersAndStatsByTenantDetailKey() {
        PrivacyAuditDeadLetterRepository repository = new PrivacyAuditDeadLetterRepository() {
            @Override
            public void save(PrivacyAuditDeadLetterEntry entry) {
            }

            @Override
            public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
                return List.of(
                        entry("A1", "tenant-a"),
                        entry("A2", "tenant-a"),
                        entry("B1", "tenant-b"),
                        entry("P1", "public")
                );
            }
        };
        PrivacyAuditPublisher replayPublisher = event -> {
        };
        PrivacyAuditDeadLetterService deadLetterService = new PrivacyAuditDeadLetterService(repository, replayPublisher);
        PrivacyAuditDeadLetterStatsService deadLetterStatsService = new PrivacyAuditDeadLetterStatsService(
                criteria -> new PrivacyAuditDeadLetterStats(4, Map.of("READ", 4L), Map.of("OK", 4L), Map.of("Patient", 4L), Map.of("TypeA", 4L))
        );
        PrivacyTenantAuditDeadLetterQueryService service = new PrivacyTenantAuditDeadLetterQueryService(
                deadLetterService,
                deadLetterStatsService,
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant")
        );

        List<PrivacyAuditDeadLetterEntry> entries = service.findByCriteria("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10));
        PrivacyAuditDeadLetterStats stats = service.computeStats("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertThat(entries).extracting(PrivacyAuditDeadLetterEntry::resourceId).containsExactly("A1", "A2");
        assertThat(stats.total()).isEqualTo(2);
        assertThat(stats.byAction()).containsEntry("READ", 2L);
        assertThat(stats.byErrorType()).containsEntry("TypeA", 2L);
    }

    @Test
    void delegatesToCurrentTenantWhenRequested() {
        PrivacyAuditDeadLetterRepository repository = new PrivacyAuditDeadLetterRepository() {
            @Override
            public void save(PrivacyAuditDeadLetterEntry entry) {
            }

            @Override
            public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
                return List.of(entry("A1", "tenant-a"), entry("B1", "tenant-b"));
            }
        };
        PrivacyAuditDeadLetterService deadLetterService = new PrivacyAuditDeadLetterService(repository, event -> {
        });
        PrivacyAuditDeadLetterStatsService deadLetterStatsService = new PrivacyAuditDeadLetterStatsService(
                criteria -> new PrivacyAuditDeadLetterStats(2, Map.of("READ", 2L), Map.of(), Map.of(), Map.of())
        );
        PrivacyTenantAuditDeadLetterQueryService service = new PrivacyTenantAuditDeadLetterQueryService(
                deadLetterService,
                deadLetterStatsService,
                () -> "tenant-b",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant")
        );

        List<PrivacyAuditDeadLetterEntry> entries = service.findForCurrentTenant(PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertThat(entries).extracting(PrivacyAuditDeadLetterEntry::resourceId).containsExactly("B1");
    }

    @Test
    void prefersTenantNativeDeadLetterReadRepositoryWhenAvailable() {
        PrivacyAuditDeadLetterRepository fallbackRepository = new PrivacyAuditDeadLetterRepository() {
            @Override
            public void save(PrivacyAuditDeadLetterEntry entry) {
            }

            @Override
            public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
                return List.of(entry("fallback", "tenant-a"));
            }
        };
        PrivacyAuditDeadLetterService deadLetterService = new PrivacyAuditDeadLetterService(fallbackRepository, event -> {
        });
        PrivacyAuditDeadLetterStatsService deadLetterStatsService = new PrivacyAuditDeadLetterStatsService(
                criteria -> new PrivacyAuditDeadLetterStats(99, Map.of("READ", 99L), Map.of(), Map.of(), Map.of())
        );
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PrivacyTenantAuditTelemetry telemetry = new MicrometerPrivacyTenantAuditTelemetry(meterRegistry);
        PrivacyTenantAuditDeadLetterReadRepository nativeRepository = new PrivacyTenantAuditDeadLetterReadRepository() {
            @Override
            public List<PrivacyAuditDeadLetterEntry> findByCriteria(
                    String tenantId,
                    String tenantDetailKey,
                    PrivacyAuditDeadLetterQueryCriteria criteria
            ) {
                return List.of(entry("native", tenantId));
            }

            @Override
            public PrivacyAuditDeadLetterStats computeStats(
                    String tenantId,
                    String tenantDetailKey,
                    PrivacyAuditDeadLetterQueryCriteria criteria
            ) {
                return new PrivacyAuditDeadLetterStats(1, Map.of("READ", 1L), Map.of("OK", 1L), Map.of("Patient", 1L), Map.of("TypeA", 1L));
            }
        };
        PrivacyTenantAuditDeadLetterQueryService service = new PrivacyTenantAuditDeadLetterQueryService(
                deadLetterService,
                deadLetterStatsService,
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                nativeRepository,
                telemetry
        );

        List<PrivacyAuditDeadLetterEntry> entries = service.findByCriteria("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10));
        PrivacyAuditDeadLetterStats stats = service.computeStats("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertThat(entries).extracting(PrivacyAuditDeadLetterEntry::resourceId).containsExactly("native");
        assertThat(stats.total()).isEqualTo(1);
        assertThat(stats.byErrorType()).containsEntry("TypeA", 1L);
        assertThat(meterRegistry.get("privacy.audit.tenant.read.path").tag("domain", "dead_letter").tag("path", "native").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("privacy.audit.tenant.read.path").tag("domain", "dead_letter_stats").tag("path", "native").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void recordsFallbackPathWhenNativeDeadLetterRepositoryUnavailable() {
        PrivacyAuditDeadLetterRepository repository = new PrivacyAuditDeadLetterRepository() {
            @Override
            public void save(PrivacyAuditDeadLetterEntry entry) {
            }

            @Override
            public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
                return List.of(entry("A1", "tenant-a"), entry("B1", "tenant-b"));
            }
        };
        PrivacyAuditDeadLetterService deadLetterService = new PrivacyAuditDeadLetterService(repository, event -> {
        });
        PrivacyAuditDeadLetterStatsService deadLetterStatsService = new PrivacyAuditDeadLetterStatsService(
                criteria -> new PrivacyAuditDeadLetterStats(2, Map.of("READ", 2L), Map.of("OK", 2L), Map.of("Patient", 2L), Map.of("TypeA", 2L))
        );
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PrivacyTenantAuditDeadLetterQueryService service = new PrivacyTenantAuditDeadLetterQueryService(
                deadLetterService,
                deadLetterStatsService,
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                null,
                new MicrometerPrivacyTenantAuditTelemetry(meterRegistry)
        );

        service.findByCriteria("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10));
        service.computeStats("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertThat(meterRegistry.get("privacy.audit.tenant.read.path").tag("domain", "dead_letter").tag("path", "fallback").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("privacy.audit.tenant.read.path").tag("domain", "dead_letter_stats").tag("path", "fallback").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void resolvesTelemetryLazilyFromSupplier() {
        PrivacyAuditDeadLetterRepository repository = new PrivacyAuditDeadLetterRepository() {
            @Override
            public void save(PrivacyAuditDeadLetterEntry entry) {
            }

            @Override
            public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
                return List.of(entry("A1", "tenant-a"));
            }
        };
        PrivacyAuditDeadLetterService deadLetterService = new PrivacyAuditDeadLetterService(repository, event -> {
        });
        PrivacyAuditDeadLetterStatsService deadLetterStatsService = new PrivacyAuditDeadLetterStatsService(
                criteria -> new PrivacyAuditDeadLetterStats(1, Map.of("READ", 1L), Map.of("OK", 1L), Map.of("Patient", 1L), Map.of("TypeA", 1L))
        );
        AtomicReference<PrivacyTenantAuditTelemetry> telemetryRef = new AtomicReference<>();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        telemetryRef.set(new MicrometerPrivacyTenantAuditTelemetry(meterRegistry));

        PrivacyTenantAuditDeadLetterQueryService service = new PrivacyTenantAuditDeadLetterQueryService(
                deadLetterService,
                deadLetterStatsService,
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                null,
                telemetryRef::get
        );

        service.findByCriteria("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertThat(meterRegistry.get("privacy.audit.tenant.read.path").tag("domain", "dead_letter").tag("path", "fallback").counter().count()).isEqualTo(1.0d);
    }

    private PrivacyAuditDeadLetterEntry entry(String resourceId, String tenant) {
        return new PrivacyAuditDeadLetterEntry(
                1L,
                Instant.parse("2026-03-18T00:00:00Z"),
                3,
                "TypeA",
                "failure",
                Instant.parse("2026-03-18T00:00:00Z"),
                "READ",
                "Patient",
                resourceId,
                "actor",
                "OK",
                Map.of("tenant", tenant)
        );
    }
}
