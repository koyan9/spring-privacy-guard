/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
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

    private PrivacyAuditDeadLetterEntry entry(String resourceId, String tenant) {
        return new PrivacyAuditDeadLetterEntry(
                null,
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
