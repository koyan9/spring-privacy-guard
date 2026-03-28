/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterAlertEvent;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterBacklogState;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("sample")
@Tag("sample-default")
@Tag("sample-dead-letter")
class PrivacyDemoDefaultDeadLetterHttpTest extends SampleReceiverRouteTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryPrivacyAuditDeadLetterRepository deadLetterRepository;

    @Autowired
    private InMemoryPrivacyAuditRepository auditRepository;

    @Autowired
    private DemoDeadLetterAlertCallback demoDeadLetterAlertCallback;

    @BeforeEach
    void resetState() throws Exception {
        deadLetterRepository.clear();
        auditRepository.clear();
        Thread.sleep(120L);
        demoDeadLetterAlertCallback.reset();
    }

    @Test
    void exposesDeadLetterHealthMetricsAndAlertCallbackViaActuator() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "failure",
                Instant.parse("2026-03-08T00:00:00Z"),
                "READ",
                "Patient",
                "dead-letter-health",
                "actor",
                "OK",
                Map.of("phone", "138****8000")
        ));

        mockMvc.perform(get("/actuator/health/privacyAuditDeadLetters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.details.state").value("WARNING"))
                .andExpect(jsonPath("$.details.total").value(1))
                .andExpect(jsonPath("$.details.warningThreshold").value(1))
                .andExpect(jsonPath("$.details.downThreshold").value(5));

        mockMvc.perform(get("/actuator/metrics/privacy.audit.deadletters.total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("privacy.audit.deadletters.total"))
                .andExpect(jsonPath("$.measurements[0].value").value(1.0));

        mockMvc.perform(get("/actuator/metrics/privacy.audit.deadletters.state").param("tag", "state:warning"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("privacy.audit.deadletters.state"))
                .andExpect(jsonPath("$.measurements[0].value").value(1.0));

        mockMvc.perform(get("/actuator/metrics/privacy.audit.deadletters.threshold").param("tag", "level:down"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("privacy.audit.deadletters.threshold"))
                .andExpect(jsonPath("$.measurements[0].value").value(5.0));

        PrivacyAuditDeadLetterAlertEvent alertEvent = awaitAlert(Duration.ofSeconds(2));
        assertThat(alertEvent.currentSnapshot().state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.WARNING);
        assertThat(alertEvent.currentSnapshot().total()).isEqualTo(1L);
    }

    @Test
    void rejectsDeadLetterManagementRequestsWithoutAdminTokenAndAuditsAttempt() throws Exception {
        mockMvc.perform(get("/audit-dead-letters"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {
                          "error": "Missing or invalid X-Demo-Admin-Token header"
                        }
                        """));

        assertThat(auditRepository.findAll()).extracting("action")
                .contains("AUDIT_DEAD_LETTERS_ACCESS_DENIED");
    }

    @Test
    void exportsImportsAndAuditsDeadLettersThroughHttpEndpoints() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "failure",
                Instant.parse("2026-03-06T00:00:00Z"),
                "READ",
                "Patient",
                "dead-letter-demo",
                "actor",
                "OK",
                Map.of("phone", "138****8000")
        ));

        String json = mockMvc.perform(get("/audit-dead-letters/export.json")
                        .headers(httpHeaders(adminHeaders())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String csv = mockMvc.perform(get("/audit-dead-letters/export.csv")
                        .headers(httpHeaders(adminHeaders())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String checksum = mockMvc.perform(get("/audit-dead-letters/export.manifest")
                        .headers(httpHeaders(adminHeaders()))
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json).contains("dead-letter-demo");
        assertThat(csv).contains("dead-letter-demo").contains("failed_at");
        assertThat(checksum).contains("sha256");

        deadLetterRepository.clear();

        mockMvc.perform(post("/audit-dead-letters/import.json")
                        .headers(httpHeaders(adminHeaders()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("deduplicate", "true")
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "received": 1,
                          "imported": 1,
                          "skippedDuplicates": 0
                        }
                        """, false));

        mockMvc.perform(post("/audit-dead-letters/import.json")
                        .headers(httpHeaders(adminHeaders()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("deduplicate", "true")
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "received": 1,
                          "imported": 0,
                          "skippedDuplicates": 1
                        }
                        """, false));

        deadLetterRepository.clear();

        mockMvc.perform(post("/audit-dead-letters/import.csv")
                        .headers(httpHeaders(adminHeaders()))
                        .contentType("text/csv")
                        .param("deduplicate", "false")
                        .content(csv))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "received": 1,
                          "imported": 1,
                          "skippedDuplicates": 0
                        }
                        """, false));

        assertThat(deadLetterRepository.findAll()).hasSize(1);
        assertThat(auditRepository.findAll()).extracting("action")
                .contains("AUDIT_DEAD_LETTERS_EXPORT", "AUDIT_DEAD_LETTERS_EXPORT_MANIFEST", "AUDIT_DEAD_LETTERS_IMPORT");
    }

    @Test
    void filtersDeletesAndReportsDeadLetterStatsThroughHttpEndpoints() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "first failure",
                Instant.parse("2026-03-06T00:00:00Z"),
                "READ",
                "Patient",
                "dead-letter-a",
                "actor",
                "OK",
                Map.of()
        ));
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                4,
                "java.lang.IllegalStateException",
                "second failure",
                Instant.parse("2026-03-06T00:10:00Z"),
                "WRITE",
                "Order",
                "dead-letter-b",
                "actor",
                "DENIED",
                Map.of()
        ));
        long id = deadLetterRepository.findAll().stream()
                .filter(entry -> "dead-letter-a".equals(entry.resourceId()))
                .findFirst()
                .orElseThrow()
                .id();

        mockMvc.perform(get("/audit-dead-letters")
                        .headers(httpHeaders(adminHeaders()))
                        .param("resourceIdLike", "dead-letter-a"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {
                            "resourceId": "dead-letter-a"
                          }
                        ]
                        """, false));

        mockMvc.perform(get("/audit-dead-letters/stats")
                        .headers(httpHeaders(adminHeaders()))
                        .param("errorType", "java.lang.IllegalStateException"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "total": 2,
                          "byAction": {
                            "READ": 1,
                            "WRITE": 1
                          },
                          "byErrorType": {
                            "java.lang.IllegalStateException": 2
                          }
                        }
                        """, false));

        mockMvc.perform(delete("/audit-dead-letters/{id}", id)
                        .headers(httpHeaders(adminHeaders())))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        mockMvc.perform(delete("/audit-dead-letters")
                        .headers(httpHeaders(adminHeaders()))
                        .param("errorType", "java.lang.IllegalStateException"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        assertThat(deadLetterRepository.findAll()).isEmpty();
        assertThat(auditRepository.findAll()).extracting("action")
                .contains("AUDIT_DEAD_LETTERS_QUERY", "AUDIT_DEAD_LETTERS_STATS_QUERY", "AUDIT_DEAD_LETTER_DELETE", "AUDIT_DEAD_LETTERS_DELETE");
    }

    @Test
    void replaysDeadLetterEntryThroughHttpEndpointAndAuditsOperation() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "failure",
                Instant.parse("2026-03-06T00:00:00Z"),
                "READ",
                "Patient",
                "dead-letter-demo",
                "actor",
                "OK",
                Map.of("phone", "138****8000")
        ));
        long id = deadLetterRepository.findAll().get(0).id();

        mockMvc.perform(post("/audit-dead-letters/{id}/replay", id)
                        .headers(httpHeaders(adminHeaders())))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        assertThat(deadLetterRepository.findAll()).isEmpty();
        assertThat(auditRepository.findAll()).extracting("resourceId").contains("dead-letter-demo");
        assertThat(auditRepository.findAll()).extracting("action").contains("AUDIT_DEAD_LETTER_REPLAY", "READ");
    }

    @Test
    void scopesSingleDeadLetterDeleteAndReplayByTenantWhenRequested() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "tenant a failure",
                Instant.parse("2026-03-06T00:00:00Z"),
                "READ",
                "Patient",
                "dead-letter-tenant-a",
                "actor",
                "OK",
                Map.of("tenant", "tenant-a")
        ));
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "tenant b failure",
                Instant.parse("2026-03-06T00:01:00Z"),
                "READ",
                "Patient",
                "dead-letter-tenant-b",
                "actor",
                "OK",
                Map.of("tenant", "tenant-b")
        ));
        long tenantAId = deadLetterRepository.findAll().stream()
                .filter(entry -> "dead-letter-tenant-a".equals(entry.resourceId()))
                .findFirst()
                .orElseThrow()
                .id();
        long tenantBId = deadLetterRepository.findAll().stream()
                .filter(entry -> "dead-letter-tenant-b".equals(entry.resourceId()))
                .findFirst()
                .orElseThrow()
                .id();

        mockMvc.perform(delete("/audit-dead-letters/{id}", tenantAId)
                        .headers(httpHeaders(adminHeaders()))
                        .param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));

        mockMvc.perform(delete("/audit-dead-letters/{id}", tenantAId)
                        .headers(httpHeaders(adminHeaders()))
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        mockMvc.perform(post("/audit-dead-letters/{id}/replay", tenantBId)
                        .headers(httpHeaders(adminHeaders()))
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));

        mockMvc.perform(post("/audit-dead-letters/{id}/replay", tenantBId)
                        .headers(httpHeaders(adminHeaders()))
                        .param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        assertThat(deadLetterRepository.findAll()).isEmpty();
        assertThat(auditRepository.findAll()).extracting("resourceId").contains("dead-letter-tenant-b");
        assertThat(auditRepository.findAll()).filteredOn(event ->
                        "AUDIT_DEAD_LETTER_DELETE".equals(event.action())
                                && "tenant-a".equals(event.details().get("tenant"))
                                && "true".equals(event.details().get("deleted")))
                .hasSize(1);
        assertThat(auditRepository.findAll()).filteredOn(event ->
                        "AUDIT_DEAD_LETTER_REPLAY".equals(event.action())
                                && "tenant-b".equals(event.details().get("tenant"))
                                && "true".equals(event.details().get("replayed")))
                .hasSize(1);

        mockMvc.perform(get("/demo-tenants/observability")
                        .headers(httpHeaders(adminHeaders())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writePaths.deadLetterDeleteById.native").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterReplayById.native").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1.0)));
    }

    private PrivacyAuditDeadLetterAlertEvent awaitAlert(Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            java.util.Optional<PrivacyAuditDeadLetterAlertEvent> alert = demoDeadLetterAlertCallback.lastAlert();
            if (alert.isPresent()) {
                return alert.get();
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("Timed out waiting for dead-letter alert callback");
    }
}
