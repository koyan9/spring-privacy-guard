/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"jdbc-tenant", "jdbc-tenant-node2"})
@Tag("sample")
@Tag("sample-jdbc")
@Tag("sample-multi-instance")
class PrivacyDemoJdbcTenantNode2ProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesSecondInstanceMetadataThroughTenantEndpoints() throws Exception {
        mockMvc.perform(get("/demo-tenants/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("node-2"));

        mockMvc.perform(get("/demo-tenants/observability")
                        .header("X-Demo-Admin-Token", "demo-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("node-2"))
                .andExpect(jsonPath("$.receiverReplayStore.backend").value("JDBC"))
                .andExpect(jsonPath("$.auditRepositoryType").value("JDBC"))
                .andExpect(jsonPath("$.deadLetterRepositoryType").value("JDBC"));
    }
}
