/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterEntry;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "privacy.guard.tenant.policies.tenant-a.audit.attach-tenant-id=false",
        "privacy.guard.tenant.policies.tenant-b.audit.attach-tenant-id=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("jdbc-tenant")
@Tag("sample")
@Tag("sample-jdbc")
@Tag("sample-materialization")
@Tag("sample-dead-letter")
class PrivacyDemoJdbcTenantDeadLetterMaterializationHttpTest extends SampleJdbcHttpTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcOperations jdbcOperations;

    @Autowired
    private PrivacyAuditDeadLetterRepository deadLetterRepository;

    @Autowired
    private PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    @BeforeEach
    void resetJdbcBackedState() {
        resetJdbcState(jdbcOperations, replayStore, JDBC_AUDIT_TABLE, JDBC_DEAD_LETTER_TABLE, JDBC_REPLAY_TABLE);
    }

    @Test
    void exportsAndRecordsTenantScopedDeadLetterAuditEventsThroughJdbcHttpFlow() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.parse("2026-03-06T00:00:00Z"),
                3,
                "java.lang.IllegalStateException",
                "tenant a failure",
                Instant.parse("2026-03-06T00:00:00Z"),
                "READ",
                "Patient",
                "dead-letter-tenant-a",
                "actor",
                "OK",
                java.util.Map.of("tenant", "tenant-a", "phone", "138####8000")
        ));

        String tenantJson = mockMvc.perform(get("/audit-dead-letters/export.json")
                        .header("X-Demo-Admin-Token", ADMIN_TOKEN)
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/audit-dead-letters/export.manifest")
                        .header("X-Demo-Admin-Token", ADMIN_TOKEN)
                        .param("tenant", "tenant-a")
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        assertThat(tenantJson).contains("dead-letter-tenant-a");

        mockMvc.perform(get("/audit-events")
                        .param("action", "AUDIT_DEAD_LETTERS_EXPORT")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].details.tenant").value("tenant-a"))
                .andExpect(jsonPath("$[0].details.format").value("json"));

        mockMvc.perform(get("/audit-events")
                        .param("action", "AUDIT_DEAD_LETTERS_EXPORT_MANIFEST")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].details.tenant").value("tenant-a"))
                .andExpect(jsonPath("$[0].details.format").value("json"))
                .andExpect(jsonPath("$[0].details.total").value("1"));

        String exportDetailsJson = queryAuditDetailsJson(jdbcOperations, JDBC_AUDIT_TABLE, "AUDIT_DEAD_LETTERS_EXPORT", "tenant-a");
        String manifestDetailsJson = queryAuditDetailsJson(jdbcOperations, JDBC_AUDIT_TABLE, "AUDIT_DEAD_LETTERS_EXPORT_MANIFEST", "tenant-a");

        assertThat(exportDetailsJson)
                .contains("\"tenant\":\"tenant-a\"")
                .contains("\"format\":\"json\"");
        assertThat(manifestDetailsJson)
                .contains("\"tenant\":\"tenant-a\"")
                .contains("\"format\":\"json\"")
                .contains("\"total\":\"1\"");
    }

    @Test
    void importsAndRecordsTenantScopedDeadLetterAuditEventsThroughJdbcHttpFlow() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.parse("2026-03-06T00:00:00Z"),
                3,
                "java.lang.IllegalStateException",
                "tenant a failure",
                Instant.parse("2026-03-06T00:00:00Z"),
                "READ",
                "Patient",
                "dead-letter-tenant-a",
                "actor",
                "OK",
                java.util.Map.of("tenant", "tenant-a", "phone", "138####8000")
        ));

        String tenantJson = mockMvc.perform(get("/audit-dead-letters/export.json")
                        .header("X-Demo-Admin-Token", ADMIN_TOKEN)
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        jdbcOperations.update("delete from " + JDBC_DEAD_LETTER_TABLE);

        mockMvc.perform(post("/audit-dead-letters/import.json")
                        .header("X-Demo-Admin-Token", ADMIN_TOKEN)
                        .contentType("application/json")
                        .param("tenant", "tenant-b")
                        .content(tenantJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        mockMvc.perform(get("/audit-dead-letters")
                        .header("X-Demo-Admin-Token", ADMIN_TOKEN)
                        .param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].details.tenant").value("tenant-b"))
                .andExpect(jsonPath("$[0].resourceId").value("dead-letter-tenant-a"));

        mockMvc.perform(get("/audit-events")
                        .param("action", "AUDIT_DEAD_LETTERS_IMPORT")
                        .param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].details.tenant").value("tenant-b"))
                .andExpect(jsonPath("$[0].details.format").value("json"))
                .andExpect(jsonPath("$[0].details.imported").value("1"));

        String importDetailsJson = queryAuditDetailsJson(jdbcOperations, JDBC_AUDIT_TABLE, "AUDIT_DEAD_LETTERS_IMPORT", "tenant-b");
        String importedDeadLetterDetailsJson = queryDeadLetterDetailsJsonByTenant(jdbcOperations, JDBC_DEAD_LETTER_TABLE, "tenant-b");

        assertThat(importDetailsJson)
                .contains("\"tenant\":\"tenant-b\"")
                .contains("\"format\":\"json\"")
                .contains("\"imported\":\"1\"");
        assertThat(importedDeadLetterDetailsJson)
                .contains("\"tenant\":\"tenant-b\"")
                .contains("\"phone\":\"138####8000\"");
    }

    @Test
    void scopesSingleDeadLetterDeleteAndReplayByTenantThroughJdbcHttpFlow() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.parse("2026-03-06T00:00:00Z"),
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
                Instant.parse("2026-03-06T00:01:00Z"),
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
        long tenantAId = deadLetterRepository.findByCriteria(io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterQueryCriteria.recent(10)).stream()
                .filter(entry -> "dead-letter-tenant-a".equals(entry.resourceId()))
                .findFirst()
                .orElseThrow()
                .id();
        long tenantBId = deadLetterRepository.findByCriteria(io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterQueryCriteria.recent(10)).stream()
                .filter(entry -> "dead-letter-tenant-b".equals(entry.resourceId()))
                .findFirst()
                .orElseThrow()
                .id();

        mockMvc.perform(delete("/audit-dead-letters/{id}", tenantAId)
                        .header("X-Demo-Admin-Token", ADMIN_TOKEN)
                        .param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));

        mockMvc.perform(delete("/audit-dead-letters/{id}", tenantAId)
                        .header("X-Demo-Admin-Token", ADMIN_TOKEN)
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        mockMvc.perform(post("/audit-dead-letters/{id}/replay", tenantBId)
                        .header("X-Demo-Admin-Token", ADMIN_TOKEN)
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));

        mockMvc.perform(post("/audit-dead-letters/{id}/replay", tenantBId)
                        .header("X-Demo-Admin-Token", ADMIN_TOKEN)
                        .param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        mockMvc.perform(get("/audit-events")
                        .param("action", "AUDIT_DEAD_LETTER_DELETE")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].details.tenant").value("tenant-a"))
                .andExpect(jsonPath("$[0].details.deleted").value("true"));

        mockMvc.perform(get("/audit-events")
                        .param("action", "AUDIT_DEAD_LETTER_REPLAY")
                        .param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].details.tenant").value("tenant-b"))
                .andExpect(jsonPath("$[0].details.replayed").value("true"));

        mockMvc.perform(get("/demo-tenants/observability")
                        .header("X-Demo-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writePaths.deadLetterDeleteById.native").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterReplayById.native").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1.0)));

        Integer remaining = jdbcOperations.queryForObject("select count(*) from " + JDBC_DEAD_LETTER_TABLE, Integer.class);
        assertThat(remaining).isZero();
    }
}
