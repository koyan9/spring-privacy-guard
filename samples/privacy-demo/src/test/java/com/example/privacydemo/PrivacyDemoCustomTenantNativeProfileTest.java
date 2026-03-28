/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("custom-tenant-native")
@Tag("sample")
@Tag("sample-custom")
@Tag("sample-tenant")
class PrivacyDemoCustomTenantNativeProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomTenantAuditRepository auditRepository;

    @Autowired
    private CustomTenantDeadLetterRepository deadLetterRepository;

    @BeforeEach
    void resetState() {
        auditRepository.clear();
        deadLetterRepository.clear();
    }

    @Test
    void exposesCustomNativeRepositoryCapabilitiesAndTelemetry() throws Exception {
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
                Map.of("tenant", "tenant-a", "phone", "138####8000")
        ));
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.parse("2026-03-06T00:10:00Z"),
                4,
                "java.lang.IllegalStateException",
                "tenant b failure",
                Instant.parse("2026-03-06T00:10:00Z"),
                "READ",
                "Patient",
                "dead-letter-tenant-b",
                "actor",
                "OK",
                Map.of("tenant", "tenant-b", "phone", "138XXXX8000")
        ));

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

        String tenantJson = mockMvc.perform(get("/audit-dead-letters/export.json")
                        .header("X-Demo-Admin-Token", "demo-admin-token")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/audit-dead-letters/export.manifest")
                        .header("X-Demo-Admin-Token", "demo-admin-token")
                        .param("tenant", "tenant-a")
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(delete("/audit-dead-letters")
                        .header("X-Demo-Admin-Token", "demo-admin-token")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/audit-dead-letters/import.json")
                        .header("X-Demo-Admin-Token", "demo-admin-token")
                        .contentType("application/json")
                        .param("tenant", "tenant-b")
                        .content(tenantJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        mockMvc.perform(post("/audit-dead-letters/replay")
                        .header("X-Demo-Admin-Token", "demo-admin-token")
                        .param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/demo-tenants/observability")
                        .header("X-Demo-Admin-Token", "demo-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("custom-native-node-1"))
                .andExpect(jsonPath("$.auditRepositoryType").value("NONE"))
                .andExpect(jsonPath("$.deadLetterRepositoryType").value("NONE"))
                .andExpect(jsonPath("$.repositoryImplementations.audit").value("CustomTenantAuditRepository"))
                .andExpect(jsonPath("$.repositoryImplementations.deadLetter").value("CustomTenantDeadLetterRepository"))
                .andExpect(jsonPath("$.repositoryCapabilities.audit.tenantReadNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.audit.tenantWriteNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantFindByIdNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantReadNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantExchangeReadNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantImportNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantDeleteNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantDeleteByIdNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantReplayNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantReplayByIdNative").value(true))
                .andExpect(jsonPath("$.readPaths.audit.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.readPaths.auditStats.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.readPaths.deadLetterExport.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.readPaths.deadLetterManifest.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.auditWrite.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterImport.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterDelete.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterDeleteById.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterReplay.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterReplayById.native").value(greaterThanOrEqualTo(0.0)));
    }
}
