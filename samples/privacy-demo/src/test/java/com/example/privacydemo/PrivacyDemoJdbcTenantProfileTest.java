/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.JdbcPrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.JdbcPrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.JdbcPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jdbc-tenant")
@Tag("sample")
@Tag("sample-jdbc")
class PrivacyDemoJdbcTenantProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrivacyAuditRepository privacyAuditRepository;

    @Autowired
    private io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterRepository deadLetterRepository;

    @Autowired
    private PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    @Autowired
    private JdbcOperations jdbcOperations;

    @Autowired
    private javax.sql.DataSource dataSource;

    @BeforeEach
    void resetJdbcBackedState() {
        jdbcOperations.update("delete from privacy_audit_event_jdbc_demo");
        jdbcOperations.update("delete from privacy_audit_dead_letter_jdbc_demo");
        jdbcOperations.update("delete from privacy_audit_webhook_replay_store_jdbc_demo");
        replayStore.clear();
    }

    @Test
    void wiresJdbcTenantProfileRepositories() {
        assertThat(privacyAuditRepository).isInstanceOf(JdbcPrivacyAuditRepository.class);
        assertThat(deadLetterRepository).isInstanceOf(JdbcPrivacyAuditDeadLetterRepository.class);
        assertThat(replayStore).isInstanceOf(JdbcPrivacyAuditDeadLetterWebhookReplayStore.class);
    }

    @Test
    void persistsAndQueriesTenantScopedAuditEventsThroughJdbcProfile() throws Exception {
        mockMvc.perform(get("/patients/demo").header("X-Privacy-Tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value("TENANT-A-A####"));

        Integer count = jdbcOperations.queryForObject(
                "select count(*) from privacy_audit_event_jdbc_demo where action = ? and tenant_key = ?",
                Integer.class,
                "PATIENT_READ",
                "tenant-a"
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void exposesNativeTenantObservabilityThroughJdbcProfile() throws Exception {
        mockMvc.perform(get("/demo-tenants/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("node-1"));

        mockMvc.perform(get("/demo-tenants/policies")
                        .header("X-Demo-Admin-Token", "demo-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].deadLetterAlertReceiverPathPattern")
                        .value(hasItem("/demo-alert-receiver/tenant-a")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].deadLetterAlertReceiverReplayNamespace")
                        .value(hasItem("tenant-a-receiver")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-b')].deadLetterAlertReceiverPathPattern")
                        .value(hasItem("/demo-alert-receiver/tenant-b")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-b')].deadLetterAlertReceiverReplayNamespace")
                        .value(hasItem("tenant-b-receiver")));

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
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.instanceId").value("node-1"))
                .andExpect(jsonPath("$.receiverReplayStore.backend").value("JDBC"))
                .andExpect(jsonPath("$.receiverReplayStore.namespace").value("demo-default"))
                .andExpect(jsonPath("$.auditRepositoryType").value("JDBC"))
                .andExpect(jsonPath("$.deadLetterRepositoryType").value("JDBC"))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantFindByIdNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantDeleteByIdNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantReplayByIdNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantExchangeReadNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantImportNative").value(true))
                .andExpect(jsonPath("$.readPaths.audit.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.readPaths.auditStats.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.readPaths.deadLetterExport.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.readPaths.deadLetterManifest.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.auditWrite.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterImport.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterDeleteById.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterReplayById.native").value(greaterThanOrEqualTo(0.0)));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.privacyAuditTenantDeadLetters.status").value("UP"))
                .andExpect(jsonPath("$.components.privacyAuditTenantDeadLetters.details.tenantCount").value(greaterThanOrEqualTo(1)));

        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getJdbcUrl())
                .contains("jdbc:h2:file:")
                .contains("privacy-demo-jdbc-tenant-shared")
                .contains("AUTO_SERVER=TRUE");
    }
}
