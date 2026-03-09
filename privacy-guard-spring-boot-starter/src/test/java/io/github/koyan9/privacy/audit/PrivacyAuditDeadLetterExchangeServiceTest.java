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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrivacyAuditDeadLetterExchangeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
    private final PrivacyAuditDeadLetterCsvCodec csvCodec = new PrivacyAuditDeadLetterCsvCodec(objectMapper);
    private final PrivacyAuditDeadLetterExchangeService service = new PrivacyAuditDeadLetterExchangeService(repository, objectMapper, csvCodec);

    @Test
    void exportsDeadLettersAsJson() {
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "TypeA", "failure", Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of("phone", "138****8000")));

        String json = service.exportJson(PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertThat(json).contains("demo").contains("TypeA");
    }

    @Test
    void exportsManifestWithChecksum() {
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "TypeA", "failure", Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of()));

        PrivacyAuditDeadLetterExportManifest manifest = service.exportManifest(PrivacyAuditDeadLetterQueryCriteria.recent(10), "json");

        assertEquals("json", manifest.format());
        assertEquals(1, manifest.total());
        assertThat(manifest.sha256()).hasSize(64);
    }

    @Test
    void importsDeadLettersFromJsonAndReassignsIds() {
        PrivacyAuditDeadLetterImportResult imported = service.importJson("""
                [
                  {
                    "id": 99,
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
                """, true, null);

        assertEquals(1, imported.imported());
        assertEquals(0, imported.skippedDuplicates());
        assertThat(repository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.id()).isNotEqualTo(99L);
            assertThat(entry.resourceId()).isEqualTo("demo");
        });
    }

    @Test
    void skipsDuplicateImportsWhenEnabled() {
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T00:00:00Z"), 3, "TypeA", "failure", Instant.parse("2026-03-05T23:00:00Z"), "READ", "Patient", "demo", "actor", "OK", Map.of("phone", "138****8000")));
        String json = service.exportJson(PrivacyAuditDeadLetterQueryCriteria.recent(10));

        PrivacyAuditDeadLetterImportResult result = service.importJson(json, true, null);

        assertEquals(0, result.imported());
        assertEquals(1, result.skippedDuplicates());
    }

    @Test
    void rejectsImportWhenChecksumMismatches() {
        assertThrows(IllegalArgumentException.class, () -> service.importJson("[]", true, "deadbeef"));
    }

    @Test
    void exportsDeadLettersAsCsv() {
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T00:00:00Z"), 3, "TypeA", "failure,with,comma", Instant.parse("2026-03-05T23:00:00Z"), "READ", "Patient", "demo", "actor", "OK", Map.of("phone", "138****8000")));

        String csv = service.exportCsv(PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertThat(csv).contains("failed_at").contains("failure,with,comma").contains("demo");
    }

    @Test
    void importsDeadLettersFromCsv() {
        repository.save(new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T00:00:00Z"), 3, "TypeA", "failure", Instant.parse("2026-03-05T23:00:00Z"), "READ", "Patient", "demo", "actor", "OK", Map.of("phone", "138****8000")));
        String csv = service.exportCsv(PrivacyAuditDeadLetterQueryCriteria.recent(10));
        repository.clear();

        PrivacyAuditDeadLetterImportResult imported = service.importCsv(csv, true, null);

        assertEquals(1, imported.imported());
        assertThat(repository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.resourceId()).isEqualTo("demo");
            assertThat(entry.details().get("phone")).isEqualTo("138****8000");
        });
    }
}
