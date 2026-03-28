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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("fallback-tenant")
@Tag("sample")
@Tag("sample-fallback")
@Tag("sample-tenant")
class PrivacyDemoFallbackTenantProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FallbackTenantRepositoryConfiguration.FallbackAuditRepository auditRepository;

    @Autowired
    private FallbackTenantRepositoryConfiguration.FallbackDeadLetterRepository deadLetterRepository;

    @BeforeEach
    void resetState() {
        auditRepository.clear();
        deadLetterRepository.clear();
    }

    @Test
    void exposesFallbackRepositoryCapabilitiesAndTelemetry() throws Exception {
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

        mockMvc.perform(get("/patients/demo").header("X-Privacy-Tenant", "tenant-a"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit-events")
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

        deadLetterRepository.clear();

        mockMvc.perform(post("/audit-dead-letters/import.json")
                        .header("X-Demo-Admin-Token", "demo-admin-token")
                        .contentType("application/json")
                        .param("tenant", "tenant-b")
                        .content(tenantJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        mockMvc.perform(get("/demo-tenants/observability")
                        .header("X-Demo-Admin-Token", "demo-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("fallback-node-1"))
                .andExpect(jsonPath("$.auditRepositoryType").value("NONE"))
                .andExpect(jsonPath("$.deadLetterRepositoryType").value("NONE"))
                .andExpect(jsonPath("$.repositoryImplementations.audit").value("FallbackAuditRepository"))
                .andExpect(jsonPath("$.repositoryImplementations.deadLetter").value("FallbackDeadLetterRepository"))
                .andExpect(jsonPath("$.repositoryCapabilities.audit.tenantReadNative").value(false))
                .andExpect(jsonPath("$.repositoryCapabilities.audit.tenantWriteNative").value(false))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantFindByIdNative").value(false))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantReadNative").value(false))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantExchangeReadNative").value(false))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantImportNative").value(false))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantDeleteNative").value(false))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantDeleteByIdNative").value(false))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantReplayNative").value(false))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantReplayByIdNative").value(false))
                .andExpect(jsonPath("$.expectedPaths.read.audit").value("fallback"))
                .andExpect(jsonPath("$.expectedPaths.read.deadLetterExport").value("fallback"))
                .andExpect(jsonPath("$.expectedPaths.write.auditWrite").value("fallback"))
                .andExpect(jsonPath("$.expectedPaths.write.deadLetterImport").value("fallback"))
                .andExpect(jsonPath("$.expectedPaths.write.deadLetterDeleteById").value("fallback"))
                .andExpect(jsonPath("$.expectedPaths.write.deadLetterReplay").value("fallback"))
                .andExpect(jsonPath("$.expectedPaths.write.deadLetterReplayById").value("fallback"))
                .andExpect(jsonPath("$.readPaths.audit.fallback").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.readPaths.auditStats.fallback").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.readPaths.deadLetterExport.fallback").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.readPaths.deadLetterManifest.fallback").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.auditWrite.fallback").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterImport.fallback").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterDeleteById.fallback").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterReplayById.fallback").value(greaterThanOrEqualTo(0.0)));
    }
}
