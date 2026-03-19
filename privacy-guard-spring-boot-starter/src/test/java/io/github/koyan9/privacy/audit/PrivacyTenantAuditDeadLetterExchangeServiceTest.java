/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.core.PrivacyTenantProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PrivacyTenantAuditDeadLetterExchangeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
    private final PrivacyAuditDeadLetterCsvCodec csvCodec = new PrivacyAuditDeadLetterCsvCodec(objectMapper);
    private final PrivacyAuditDeadLetterExchangeService exchangeService = new PrivacyAuditDeadLetterExchangeService(repository, objectMapper, csvCodec);
    private final PrivacyTenantProvider tenantProvider = () -> "tenant-a";
    private final PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver =
            tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant");
    private final PrivacyTenantAuditDeadLetterQueryService queryService = new PrivacyTenantAuditDeadLetterQueryService(
            new PrivacyAuditDeadLetterService(repository, event -> {
            }),
            new PrivacyAuditDeadLetterStatsService(repository),
            tenantProvider,
            tenantAuditPolicyResolver
    );
    private final PrivacyTenantAuditDeadLetterExchangeService tenantExchangeService =
            new PrivacyTenantAuditDeadLetterExchangeService(
                    exchangeService,
                    queryService,
                    tenantAuditPolicyResolver,
                    csvCodec,
                    objectMapper
            );

    @Test
    void exportsDeadLettersAsJsonForTenant() {
        repository.save(entry("tenant-a-1", "tenant-a"));
        repository.save(entry("tenant-b-1", "tenant-b"));

        String json = tenantExchangeService.exportJson("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertThat(json).contains("tenant-a-1");
        assertThat(json).doesNotContain("tenant-b-1");
    }

    @Test
    void exportsManifestForTenantScope() {
        repository.save(entry("tenant-a-1", "tenant-a"));
        repository.save(entry("tenant-b-1", "tenant-b"));

        PrivacyAuditDeadLetterExportManifest manifest = tenantExchangeService.exportManifest(
                "tenant-b",
                PrivacyAuditDeadLetterQueryCriteria.recent(10),
                "json"
        );

        assertEquals("json", manifest.format());
        assertEquals(1, manifest.total());
        assertThat(manifest.sha256()).hasSize(64);
    }

    @Test
    void importsJsonAndScopesEntriesToTenant() {
        PrivacyAuditDeadLetterImportResult imported = tenantExchangeService.importJson(
                "tenant-a",
                """
                [
                  {
                    "failedAt": "2026-03-06T00:00:00Z",
                    "attempts": 3,
                    "errorType": "TypeA",
                    "errorMessage": "failure",
                    "occurredAt": "2026-03-05T23:00:00Z",
                    "action": "READ",
                    "resourceType": "Patient",
                    "resourceId": "demo",
                    "actor": "actor",
                    "outcome": "OK",
                    "details": {
                      "phone": "138****8000"
                    }
                  }
                ]
                """,
                true,
                null
        );

        assertEquals(1, imported.imported());
        assertThat(repository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.resourceId()).isEqualTo("demo");
            assertThat(entry.details()).containsEntry("tenant", "tenant-a");
        });
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
