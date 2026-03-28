/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditEvent;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryCriteria;
import io.github.koyan9.privacy.audit.PrivacyAuditSortDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "privacy.guard.tenant.policies.tenant-a.audit.attach-tenant-id=false",
        "privacy.guard.tenant.policies.tenant-b.audit.attach-tenant-id=false"
})
@AutoConfigureMockMvc
@Tag("sample")
@Tag("sample-default")
@Tag("sample-materialization")
class PrivacyDemoTenantWriteMaterializationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryPrivacyAuditRepository auditRepository;

    @BeforeEach
    void resetState() {
        auditRepository.clear();
    }

    @Test
    void queriesTenantAAuditEventsThroughHttpEvenWhenAttachTenantIdDisabled() throws Exception {
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

        assertThat(auditRepository.findAll())
                .filteredOn(event -> "PATIENT_READ".equals(event.action()))
                .singleElement()
                .extracting(PrivacyAuditEvent::details)
                .satisfies(details -> assertThat(details).containsEntry("tenant", "tenant-a"));
    }

    @Test
    void queriesTenantBAuditStatsThroughHttpEvenWhenAttachTenantIdDisabled() throws Exception {
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

        assertThat(auditRepository.findByCriteria(
                "tenant-b",
                "tenant",
                new PrivacyAuditQueryCriteria(
                        "PATIENT_READ",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        PrivacyAuditSortDirection.DESC,
                        10,
                        0
                )
        ))
                .extracting(PrivacyAuditEvent::resourceId)
                .isEqualTo(List.of("demo-patient-138XXXX8000"));
    }
}
