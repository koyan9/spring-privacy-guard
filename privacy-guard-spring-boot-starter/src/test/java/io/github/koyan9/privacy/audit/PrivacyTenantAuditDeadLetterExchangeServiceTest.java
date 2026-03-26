/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.core.PrivacyTenantProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    void recordsExportAndManifestReadPathTelemetry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PrivacyTenantAuditDeadLetterExchangeService exchangeWithTelemetry =
                new PrivacyTenantAuditDeadLetterExchangeService(
                        exchangeService,
                        repository,
                        queryService,
                        tenantAuditPolicyResolver,
                        repository,
                        repository,
                        csvCodec,
                        objectMapper,
                        new MicrometerPrivacyTenantAuditTelemetry(registry)
                );
        repository.save(entry("tenant-a-1", "tenant-a"));
        repository.save(entry("tenant-b-1", "tenant-b"));

        exchangeWithTelemetry.exportJson("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10));
        exchangeWithTelemetry.exportManifest("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10), "json");

        assertThat(registry.get("privacy.audit.tenant.read.path")
                .tag("domain", "dead_letter_export")
                .tag("path", "native")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.tenant.read.path")
                .tag("domain", "dead_letter_manifest")
                .tag("path", "native")
                .counter()
                .count()).isEqualTo(1.0d);
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

    @Test
    void usesTenantAwareBulkWriteForTenantImportsWhenRepositorySupportsIt() {
        CapturingTenantWriteRepository capturingRepository = new CapturingTenantWriteRepository();
        PrivacyAuditDeadLetterExchangeService capturingExchangeService = new PrivacyAuditDeadLetterExchangeService(
                capturingRepository,
                objectMapper,
                csvCodec
        );
        PrivacyTenantAuditDeadLetterQueryService capturingQueryService = new PrivacyTenantAuditDeadLetterQueryService(
                new PrivacyAuditDeadLetterService(capturingRepository, event -> {
                }),
                new PrivacyAuditDeadLetterStatsService(capturingRepository),
                tenantProvider,
                tenantAuditPolicyResolver
        );
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PrivacyTenantAuditDeadLetterExchangeService exchangeService = new PrivacyTenantAuditDeadLetterExchangeService(
                capturingExchangeService,
                capturingRepository,
                capturingQueryService,
                tenantAuditPolicyResolver,
                capturingRepository,
                capturingRepository,
                csvCodec,
                objectMapper,
                new MicrometerPrivacyTenantAuditTelemetry(registry)
        );

        PrivacyAuditDeadLetterImportResult imported = exchangeService.importJson(
                "tenant-b",
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
                    "resourceId": "native-import",
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
        assertThat(capturingRepository.tenantAwareSaveAllRequests).hasSize(1);
        assertThat(capturingRepository.genericSaveAllEntries).isEmpty();
        assertThat(capturingRepository.tenantAwareSaveAllRequests.get(0)).singleElement().satisfies(request -> {
            assertThat(request.tenantId()).isEqualTo("tenant-b");
            assertThat(request.tenantDetailKey()).isEqualTo("tenant");
            assertThat(request.entry().details()).containsEntry("tenant", "tenant-b");
        });
        assertThat(registry.get("privacy.audit.tenant.write.path")
                .tag("domain", "dead_letter_import")
                .tag("path", "native")
                .counter()
                .count()).isEqualTo(1.0d);
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

    private static final class CapturingTenantWriteRepository extends InMemoryPrivacyAuditDeadLetterRepository {

        private final List<List<PrivacyTenantAuditDeadLetterWriteRequest>> tenantAwareSaveAllRequests = new ArrayList<>();
        private final List<PrivacyAuditDeadLetterEntry> genericSaveAllEntries = new ArrayList<>();

        @Override
        public void saveAll(List<PrivacyAuditDeadLetterEntry> entries) {
            if (entries != null) {
                genericSaveAllEntries.addAll(entries);
            }
            super.saveAll(entries);
        }

        @Override
        public void saveAllTenantAware(List<PrivacyTenantAuditDeadLetterWriteRequest> requests) {
            tenantAwareSaveAllRequests.add(requests == null ? List.of() : List.copyOf(requests));
            super.saveAllTenantAware(requests);
        }
    }
}
