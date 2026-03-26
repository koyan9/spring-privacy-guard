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

class PrivacyTenantAuditDeadLetterOperationsServiceTest {

    @Test
    void deletesDeadLettersByTenant() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(entry("A1", "tenant-a"));
        repository.save(entry("B1", "tenant-b"));
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, event -> {
        });
        PrivacyTenantAuditDeadLetterQueryService queryService = new PrivacyTenantAuditDeadLetterQueryService(
                service,
                new PrivacyAuditDeadLetterStatsService(repository),
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant")
        );
        PrivacyTenantAuditDeadLetterOperationsService operationsService = new PrivacyTenantAuditDeadLetterOperationsService(
                service,
                queryService,
                () -> "tenant-a"
        );

        int deleted = operationsService.deleteByCriteria("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll()).extracting(PrivacyAuditDeadLetterEntry::resourceId).containsExactly("B1");
    }

    @Test
    void replaysDeadLettersByTenant() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(entry("A1", "tenant-a"));
        repository.save(entry("B1", "tenant-b"));
        AtomicReference<String> replayed = new AtomicReference<>();
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, event -> replayed.set(event.resourceId()));
        PrivacyTenantAuditDeadLetterQueryService queryService = new PrivacyTenantAuditDeadLetterQueryService(
                service,
                new PrivacyAuditDeadLetterStatsService(repository),
                () -> "tenant-b",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant")
        );
        PrivacyTenantAuditDeadLetterOperationsService operationsService = new PrivacyTenantAuditDeadLetterOperationsService(
                service,
                queryService,
                () -> "tenant-b"
        );

        PrivacyAuditDeadLetterReplayResult result = operationsService.replayByCriteria("tenant-b", PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertThat(result.requested()).isEqualTo(1);
        assertThat(result.replayed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(replayed.get()).isEqualTo("B1");
        assertThat(repository.findAll()).extracting(PrivacyAuditDeadLetterEntry::resourceId).containsExactly("A1");
    }

    @Test
    void prefersNativeTenantReplayRepositoryWhenAvailable() {
        AtomicReference<String> replayed = new AtomicReference<>();
        PrivacyAuditDeadLetterRepository repository = new PrivacyAuditDeadLetterRepository() {
            @Override
            public void save(PrivacyAuditDeadLetterEntry entry) {
            }

            @Override
            public boolean deleteById(long id) {
                throw new AssertionError("native tenant replay should own delete orchestration");
            }
        };
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, event -> replayed.set(event.resourceId()));
        PrivacyTenantAuditPolicyResolver policyResolver =
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant");
        PrivacyTenantAuditDeadLetterQueryService queryService = new PrivacyTenantAuditDeadLetterQueryService(
                service,
                new PrivacyAuditDeadLetterStatsService(new InMemoryPrivacyAuditDeadLetterRepository()),
                () -> "tenant-b",
                policyResolver
        );
        AtomicReference<String> tenantRef = new AtomicReference<>();
        AtomicReference<String> detailKeyRef = new AtomicReference<>();
        AtomicReference<PrivacyAuditDeadLetterQueryCriteria> criteriaRef = new AtomicReference<>();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PrivacyTenantAuditDeadLetterReplayRepository replayRepository = (tenantId, tenantDetailKey, criteria, replayAction) -> {
            tenantRef.set(tenantId);
            detailKeyRef.set(tenantDetailKey);
            criteriaRef.set(criteria);
            PrivacyAuditDeadLetterEntry entry = entry(91L, "tenant-b-native", tenantId);
            boolean replayedNative = replayAction.test(entry);
            return new PrivacyAuditDeadLetterReplayResult(
                    1,
                    replayedNative ? 1 : 0,
                    replayedNative ? 0 : 1,
                    replayedNative ? List.of(91L) : List.of(),
                    replayedNative ? List.of() : List.of(91L)
            );
        };
        PrivacyTenantAuditDeadLetterOperationsService operationsService = new PrivacyTenantAuditDeadLetterOperationsService(
                service,
                queryService,
                () -> "tenant-b",
                policyResolver,
                null,
                replayRepository,
                new MicrometerPrivacyTenantAuditTelemetry(meterRegistry)
        );
        PrivacyAuditDeadLetterQueryCriteria criteria = PrivacyAuditDeadLetterQueryCriteria.recent(10);

        PrivacyAuditDeadLetterReplayResult result = operationsService.replayByCriteria("tenant-b", criteria);

        assertThat(result.requested()).isEqualTo(1);
        assertThat(result.replayed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(replayed.get()).isEqualTo("tenant-b-native");
        assertThat(tenantRef.get()).isEqualTo("tenant-b");
        assertThat(detailKeyRef.get()).isEqualTo("tenant");
        assertThat(criteriaRef.get()).isEqualTo(criteria.normalize());
        assertThat(meterRegistry.get("privacy.audit.tenant.write.path")
                .tag("domain", "dead_letter_replay")
                .tag("path", "native")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void replaysTenantSelectedEntriesWithoutLookingThemUpAgainById() {
        AtomicReference<String> replayed = new AtomicReference<>();
        AtomicReference<Long> deletedId = new AtomicReference<>();
        PrivacyAuditDeadLetterRepository repository = new PrivacyAuditDeadLetterRepository() {
            @Override
            public void save(PrivacyAuditDeadLetterEntry entry) {
            }

            @Override
            public java.util.Optional<PrivacyAuditDeadLetterEntry> findById(long id) {
                throw new AssertionError("tenant replay should reuse the selected entry instead of calling findById");
            }

            @Override
            public boolean deleteById(long id) {
                deletedId.set(id);
                return true;
            }
        };
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, event -> replayed.set(event.resourceId()));
        PrivacyTenantAuditDeadLetterReadRepository nativeReadRepository = new PrivacyTenantAuditDeadLetterReadRepository() {
            @Override
            public java.util.List<PrivacyAuditDeadLetterEntry> findByCriteria(
                    String tenantId,
                    String tenantDetailKey,
                    PrivacyAuditDeadLetterQueryCriteria criteria
            ) {
                return java.util.List.of(new PrivacyAuditDeadLetterEntry(
                        77L,
                        Instant.parse("2026-03-18T00:00:00Z"),
                        3,
                        "TypeA",
                        "failure",
                        Instant.parse("2026-03-18T00:00:00Z"),
                        "READ",
                        "Patient",
                        "B-native",
                        "actor",
                        "OK",
                        Map.of("tenant", tenantId)
                ));
            }

            @Override
            public PrivacyAuditDeadLetterStats computeStats(
                    String tenantId,
                    String tenantDetailKey,
                    PrivacyAuditDeadLetterQueryCriteria criteria
            ) {
                return new PrivacyAuditDeadLetterStats(0, Map.of(), Map.of(), Map.of(), Map.of());
            }
        };
        PrivacyTenantAuditDeadLetterQueryService queryService = new PrivacyTenantAuditDeadLetterQueryService(
                service,
                new PrivacyAuditDeadLetterStatsService(new InMemoryPrivacyAuditDeadLetterRepository()),
                () -> "tenant-b",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                nativeReadRepository
        );
        PrivacyTenantAuditDeadLetterOperationsService operationsService = new PrivacyTenantAuditDeadLetterOperationsService(
                service,
                queryService,
                () -> "tenant-b"
        );

        PrivacyAuditDeadLetterReplayResult result = operationsService.replayByCriteria("tenant-b", PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertThat(result.requested()).isEqualTo(1);
        assertThat(result.replayed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(replayed.get()).isEqualTo("B-native");
        assertThat(deletedId.get()).isEqualTo(77L);
    }

    @Test
    void prefersNativeTenantDeleteRepositoryWhenAvailable() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(entry("A1", "tenant-a"));
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, event -> {
        });
        PrivacyTenantAuditPolicyResolver policyResolver =
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant");
        PrivacyTenantAuditDeadLetterQueryService queryService = new PrivacyTenantAuditDeadLetterQueryService(
                service,
                new PrivacyAuditDeadLetterStatsService(repository),
                () -> "tenant-a",
                policyResolver
        );
        java.util.concurrent.atomic.AtomicReference<String> tenantRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> detailKeyRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<PrivacyAuditDeadLetterQueryCriteria> criteriaRef = new java.util.concurrent.atomic.AtomicReference<>();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PrivacyTenantAuditDeadLetterDeleteRepository deleteRepository = (tenantId, tenantDetailKey, criteria) -> {
            tenantRef.set(tenantId);
            detailKeyRef.set(tenantDetailKey);
            criteriaRef.set(criteria);
            return 7;
        };
        PrivacyTenantAuditDeadLetterOperationsService operationsService = new PrivacyTenantAuditDeadLetterOperationsService(
                service,
                queryService,
                () -> "tenant-a",
                policyResolver,
                deleteRepository,
                null,
                new MicrometerPrivacyTenantAuditTelemetry(meterRegistry)
        );
        PrivacyAuditDeadLetterQueryCriteria criteria = PrivacyAuditDeadLetterQueryCriteria.recent(10);

        int deleted = operationsService.deleteByCriteria("tenant-a", criteria);

        assertThat(deleted).isEqualTo(7);
        assertThat(tenantRef.get()).isEqualTo("tenant-a");
        assertThat(detailKeyRef.get()).isEqualTo("tenant");
        assertThat(criteriaRef.get()).isEqualTo(criteria.normalize());
        assertThat(meterRegistry.get("privacy.audit.tenant.write.path")
                .tag("domain", "dead_letter_delete")
                .tag("path", "native")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void replaysTenantDeadLettersWithoutRefetchingEachId() {
        AtomicReference<String> replayed = new AtomicReference<>();
        PrivacyAuditDeadLetterRepository repository = new PrivacyAuditDeadLetterRepository() {
            @Override
            public void save(PrivacyAuditDeadLetterEntry entry) {
            }

            @Override
            public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
                return List.of(entry(42L, "tenant-b-replay", "tenant-b"));
            }

            @Override
            public java.util.Optional<PrivacyAuditDeadLetterEntry> findById(long id) {
                throw new AssertionError("tenant replay should not re-fetch dead letters by id");
            }

            @Override
            public boolean deleteById(long id) {
                return id == 42L;
            }
        };
        PrivacyAuditDeadLetterService service = new PrivacyAuditDeadLetterService(repository, event -> replayed.set(event.resourceId()));
        PrivacyTenantAuditPolicyResolver policyResolver =
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant");
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PrivacyTenantAuditDeadLetterQueryService queryService = new PrivacyTenantAuditDeadLetterQueryService(
                service,
                new PrivacyAuditDeadLetterStatsService(criteria -> new PrivacyAuditDeadLetterStats(1, Map.of(), Map.of(), Map.of(), Map.of())),
                () -> "tenant-b",
                policyResolver
        );
        PrivacyTenantAuditDeadLetterOperationsService operationsService = new PrivacyTenantAuditDeadLetterOperationsService(
                service,
                queryService,
                () -> "tenant-b",
                policyResolver,
                null,
                null,
                new MicrometerPrivacyTenantAuditTelemetry(meterRegistry)
        );

        PrivacyAuditDeadLetterReplayResult result = operationsService.replayByCriteria("tenant-b", PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertThat(result.requested()).isEqualTo(1);
        assertThat(result.replayed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(replayed.get()).isEqualTo("tenant-b-replay");
        assertThat(meterRegistry.get("privacy.audit.tenant.write.path")
                .tag("domain", "dead_letter_replay")
                .tag("path", "fallback")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    private PrivacyAuditDeadLetterEntry entry(String resourceId, String tenant) {
        return entry(null, resourceId, tenant);
    }

    private PrivacyAuditDeadLetterEntry entry(Long id, String resourceId, String tenant) {
        return new PrivacyAuditDeadLetterEntry(
                id,
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
