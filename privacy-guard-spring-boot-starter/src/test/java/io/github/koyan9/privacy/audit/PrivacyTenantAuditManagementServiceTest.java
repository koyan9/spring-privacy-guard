/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyTenantAuditManagementServiceTest {

    @Test
    void delegatesAuditAndDeadLetterFlowsThroughTenantScope() {
        InMemoryPrivacyAuditRepository auditRepository = new InMemoryPrivacyAuditRepository();
        auditRepository.save(new PrivacyAuditEvent(
                Instant.parse("2026-03-18T00:00:00Z"),
                "READ",
                "Patient",
                "tenant-a-audit",
                "actor",
                "OK",
                Map.of("tenant", "tenant-a")
        ));
        auditRepository.save(new PrivacyAuditEvent(
                Instant.parse("2026-03-18T00:00:01Z"),
                "READ",
                "Patient",
                "tenant-b-audit",
                "actor",
                "OK",
                Map.of("tenant", "tenant-b")
        ));

        InMemoryPrivacyAuditDeadLetterRepository deadLetterRepository = new InMemoryPrivacyAuditDeadLetterRepository();
        deadLetterRepository.save(entry("tenant-a-dead-letter", "tenant-a"));
        deadLetterRepository.save(entry("tenant-b-dead-letter", "tenant-b"));

        PrivacyAuditDeadLetterService deadLetterService = new PrivacyAuditDeadLetterService(deadLetterRepository, event -> {
        });
        PrivacyTenantAuditPolicyResolver tenantPolicyResolver =
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant");
        PrivacyTenantAuditManagementService service = new PrivacyTenantAuditManagementService(
                new PrivacyAuditQueryService(auditRepository),
                new PrivacyAuditStatsService(auditRepository),
                new PrivacyTenantAuditQueryService(new PrivacyAuditQueryService(auditRepository), new PrivacyAuditStatsService(auditRepository), () -> "tenant-a", tenantPolicyResolver),
                deadLetterService,
                new PrivacyAuditDeadLetterStatsService(deadLetterRepository),
                new PrivacyTenantAuditDeadLetterQueryService(deadLetterService, new PrivacyAuditDeadLetterStatsService(deadLetterRepository), () -> "tenant-a", tenantPolicyResolver),
                new PrivacyTenantAuditDeadLetterOperationsService(
                        deadLetterService,
                        new PrivacyTenantAuditDeadLetterQueryService(deadLetterService, new PrivacyAuditDeadLetterStatsService(deadLetterRepository), () -> "tenant-a", tenantPolicyResolver),
                        () -> "tenant-a"
                ),
                new PrivacyAuditDeadLetterExchangeService(deadLetterRepository, new ObjectMapper().findAndRegisterModules(), new PrivacyAuditDeadLetterCsvCodec(new ObjectMapper().findAndRegisterModules())),
                new PrivacyTenantAuditDeadLetterExchangeService(
                        new PrivacyAuditDeadLetterExchangeService(deadLetterRepository, new ObjectMapper().findAndRegisterModules(), new PrivacyAuditDeadLetterCsvCodec(new ObjectMapper().findAndRegisterModules())),
                        new PrivacyTenantAuditDeadLetterQueryService(deadLetterService, new PrivacyAuditDeadLetterStatsService(deadLetterRepository), () -> "tenant-a", tenantPolicyResolver),
                        tenantPolicyResolver,
                        new PrivacyAuditDeadLetterCsvCodec(new ObjectMapper().findAndRegisterModules()),
                        new ObjectMapper().findAndRegisterModules()
                ),
                () -> "tenant-a"
        );

        assertThat(service.findAuditEvents("tenant-a", PrivacyAuditQueryCriteria.recent(10)))
                .extracting(PrivacyAuditEvent::resourceId)
                .containsExactly("tenant-a-audit");
        assertThat(service.findDeadLetters("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10)))
                .extracting(PrivacyAuditDeadLetterEntry::resourceId)
                .containsExactly("tenant-a-dead-letter");
        assertThat(service.deleteDeadLetters("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10))).isEqualTo(1);
        assertThat(service.findDeadLetters("tenant-b", PrivacyAuditDeadLetterQueryCriteria.recent(10)))
                .extracting(PrivacyAuditDeadLetterEntry::resourceId)
                .containsExactly("tenant-b-dead-letter");
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
