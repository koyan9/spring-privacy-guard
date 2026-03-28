/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class PrivacyDemoJdbcTenantWriteMaterializationHttpTest extends SampleJdbcHttpTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcOperations jdbcOperations;

    @Autowired
    private PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    @BeforeEach
    void resetJdbcBackedState() {
        resetJdbcState(jdbcOperations, replayStore, JDBC_AUDIT_TABLE, JDBC_DEAD_LETTER_TABLE, JDBC_REPLAY_TABLE);
    }

    @Test
    void exposesTenantAMaterializedAuditDetailsThroughJdbcHttpFlow() throws Exception {
        mockMvc.perform(get("/patients/demo").header("X-Privacy-Tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value("TENANT-A-A####"));

        mockMvc.perform(get("/audit-events")
                        .param("action", "PATIENT_READ")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].details.tenant").value("tenant-a"))
                .andExpect(jsonPath("$[0].details.phone").value("138####8000"))
                .andExpect(jsonPath("$[0].details.employeeCode").value("E#####4"))
                .andExpect(jsonPath("$[0].details.idCard").doesNotExist());

        String detailsJson = queryAuditDetailsJson(jdbcOperations, JDBC_AUDIT_TABLE, "PATIENT_READ", "tenant-a");

        assertThat(detailsJson)
                .contains("\"tenant\":\"tenant-a\"")
                .contains("\"phone\":\"138####8000\"")
                .contains("\"employeeCode\":\"E#####4\"");
    }

    @Test
    void exposesTenantBMaterializedAuditDetailsAndStatsThroughJdbcHttpFlow() throws Exception {
        mockMvc.perform(get("/patients/demo").header("X-Privacy-Tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value("TENANT-B-AXXXX"));

        mockMvc.perform(get("/audit-events")
                        .param("action", "PATIENT_READ")
                        .param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].details.tenant").value("tenant-b"))
                .andExpect(jsonPath("$[0].details.phone").value("138XXXX8000"))
                .andExpect(jsonPath("$[0].details.employeeCode").doesNotExist());

        mockMvc.perform(get("/audit-events/stats")
                        .param("action", "PATIENT_READ")
                        .param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.byAction.PATIENT_READ").value(1))
                .andExpect(jsonPath("$.byResourceType.Patient").value(1));

        String detailsJson = queryAuditDetailsJson(jdbcOperations, JDBC_AUDIT_TABLE, "PATIENT_READ", "tenant-b");

        assertThat(detailsJson)
                .contains("\"tenant\":\"tenant-b\"")
                .contains("\"phone\":\"138XXXX8000\"")
                .doesNotContain("employeeCode");
    }
}
