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
import java.util.stream.IntStream;

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
    void exportsTenantJsonThroughPagedNativeReadRepositoryWithoutUsingQueryService() throws Exception {
        RecordingTenantReadRepository nativeReadRepository = new RecordingTenantReadRepository(tenantEntries(501));
        PrivacyTenantAuditDeadLetterExchangeService nativeExchangeService =
                new PrivacyTenantAuditDeadLetterExchangeService(
                        exchangeService,
                        null,
                        throwingTenantQueryService(),
                        tenantAuditPolicyResolver,
                        nativeReadRepository,
                        null,
                        csvCodec,
                        objectMapper,
                        (PrivacyTenantAuditTelemetry) null
                );

        String json = nativeExchangeService.exportJson("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(501));

        assertThat(objectMapper.readTree(json).size()).isEqualTo(501);
        assertThat(nativeReadRepository.requestedOffsets).containsExactly(0, 500);
        assertThat(nativeReadRepository.requestedLimits).containsExactly(500, 1);
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
    void exportsTenantManifestThroughPagedNativeReadRepositoryWithoutUsingQueryService() {
        RecordingTenantReadRepository nativeReadRepository = new RecordingTenantReadRepository(tenantEntries(501));
        PrivacyTenantAuditDeadLetterExchangeService nativeExchangeService =
                new PrivacyTenantAuditDeadLetterExchangeService(
                        exchangeService,
                        null,
                        throwingTenantQueryService(),
                        tenantAuditPolicyResolver,
                        nativeReadRepository,
                        null,
                        csvCodec,
                        objectMapper,
                        (PrivacyTenantAuditTelemetry) null
                );

        PrivacyAuditDeadLetterExportManifest manifest = nativeExchangeService.exportManifest(
                "tenant-a",
                PrivacyAuditDeadLetterQueryCriteria.recent(501),
                "json"
        );

        assertEquals("json", manifest.format());
        assertEquals(501, manifest.total());
        assertThat(manifest.sha256()).hasSize(64);
        assertThat(nativeReadRepository.requestedOffsets).containsExactly(0, 500);
        assertThat(nativeReadRepository.requestedLimits).containsExactly(500, 1);
    }

    @Test
    void exportsTenantJsonThroughPagedFallbackQueryService() throws Exception {
        RecordingFallbackTenantQueryService fallbackQueryService = new RecordingFallbackTenantQueryService(tenantEntries(5));
        PrivacyTenantAuditDeadLetterExchangeService fallbackExchangeService =
                new PrivacyTenantAuditDeadLetterExchangeService(
                        exchangeService,
                        null,
                        fallbackQueryService,
                        tenantAuditPolicyResolver,
                        null,
                        null,
                        csvCodec,
                        objectMapper,
                        (PrivacyTenantAuditTelemetry) null
                );

        String json = fallbackExchangeService.exportJson("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(5), 2);

        assertThat(objectMapper.readTree(json).size()).isEqualTo(5);
        assertThat(fallbackQueryService.requestedOffsets).containsExactly(0, 2, 4);
        assertThat(fallbackQueryService.requestedLimits).containsExactly(2, 2, 1);
    }

    @Test
    void fallsBackToTenantQueryServiceWhenReadRepositoryDoesNotDeclareExchangeCapability() throws Exception {
        RecordingFallbackTenantQueryService fallbackQueryService = new RecordingFallbackTenantQueryService(tenantEntries(5));
        PrivacyTenantAuditDeadLetterReadRepository capabilityFalseReadRepository = new PrivacyTenantAuditDeadLetterReadRepository() {
            @Override
            public List<PrivacyAuditDeadLetterEntry> findByCriteria(
                    String tenantId,
                    String tenantDetailKey,
                    PrivacyAuditDeadLetterQueryCriteria criteria
            ) {
                throw new AssertionError("exchange read should fall back to query service when capability is false");
            }

            @Override
            public PrivacyAuditDeadLetterStats computeStats(
                    String tenantId,
                    String tenantDetailKey,
                    PrivacyAuditDeadLetterQueryCriteria criteria
            ) {
                throw new AssertionError("exchange manifest should fall back to query service when capability is false");
            }
        };
        PrivacyTenantAuditDeadLetterExchangeService fallbackExchangeService =
                new PrivacyTenantAuditDeadLetterExchangeService(
                        exchangeService,
                        null,
                        fallbackQueryService,
                        tenantAuditPolicyResolver,
                        capabilityFalseReadRepository,
                        null,
                        csvCodec,
                        objectMapper,
                        (PrivacyTenantAuditTelemetry) null
                );

        String json = fallbackExchangeService.exportJson("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(5), 2);

        assertThat(objectMapper.readTree(json).size()).isEqualTo(5);
        assertThat(fallbackQueryService.requestedOffsets).containsExactly(0, 2, 4);
        assertThat(fallbackQueryService.requestedLimits).containsExactly(2, 2, 1);
    }

    @Test
    void exportsTenantManifestThroughPagedFallbackQueryService() {
        RecordingFallbackTenantQueryService fallbackQueryService = new RecordingFallbackTenantQueryService(tenantEntries(5));
        PrivacyTenantAuditDeadLetterExchangeService fallbackExchangeService =
                new PrivacyTenantAuditDeadLetterExchangeService(
                        exchangeService,
                        null,
                        fallbackQueryService,
                        tenantAuditPolicyResolver,
                        null,
                        null,
                        csvCodec,
                        objectMapper,
                        (PrivacyTenantAuditTelemetry) null
                );

        PrivacyAuditDeadLetterExportManifest manifest = fallbackExchangeService.exportManifest(
                "tenant-a",
                PrivacyAuditDeadLetterQueryCriteria.recent(5),
                "json",
                2
        );

        assertEquals("json", manifest.format());
        assertEquals(5, manifest.total());
        assertThat(manifest.sha256()).hasSize(64);
        assertThat(fallbackQueryService.requestedOffsets).containsExactly(0, 2, 4);
        assertThat(fallbackQueryService.requestedLimits).containsExactly(2, 2, 1);
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

    @Test
    void fallsBackToGenericImportWhenWriteRepositoryDoesNotDeclareImportCapability() {
        CapturingGenericImportRepository capturingRepository = new CapturingGenericImportRepository();
        PrivacyAuditDeadLetterExchangeService capturingExchangeService = new PrivacyAuditDeadLetterExchangeService(
                capturingRepository,
                objectMapper,
                csvCodec
        );
        PrivacyTenantAuditDeadLetterWriteRepository capabilityFalseWriteRepository = new PrivacyTenantAuditDeadLetterWriteRepository() {
            @Override
            public void save(PrivacyTenantAuditDeadLetterWriteRequest request) {
                throw new AssertionError("tenant import should fall back to generic path when capability is false");
            }
        };
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PrivacyTenantAuditDeadLetterExchangeService exchangeService = new PrivacyTenantAuditDeadLetterExchangeService(
                capturingExchangeService,
                capturingRepository,
                queryService,
                tenantAuditPolicyResolver,
                capturingRepository,
                capabilityFalseWriteRepository,
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
                    "resourceId": "fallback-import",
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
        assertThat(capturingRepository.genericSaveAllEntries).singleElement().satisfies(entry ->
                assertThat(entry.details()).containsEntry("tenant", "tenant-b"));
        assertThat(registry.get("privacy.audit.tenant.write.path")
                .tag("domain", "dead_letter_import")
                .tag("path", "fallback")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    private PrivacyTenantAuditDeadLetterQueryService throwingTenantQueryService() {
        InMemoryPrivacyAuditDeadLetterRepository throwingRepository = new InMemoryPrivacyAuditDeadLetterRepository() {
            @Override
            public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
                throw new AssertionError("tenantQueryService should not be used when a native tenant read repository is available");
            }

            @Override
            public PrivacyAuditDeadLetterStats computeStats(PrivacyAuditDeadLetterQueryCriteria criteria) {
                throw new AssertionError("tenantQueryService stats should not be used when a native tenant read repository is available");
            }
        };
        return new PrivacyTenantAuditDeadLetterQueryService(
                new PrivacyAuditDeadLetterService(throwingRepository, event -> {
                }),
                new PrivacyAuditDeadLetterStatsService(throwingRepository),
                tenantProvider,
                tenantAuditPolicyResolver
        );
    }

    private List<PrivacyAuditDeadLetterEntry> tenantEntries(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> entry("tenant-a-" + index, "tenant-a"))
                .toList();
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

    private static final class RecordingTenantReadRepository implements PrivacyTenantAuditDeadLetterReadRepository {

        private final List<PrivacyAuditDeadLetterEntry> entries;
        private final List<Integer> requestedLimits = new ArrayList<>();
        private final List<Integer> requestedOffsets = new ArrayList<>();

        private RecordingTenantReadRepository(List<PrivacyAuditDeadLetterEntry> entries) {
            this.entries = List.copyOf(entries);
        }

        @Override
        public List<PrivacyAuditDeadLetterEntry> findByCriteria(
                String tenantId,
                String tenantDetailKey,
                PrivacyAuditDeadLetterQueryCriteria criteria
        ) {
            PrivacyAuditDeadLetterQueryCriteria normalized = criteria == null
                    ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                    : criteria.normalize();
            requestedLimits.add(normalized.limit());
            requestedOffsets.add(normalized.offset());
            return entries.stream()
                    .skip(normalized.offset())
                    .limit(normalized.limit())
                    .toList();
        }

        @Override
        public PrivacyAuditDeadLetterStats computeStats(
                String tenantId,
                String tenantDetailKey,
                PrivacyAuditDeadLetterQueryCriteria criteria
        ) {
            return new PrivacyAuditDeadLetterStats(entries.size(), Map.of("READ", (long) entries.size()), Map.of("OK", (long) entries.size()), Map.of("Patient", (long) entries.size()), Map.of("TypeA", (long) entries.size()));
        }

        @Override
        public boolean supportsTenantExchangeRead() {
            return true;
        }
    }

    private final class RecordingFallbackTenantQueryService extends PrivacyTenantAuditDeadLetterQueryService {

        private final List<PrivacyAuditDeadLetterEntry> entries;
        private final List<Integer> requestedLimits = new ArrayList<>();
        private final List<Integer> requestedOffsets = new ArrayList<>();

        private RecordingFallbackTenantQueryService(List<PrivacyAuditDeadLetterEntry> entries) {
            super(
                    new PrivacyAuditDeadLetterService(new InMemoryPrivacyAuditDeadLetterRepository(), event -> {
                    }),
                    new PrivacyAuditDeadLetterStatsService(criteria -> new PrivacyAuditDeadLetterStats(entries.size(), Map.of(), Map.of(), Map.of(), Map.of())),
                    tenantProvider,
                    tenantAuditPolicyResolver
            );
            this.entries = List.copyOf(entries);
        }

        @Override
        public List<PrivacyAuditDeadLetterEntry> findByCriteria(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
            PrivacyAuditDeadLetterQueryCriteria normalized = criteria == null
                    ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                    : criteria.normalize();
            requestedLimits.add(normalized.limit());
            requestedOffsets.add(normalized.offset());
            return entries.stream()
                    .filter(entry -> tenantId.equals(entry.details().get("tenant")))
                    .skip(normalized.offset())
                    .limit(normalized.limit())
                    .toList();
        }
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

    private static final class CapturingGenericImportRepository extends InMemoryPrivacyAuditDeadLetterRepository {

        private final List<PrivacyAuditDeadLetterEntry> genericSaveAllEntries = new ArrayList<>();

        @Override
        public void saveAll(List<PrivacyAuditDeadLetterEntry> entries) {
            if (entries != null) {
                genericSaveAllEntries.addAll(entries);
            }
            super.saveAll(entries);
        }
    }
}
