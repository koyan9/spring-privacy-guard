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

public class PrivacyTenantAuditDeadLetterExchangeService {

    private final PrivacyAuditDeadLetterExchangeService exchangeService;
    private final PrivacyTenantAuditDeadLetterQueryService tenantQueryService;
    private final PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver;
    private final PrivacyAuditDeadLetterCsvCodec csvCodec;
    private final ObjectMapper objectMapper;

    public PrivacyTenantAuditDeadLetterExchangeService(
            PrivacyAuditDeadLetterExchangeService exchangeService,
            PrivacyTenantAuditDeadLetterQueryService tenantQueryService,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyAuditDeadLetterCsvCodec csvCodec,
            ObjectMapper objectMapper
    ) {
        this.exchangeService = exchangeService;
        this.tenantQueryService = tenantQueryService;
        this.tenantAuditPolicyResolver = tenantAuditPolicyResolver == null
                ? PrivacyTenantAuditPolicyResolver.noop()
                : tenantAuditPolicyResolver;
        this.csvCodec = csvCodec;
        this.objectMapper = objectMapper;
    }

    public String exportJson(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return exchangeService.exportJson(criteria);
        }
        try {
            return objectMapper.writeValueAsString(tenantQueryService.findByCriteria(normalizedTenant, criteria));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export tenant-scoped privacy audit dead letters as JSON", exception);
        }
    }

    public String exportCsv(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return exchangeService.exportCsv(criteria);
        }
        return csvCodec.exportEntries(tenantQueryService.findByCriteria(normalizedTenant, criteria));
    }

    public PrivacyAuditDeadLetterExportManifest exportManifest(
            String tenantId,
            PrivacyAuditDeadLetterQueryCriteria criteria,
            String format
    ) {
        String normalizedFormat = normalizeFormat(format);
        String content = switch (normalizedFormat) {
            case "json" -> exportJson(tenantId, criteria);
            case "csv" -> exportCsv(tenantId, criteria);
            default -> throw new IllegalArgumentException("Unsupported dead-letter export format: " + format);
        };
        List<PrivacyAuditDeadLetterEntry> entries = filteredEntries(tenantId, criteria);
        return new PrivacyAuditDeadLetterExportManifest(
                normalizedFormat,
                entries.size(),
                Instant.now(),
                sha256(content)
        );
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
        return importScopedEntries(scopedEntries, deduplicate, checksum);
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
        return importScopedEntries(scopedEntries, deduplicate, checksum);
    }

    private List<PrivacyAuditDeadLetterEntry> filteredEntries(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return exchangeService == null ? List.of() : tenantQueryService.findByCriteria(null, criteria);
        }
        return tenantQueryService.findByCriteria(normalizedTenant, criteria);
    }

    private PrivacyAuditDeadLetterImportResult importScopedEntries(
            List<PrivacyAuditDeadLetterEntry> scopedEntries,
            boolean deduplicate,
            String checksum
    ) {
        try {
            String scopedJson = objectMapper.writeValueAsString(scopedEntries);
            PrivacyAuditDeadLetterImportResult imported = exchangeService.importJson(scopedJson, deduplicate, null);
            return new PrivacyAuditDeadLetterImportResult(
                    imported.received(),
                    imported.imported(),
                    imported.skippedDuplicates(),
                    checksum
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to import tenant-scoped privacy audit dead letters", exception);
        }
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
}
