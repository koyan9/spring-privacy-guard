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

class PrivacyTenantAuditDeadLetterObservationServiceTest {

    @Test
    void computesTenantScopedBacklogSnapshot() {
        PrivacyTenantAuditDeadLetterObservationService service = new PrivacyTenantAuditDeadLetterObservationService(
                tenantQueryService(),
                () -> "tenant-a",
                1,
                5
        );

        PrivacyAuditDeadLetterBacklogSnapshot snapshot = service.currentSnapshot("tenant-a");

        assertThat(snapshot.total()).isEqualTo(2L);
        assertThat(snapshot.state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.WARNING);
        assertThat(snapshot.byAction()).containsEntry("READ", 2L);
        assertThat(snapshot.byErrorType()).containsEntry("TypeA", 2L);
    }

    @Test
    void usesCurrentTenantForCurrentSnapshot() {
        PrivacyTenantAuditDeadLetterObservationService service = new PrivacyTenantAuditDeadLetterObservationService(
                tenantQueryService(),
                () -> "tenant-b",
                1,
                5
        );

        PrivacyAuditDeadLetterBacklogSnapshot snapshot = service.currentSnapshotForCurrentTenant();

        assertThat(snapshot.total()).isEqualTo(1L);
        assertThat(snapshot.state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.WARNING);
        assertThat(snapshot.byAction()).containsEntry("READ", 1L);
    }

    @Test
    void returnsClearSnapshotWhenTenantMissing() {
        PrivacyTenantAuditDeadLetterObservationService service = new PrivacyTenantAuditDeadLetterObservationService(
                tenantQueryService(),
                () -> null,
                1,
                5
        );

        PrivacyAuditDeadLetterBacklogSnapshot snapshot = service.currentSnapshot(null);

        assertThat(snapshot.total()).isZero();
        assertThat(snapshot.state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.CLEAR);
        assertThat(snapshot.byAction()).isEmpty();
    }

    @Test
    void appliesTenantSpecificThresholdOverrides() {
        PrivacyTenantAuditDeadLetterObservationService service = new PrivacyTenantAuditDeadLetterObservationService(
                tenantQueryService(),
                () -> "tenant-a",
                tenantId -> {
                    if ("tenant-a".equals(tenantId)) {
                        return new PrivacyTenantDeadLetterObservabilityPolicy(2L, 2L, Boolean.FALSE);
                    }
                    return PrivacyTenantDeadLetterObservabilityPolicy.none();
                },
                1,
                5
        );

        PrivacyAuditDeadLetterBacklogSnapshot tenantASnapshot = service.currentSnapshot("tenant-a");
        PrivacyAuditDeadLetterBacklogSnapshot tenantBSnapshot = service.currentSnapshot("tenant-b");

        assertThat(tenantASnapshot.warningThreshold()).isEqualTo(2L);
        assertThat(tenantASnapshot.downThreshold()).isEqualTo(2L);
        assertThat(tenantASnapshot.state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.DOWN);
        assertThat(tenantBSnapshot.warningThreshold()).isEqualTo(1L);
        assertThat(tenantBSnapshot.downThreshold()).isEqualTo(5L);
        assertThat(tenantBSnapshot.state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.WARNING);
    }

    private PrivacyTenantAuditDeadLetterQueryService tenantQueryService() {
        PrivacyAuditDeadLetterRepository repository = new PrivacyAuditDeadLetterRepository() {
            @Override
            public void save(PrivacyAuditDeadLetterEntry entry) {
            }

            @Override
            public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
                return List.of(
                        entry("A1", "tenant-a"),
                        entry("A2", "tenant-a"),
                        entry("B1", "tenant-b")
                );
            }
        };
        PrivacyAuditDeadLetterService deadLetterService = new PrivacyAuditDeadLetterService(repository, event -> {
        });
        PrivacyAuditDeadLetterStatsService statsService = new PrivacyAuditDeadLetterStatsService(
                criteria -> new PrivacyAuditDeadLetterStats(3, Map.of("READ", 3L), Map.of("OK", 3L), Map.of("Patient", 3L), Map.of("TypeA", 3L))
        );
        return new PrivacyTenantAuditDeadLetterQueryService(
                deadLetterService,
                statsService,
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant")
        );
    }

    private PrivacyAuditDeadLetterEntry entry(String resourceId, String tenantId) {
        return new PrivacyAuditDeadLetterEntry(
                1L,
                Instant.parse("2026-03-20T00:00:00Z"),
                3,
                "TypeA",
                "failure",
                Instant.parse("2026-03-20T00:00:00Z"),
                "READ",
                "Patient",
                resourceId,
                "actor",
                "OK",
                Map.of("tenant", tenantId)
        );
    }
}
