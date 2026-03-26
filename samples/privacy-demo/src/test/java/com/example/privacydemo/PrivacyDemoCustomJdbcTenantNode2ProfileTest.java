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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"custom-jdbc-tenant", "custom-jdbc-tenant-node2"})
class PrivacyDemoCustomJdbcTenantNode2ProfileTest {

    @Autowired
    private MockMvc mockMvc;

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
    void exposesSecondCustomJdbcInstanceMetadata() throws Exception {
        mockMvc.perform(get("/demo-tenants/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("custom-jdbc-node-2"));

        mockMvc.perform(get("/demo-tenants/observability")
                        .header("X-Demo-Admin-Token", "demo-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("custom-jdbc-node-2"))
                .andExpect(jsonPath("$.receiverReplayStore.backend").value("JDBC"))
                .andExpect(jsonPath("$.repositoryImplementations.audit").value("CustomJdbcTenantAuditRepository"))
                .andExpect(jsonPath("$.repositoryImplementations.deadLetter").value("CustomJdbcTenantDeadLetterRepository"));
    }
}
