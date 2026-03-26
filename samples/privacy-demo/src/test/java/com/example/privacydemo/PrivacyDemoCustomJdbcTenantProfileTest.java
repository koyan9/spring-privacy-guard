/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("custom-jdbc-tenant")
class PrivacyDemoCustomJdbcTenantProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomJdbcTenantAuditRepository auditRepository;

    @Autowired
    private CustomJdbcTenantDeadLetterRepository deadLetterRepository;

    @Autowired
    private PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    @Autowired
    private JdbcOperations jdbcOperations;

    @BeforeEach
    void resetJdbcBackedState() {
        jdbcOperations.update("delete from privacy_audit_event_custom_jdbc_demo");
        jdbcOperations.update("delete from privacy_audit_dead_letter_custom_jdbc_demo");
        jdbcOperations.update("delete from privacy_audit_webhook_replay_store_custom_jdbc_demo");
        replayStore.clear();
    }

    @Test
    void exposesCustomJdbcTenantRepositoriesAsNative() throws Exception {
        assertThat(auditRepository).isNotNull();
        assertThat(deadLetterRepository).isNotNull();

        mockMvc.perform(get("/patients/demo").header("X-Privacy-Tenant", "tenant-a"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit-events")
                        .param("action", "PATIENT_READ")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit-events/stats")
                        .param("action", "PATIENT_READ")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/demo-tenants/observability")
                        .header("X-Demo-Admin-Token", "demo-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("custom-jdbc-node-1"))
                .andExpect(jsonPath("$.receiverReplayStore.backend").value("JDBC"))
                .andExpect(jsonPath("$.repositoryImplementations.audit").value("CustomJdbcTenantAuditRepository"))
                .andExpect(jsonPath("$.repositoryImplementations.deadLetter").value("CustomJdbcTenantDeadLetterRepository"))
                .andExpect(jsonPath("$.repositoryCapabilities.audit.tenantReadNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.audit.tenantWriteNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantReadNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantExchangeReadNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantImportNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantDeleteNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantReplayNative").value(true))
                .andExpect(jsonPath("$.readPaths.audit.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.readPaths.auditStats.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.readPaths.deadLetterExport.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.readPaths.deadLetterManifest.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.auditWrite.native").value(greaterThanOrEqualTo(1.0)));
    }
}
