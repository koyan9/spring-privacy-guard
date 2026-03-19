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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jdbc-tenant")
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
}
