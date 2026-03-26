/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyTenantAuditDeadLetterHealthIndicatorTest {

    @Test
    void reportsUpWithWarningTenantDetailsBelowDownThreshold() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(entry("dead-letter-a", "tenant-a"));

        PrivacyTenantAuditPolicyResolver tenantPolicyResolver =
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant");
        PrivacyTenantAuditDeadLetterObservationService observationService = new PrivacyTenantAuditDeadLetterObservationService(
                new PrivacyTenantAuditDeadLetterQueryService(
                        new PrivacyAuditDeadLetterService(repository, event -> {
                        }),
                        new PrivacyAuditDeadLetterStatsService(repository),
                        PrivacyTenantProvider.noop(),
                        tenantPolicyResolver,
                        repository
                ),
                PrivacyTenantProvider.noop(),
                tenantId -> "tenant-a".equals(tenantId)
                        ? new PrivacyTenantDeadLetterObservabilityPolicy(1L, 3L, Boolean.TRUE)
                        : PrivacyTenantDeadLetterObservabilityPolicy.none(),
                1,
                3
        );
        PrivacyTenantAuditDeadLetterHealthIndicator indicator =
                new PrivacyTenantAuditDeadLetterHealthIndicator(observationService, List.of("tenant-a", "tenant-b"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails())
                .containsEntry("state", "WARNING")
                .containsEntry("tenantCount", 2)
                .containsEntry("warningTenants", List.of("tenant-a"))
                .containsEntry("downTenants", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> tenantDetails = (Map<String, Object>) indicator.health().getDetails().get("tenants");
        assertThat(tenantDetails.keySet()).contains("tenant-a", "tenant-b");
        @SuppressWarnings("unchecked")
        Map<String, Object> tenantADetails = (Map<String, Object>) tenantDetails.get("tenant-a");
        assertThat(tenantADetails).containsEntry("warningThreshold", 1L).containsEntry("downThreshold", 3L);
    }

    @Test
    void reportsDownWhenAnyTenantReachesDownThreshold() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(entry("dead-letter-a", "tenant-a"));
        repository.save(entry("dead-letter-b", "tenant-a"));

        PrivacyTenantAuditPolicyResolver tenantPolicyResolver =
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant");
        PrivacyTenantAuditDeadLetterObservationService observationService = new PrivacyTenantAuditDeadLetterObservationService(
                new PrivacyTenantAuditDeadLetterQueryService(
                        new PrivacyAuditDeadLetterService(repository, event -> {
                        }),
                        new PrivacyAuditDeadLetterStatsService(repository),
                        PrivacyTenantProvider.noop(),
                        tenantPolicyResolver,
                        repository
                ),
                PrivacyTenantProvider.noop(),
                tenantId -> "tenant-a".equals(tenantId)
                        ? new PrivacyTenantDeadLetterObservabilityPolicy(1L, 2L, Boolean.FALSE)
                        : PrivacyTenantDeadLetterObservabilityPolicy.none(),
                1,
                2
        );
        PrivacyTenantAuditDeadLetterHealthIndicator indicator =
                new PrivacyTenantAuditDeadLetterHealthIndicator(observationService, List.of("tenant-a"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicator.health().getDetails())
                .containsEntry("state", "DOWN")
                .containsEntry("downTenants", List.of("tenant-a"));
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
                Map.of("tenant", tenantId)
        );
    }
}
