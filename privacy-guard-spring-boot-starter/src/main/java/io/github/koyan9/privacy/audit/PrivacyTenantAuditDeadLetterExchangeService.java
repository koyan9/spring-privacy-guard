/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class PrivacyTenantAuditDeadLetterExchangeService {

    private static final int DEFAULT_EXPORT_PAGE_SIZE = 500;

    private final PrivacyAuditDeadLetterExchangeService exchangeService;
    private final PrivacyAuditDeadLetterRepository deadLetterRepository;
    private final PrivacyTenantAuditDeadLetterQueryService tenantQueryService;
    private final PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver;
    private final PrivacyTenantAuditDeadLetterReadRepository tenantReadRepository;
    private final PrivacyTenantAuditDeadLetterWriteRepository tenantWriteRepository;
    private final PrivacyAuditDeadLetterCsvCodec csvCodec;
    private final ObjectMapper objectMapper;
    private final Supplier<PrivacyTenantAuditTelemetry> telemetrySupplier;

    public PrivacyTenantAuditDeadLetterExchangeService(
            PrivacyAuditDeadLetterExchangeService exchangeService,
            PrivacyTenantAuditDeadLetterQueryService tenantQueryService,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyAuditDeadLetterCsvCodec csvCodec,
            ObjectMapper objectMapper
    ) {
        this(
                exchangeService,
                null,
                tenantQueryService,
                tenantAuditPolicyResolver,
                null,
                null,
                csvCodec,
                objectMapper,
                (Supplier<PrivacyTenantAuditTelemetry>) null
        );
    }

    public PrivacyTenantAuditDeadLetterExchangeService(
            PrivacyAuditDeadLetterExchangeService exchangeService,
            PrivacyAuditDeadLetterRepository deadLetterRepository,
            PrivacyTenantAuditDeadLetterQueryService tenantQueryService,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditDeadLetterReadRepository tenantReadRepository,
            PrivacyTenantAuditDeadLetterWriteRepository tenantWriteRepository,
            PrivacyAuditDeadLetterCsvCodec csvCodec,
            ObjectMapper objectMapper,
            PrivacyTenantAuditTelemetry telemetry
    ) {
        this(
                exchangeService,
                deadLetterRepository,
                tenantQueryService,
                tenantAuditPolicyResolver,
                tenantReadRepository,
                tenantWriteRepository,
                csvCodec,
                objectMapper,
                () -> telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry
        );
    }

    public PrivacyTenantAuditDeadLetterExchangeService(
            PrivacyAuditDeadLetterExchangeService exchangeService,
            PrivacyAuditDeadLetterRepository deadLetterRepository,
            PrivacyTenantAuditDeadLetterQueryService tenantQueryService,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditDeadLetterReadRepository tenantReadRepository,
            PrivacyTenantAuditDeadLetterWriteRepository tenantWriteRepository,
            PrivacyAuditDeadLetterCsvCodec csvCodec,
            ObjectMapper objectMapper,
            Supplier<PrivacyTenantAuditTelemetry> telemetrySupplier
    ) {
        this.exchangeService = exchangeService;
        this.deadLetterRepository = deadLetterRepository;
        this.tenantQueryService = tenantQueryService;
        this.tenantAuditPolicyResolver = tenantAuditPolicyResolver == null
                ? PrivacyTenantAuditPolicyResolver.noop()
                : tenantAuditPolicyResolver;
        this.tenantReadRepository = tenantReadRepository;
        this.tenantWriteRepository = tenantWriteRepository;
        this.csvCodec = csvCodec;
        this.objectMapper = objectMapper;
        this.telemetrySupplier = telemetrySupplier == null
                ? PrivacyTenantAuditTelemetry::noop
                : telemetrySupplier;
    }

    public String exportJson(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        return exportJson(tenantId, criteria, DEFAULT_EXPORT_PAGE_SIZE);
    }

    String exportJson(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria, int pageSize) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return exchangeService.exportJson(criteria, pageSize);
        }
        telemetry().recordQueryReadPath("dead_letter_export", readPathKind());
        return tenantScopedReadExchangeService(normalizedTenant).exportJson(criteria, pageSize);
    }

    public String exportCsv(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        return exportCsv(tenantId, criteria, DEFAULT_EXPORT_PAGE_SIZE);
    }

    String exportCsv(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria, int pageSize) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return exchangeService.exportCsv(criteria, pageSize);
        }
        telemetry().recordQueryReadPath("dead_letter_export", readPathKind());
        return tenantScopedReadExchangeService(normalizedTenant).exportCsv(criteria, pageSize);
    }

    public PrivacyAuditDeadLetterExportManifest exportManifest(
            String tenantId,
            PrivacyAuditDeadLetterQueryCriteria criteria,
            String format
    ) {
        return exportManifest(tenantId, criteria, format, DEFAULT_EXPORT_PAGE_SIZE);
    }

    PrivacyAuditDeadLetterExportManifest exportManifest(
            String tenantId,
            PrivacyAuditDeadLetterQueryCriteria criteria,
            String format,
            int pageSize
    ) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return exchangeService.exportManifest(criteria, format, pageSize);
        }
        telemetry().recordQueryReadPath("dead_letter_manifest", readPathKind());
        return tenantScopedReadExchangeService(normalizedTenant).exportManifest(criteria, format, pageSize);
    }

    public PrivacyAuditDeadLetterImportResult importJson(
            String tenantId,
            String content,
            boolean deduplicate,
            String expectedChecksum
    ) {
        String resolvedContent = content == null ? "" : content;
        String checksum = sha256(resolvedContent);
        verifyChecksum(checksum, expectedChecksum);
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return exchangeService.importJson(resolvedContent, deduplicate, expectedChecksum);
        }
        List<PrivacyAuditDeadLetterEntry> scopedEntries = scopeEntries(parseJsonEntries(resolvedContent), normalizedTenant);
        return importScopedEntries(scopedEntries, normalizedTenant, deduplicate, checksum);
    }

    public PrivacyAuditDeadLetterImportResult importCsv(
            String tenantId,
            String content,
            boolean deduplicate,
            String expectedChecksum
    ) {
        String resolvedContent = content == null ? "" : content;
        String checksum = sha256(resolvedContent);
        verifyChecksum(checksum, expectedChecksum);
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return exchangeService.importCsv(resolvedContent, deduplicate, expectedChecksum);
        }
        List<PrivacyAuditDeadLetterEntry> scopedEntries = scopeEntries(csvCodec.importEntries(resolvedContent), normalizedTenant);
        return importScopedEntries(scopedEntries, normalizedTenant, deduplicate, checksum);
    }

    private PrivacyAuditDeadLetterImportResult importScopedEntries(
            List<PrivacyAuditDeadLetterEntry> scopedEntries,
            String tenantId,
            boolean deduplicate,
            String checksum
    ) {
        if (tenantWriteRepository != null && tenantWriteRepository.supportsTenantImport()) {
            telemetry().recordWritePath("dead_letter_import", "native");
            return exchangeService.importEntries(
                    scopedEntries,
                    deduplicate,
                    checksum,
                    entries -> tenantWriteRepository.saveAllTenantAware(toWriteRequests(entries, tenantId))
            );
        }
        telemetry().recordWritePath("dead_letter_import", "fallback");
        return exchangeService.importEntries(scopedEntries, deduplicate, checksum);
    }

    private List<PrivacyAuditDeadLetterEntry> parseJsonEntries(String content) {
        try (JsonParser parser = objectMapper.getFactory().createParser(new StringReader(content))) {
            parser.setCodec(objectMapper);
            JsonToken token = parser.nextToken();
            if (token == null) {
                return List.of();
            }
            List<PrivacyAuditDeadLetterEntry> entries = new ArrayList<>();
            if (token == JsonToken.START_ARRAY) {
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    entries.add(parser.readValueAs(PrivacyAuditDeadLetterEntry.class));
                }
                return List.copyOf(entries);
            }
            return List.of(parser.readValueAs(PrivacyAuditDeadLetterEntry.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse tenant-scoped privacy audit dead letter JSON", exception);
        }
    }

    private List<PrivacyAuditDeadLetterEntry> scopeEntries(List<PrivacyAuditDeadLetterEntry> entries, String tenantId) {
        String detailKey = tenantDetailKey(tenantId);
        List<PrivacyAuditDeadLetterEntry> scoped = new ArrayList<>(entries.size());
        for (PrivacyAuditDeadLetterEntry entry : entries) {
            Map<String, String> details = new LinkedHashMap<>(entry.details());
            details.put(detailKey, tenantId);
            scoped.add(new PrivacyAuditDeadLetterEntry(
                    entry.id(),
                    entry.failedAt(),
                    entry.attempts(),
                    entry.errorType(),
                    entry.errorMessage(),
                    entry.occurredAt(),
                    entry.action(),
                    entry.resourceType(),
                    entry.resourceId(),
                    entry.actor(),
                    entry.outcome(),
                    details
            ));
        }
        return List.copyOf(scoped);
    }

    private List<PrivacyTenantAuditDeadLetterWriteRequest> toWriteRequests(
            List<PrivacyAuditDeadLetterEntry> entries,
            String tenantId
    ) {
        String detailKey = tenantDetailKey(tenantId);
        return entries.stream()
                .map(entry -> new PrivacyTenantAuditDeadLetterWriteRequest(entry, tenantId, detailKey))
                .toList();
    }

    private PrivacyAuditDeadLetterExchangeService tenantScopedReadExchangeService(String tenantId) {
        String detailKey = tenantDetailKey(tenantId);
        PrivacyAuditDeadLetterRepository tenantScopedRepository = new PrivacyAuditDeadLetterRepository() {
            @Override
            public void save(PrivacyAuditDeadLetterEntry entry) {
                throw new UnsupportedOperationException("Tenant-scoped read repository does not support writes");
            }

            @Override
            public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
                if (tenantReadRepository != null && tenantReadRepository.supportsTenantExchangeRead()) {
                    return tenantReadRepository.findByCriteria(tenantId, detailKey, criteria);
                }
                return tenantQueryService.findByCriteria(tenantId, criteria);
            }
        };
        // Reuse the shared paged export/manifest implementation with a tenant-scoped read adapter.
        return new PrivacyAuditDeadLetterExchangeService(tenantScopedRepository, objectMapper, csvCodec);
    }

    private String tenantDetailKey(String tenantId) {
        PrivacyTenantAuditPolicy policy = tenantAuditPolicyResolver.resolve(tenantId);
        if (policy == null) {
            return "tenantId";
        }
        return policy.tenantDetailKey();
    }

    private String normalizeTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantId.trim();
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "json";
        }
        return format.trim().toLowerCase();
    }

    private void verifyChecksum(String actual, String expectedChecksum) {
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            return;
        }
        if (!actual.equalsIgnoreCase(expectedChecksum.trim())) {
            throw new IllegalArgumentException("Dead-letter import checksum mismatch");
        }
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private String readPathKind() {
        return tenantReadRepository != null && tenantReadRepository.supportsTenantExchangeRead() ? "native" : "fallback";
    }

    private PrivacyTenantAuditTelemetry telemetry() {
        PrivacyTenantAuditTelemetry telemetry = telemetrySupplier.get();
        return telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry;
    }
}
