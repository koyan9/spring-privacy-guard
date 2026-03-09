/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class PrivacyAuditDeadLetterExchangeService {

    private static final TypeReference<List<PrivacyAuditDeadLetterEntry>> ENTRY_LIST_TYPE = new TypeReference<>() {
    };

    private final PrivacyAuditDeadLetterRepository deadLetterRepository;
    private final ObjectMapper objectMapper;
    private final PrivacyAuditDeadLetterCsvCodec csvCodec;

    public PrivacyAuditDeadLetterExchangeService(
            PrivacyAuditDeadLetterRepository deadLetterRepository,
            ObjectMapper objectMapper,
            PrivacyAuditDeadLetterCsvCodec csvCodec
    ) {
        this.deadLetterRepository = Objects.requireNonNull(deadLetterRepository, "deadLetterRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.csvCodec = Objects.requireNonNull(csvCodec, "csvCodec must not be null");
    }

    public String exportJson(PrivacyAuditDeadLetterQueryCriteria criteria) {
        try {
            return objectMapper.writeValueAsString(deadLetterRepository.findByCriteria(normalize(criteria)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to export privacy audit dead letters as JSON", exception);
        }
    }

    public String exportCsv(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return csvCodec.exportEntries(deadLetterRepository.findByCriteria(normalize(criteria)));
    }

    public PrivacyAuditDeadLetterExportManifest exportManifest(PrivacyAuditDeadLetterQueryCriteria criteria, String format) {
        String normalizedFormat = normalizeFormat(format);
        String content = switch (normalizedFormat) {
            case "json" -> exportJson(criteria);
            case "csv" -> exportCsv(criteria);
            default -> throw new IllegalArgumentException("Unsupported dead-letter export format: " + format);
        };
        int total = deadLetterRepository.findByCriteria(normalize(criteria)).size();
        return new PrivacyAuditDeadLetterExportManifest(normalizedFormat, total, Instant.now(), sha256(content));
    }

    public PrivacyAuditDeadLetterImportResult importJson(String content, boolean deduplicate, String expectedChecksum) {
        verifyChecksum(content, expectedChecksum);
        try {
            List<PrivacyAuditDeadLetterEntry> entries;
            try {
                entries = objectMapper.readValue(content, ENTRY_LIST_TYPE);
            } catch (JsonProcessingException exception) {
                entries = List.of(objectMapper.readValue(content, PrivacyAuditDeadLetterEntry.class));
            }
            return importEntries(entries, deduplicate, content);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to import privacy audit dead letters from JSON", exception);
        }
    }

    public PrivacyAuditDeadLetterImportResult importCsv(String content, boolean deduplicate, String expectedChecksum) {
        verifyChecksum(content, expectedChecksum);
        List<PrivacyAuditDeadLetterEntry> entries = csvCodec.importEntries(content);
        return importEntries(entries, deduplicate, content);
    }

    private PrivacyAuditDeadLetterImportResult importEntries(List<PrivacyAuditDeadLetterEntry> entries, boolean deduplicate, String content) {
        List<PrivacyAuditDeadLetterEntry> normalizedEntries = entries.stream().map(this::normalizeImportedEntry).toList();
        Map<String, PrivacyAuditDeadLetterEntry> existingFingerprints = new LinkedHashMap<>();
        if (deduplicate) {
            for (PrivacyAuditDeadLetterEntry existing : deadLetterRepository.findAll()) {
                existingFingerprints.put(fingerprint(existing), existing);
            }
        }

        int skipped = 0;
        java.util.ArrayList<PrivacyAuditDeadLetterEntry> accepted = new java.util.ArrayList<>();
        for (PrivacyAuditDeadLetterEntry entry : normalizedEntries) {
            String fingerprint = fingerprint(entry);
            if (deduplicate && existingFingerprints.containsKey(fingerprint)) {
                skipped++;
                continue;
            }
            accepted.add(entry);
            existingFingerprints.put(fingerprint, entry);
        }
        deadLetterRepository.saveAll(accepted);
        return new PrivacyAuditDeadLetterImportResult(normalizedEntries.size(), accepted.size(), skipped, sha256(content));
    }

    private PrivacyAuditDeadLetterQueryCriteria normalize(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return criteria == null ? PrivacyAuditDeadLetterQueryCriteria.recent(100) : criteria.normalize();
    }

    private PrivacyAuditDeadLetterEntry normalizeImportedEntry(PrivacyAuditDeadLetterEntry entry) {
        return new PrivacyAuditDeadLetterEntry(
                null,
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
                entry.details()
        );
    }

    private void verifyChecksum(String content, String expectedChecksum) {
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            return;
        }
        String actual = sha256(content);
        if (!actual.equalsIgnoreCase(expectedChecksum.trim())) {
            throw new IllegalArgumentException("Dead-letter import checksum mismatch");
        }
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "json";
        }
        return format.trim().toLowerCase();
    }

    private String fingerprint(PrivacyAuditDeadLetterEntry entry) {
        Map<String, String> sortedDetails = new TreeMap<>(entry.details());
        String canonical = String.join("|",
                safe(entry.failedAt()),
                String.valueOf(entry.attempts()),
                safe(entry.errorType()),
                safe(entry.errorMessage()),
                safe(entry.occurredAt()),
                safe(entry.action()),
                safe(entry.resourceType()),
                safe(entry.resourceId()),
                safe(entry.actor()),
                safe(entry.outcome()),
                sortedDetails.toString()
        );
        return sha256(canonical);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }
}
