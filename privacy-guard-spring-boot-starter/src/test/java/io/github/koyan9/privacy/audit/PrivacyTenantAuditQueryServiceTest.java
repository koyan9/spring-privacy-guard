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

class PrivacyTenantAuditQueryServiceTest {

    @Test
    void filtersEventsAndStatsByTenantDetailKey() {
        PrivacyAuditQueryRepository repository = criteria -> List.of(
                event("A1", "tenant-a"),
                event("A2", "tenant-a"),
                event("B1", "tenant-b"),
                event("P1", "public")
        );
        PrivacyAuditQueryService queryService = new PrivacyAuditQueryService(repository);
        PrivacyAuditStatsService statsService = new PrivacyAuditStatsService(
                criteria -> new PrivacyAuditQueryStats(4, Map.of("READ", 4L), Map.of("SUCCESS", 4L), Map.of("Patient", 4L))
        );
        PrivacyTenantAuditQueryService service = new PrivacyTenantAuditQueryService(
                queryService,
                statsService,
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant")
        );

        List<PrivacyAuditEvent> events = service.findByCriteria("tenant-a", PrivacyAuditQueryCriteria.recent(10));
        PrivacyAuditQueryStats stats = service.computeStats("tenant-a", PrivacyAuditQueryCriteria.recent(10));

        assertThat(events).extracting(PrivacyAuditEvent::resourceId).containsExactly("A1", "A2");
        assertThat(stats.total()).isEqualTo(2);
        assertThat(stats.byAction()).containsEntry("READ", 2L);
    }

    @Test
    void delegatesToCurrentTenantWhenRequested() {
        PrivacyAuditQueryRepository repository = criteria -> List.of(event("A1", "tenant-a"), event("B1", "tenant-b"));
        PrivacyAuditQueryService queryService = new PrivacyAuditQueryService(repository);
        PrivacyAuditStatsService statsService = new PrivacyAuditStatsService(criteria -> new PrivacyAuditQueryStats(2, Map.of("READ", 2L), Map.of(), Map.of()));
        PrivacyTenantAuditQueryService service = new PrivacyTenantAuditQueryService(
                queryService,
                statsService,
                () -> "tenant-b",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant")
        );

        List<PrivacyAuditEvent> events = service.findForCurrentTenant(PrivacyAuditQueryCriteria.recent(10));

        assertThat(events).extracting(PrivacyAuditEvent::resourceId).containsExactly("B1");
    }

    @Test
    void prefersTenantNativeReadRepositoryWhenAvailable() {
        PrivacyAuditQueryRepository fallbackRepository = criteria -> List.of(event("fallback", "tenant-a"));
        PrivacyAuditQueryService queryService = new PrivacyAuditQueryService(fallbackRepository);
        PrivacyAuditStatsService statsService = new PrivacyAuditStatsService(
                criteria -> new PrivacyAuditQueryStats(99, Map.of("READ", 99L), Map.of(), Map.of())
        );
        PrivacyTenantAuditReadRepository nativeRepository = new PrivacyTenantAuditReadRepository() {
            @Override
            public List<PrivacyAuditEvent> findByCriteria(String tenantId, String tenantDetailKey, PrivacyAuditQueryCriteria criteria) {
                return List.of(event("native", tenantId));
            }

            @Override
            public PrivacyAuditQueryStats computeStats(String tenantId, String tenantDetailKey, PrivacyAuditQueryCriteria criteria) {
                return new PrivacyAuditQueryStats(1, Map.of("READ", 1L), Map.of("SUCCESS", 1L), Map.of("Patient", 1L));
            }
        };
        PrivacyTenantAuditQueryService service = new PrivacyTenantAuditQueryService(
                queryService,
                statsService,
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                nativeRepository
        );

        List<PrivacyAuditEvent> events = service.findByCriteria("tenant-a", PrivacyAuditQueryCriteria.recent(10));
        PrivacyAuditQueryStats stats = service.computeStats("tenant-a", PrivacyAuditQueryCriteria.recent(10));

        assertThat(events).extracting(PrivacyAuditEvent::resourceId).containsExactly("native");
        assertThat(stats.total()).isEqualTo(1);
        assertThat(stats.byOutcome()).containsEntry("SUCCESS", 1L);
    }

    private PrivacyAuditEvent event(String resourceId, String tenant) {
        return new PrivacyAuditEvent(
                Instant.parse("2026-03-18T00:00:00Z"),
                "READ",
                "Patient",
                resourceId,
                "actor",
                "SUCCESS",
                Map.of("tenant", tenant)
        );
    }
}
