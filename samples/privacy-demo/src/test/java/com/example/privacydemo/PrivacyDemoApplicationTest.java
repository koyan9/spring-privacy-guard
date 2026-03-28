/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterBacklogState;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterEntry;
import io.github.koyan9.privacy.audit.PrivacyAuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("sample")
@Tag("sample-default")
@Tag("sample-tenant")
class PrivacyDemoApplicationTest {

    private static final String ADMIN_TOKEN_HEADER = "X-Demo-Admin-Token";
    private static final String ADMIN_TOKEN_VALUE = "demo-admin-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryPrivacyAuditDeadLetterRepository deadLetterRepository;

    @Autowired
    private InMemoryPrivacyAuditRepository auditRepository;

    @Autowired
    private DemoDeadLetterAlertCallback demoDeadLetterAlertCallback;

    @BeforeEach
    void resetRepositories() throws Exception {
        deadLetterRepository.clear();
        auditRepository.clear();
        Thread.sleep(120L);
        demoDeadLetterAlertCallback.reset();
    }

    @Test
    void returnsMaskedPatientPayloadUsingCustomNameStrategy() throws Exception {
        mockMvc.perform(get("/patients/demo"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "patientName": "CUSTOM-A****",
                          "phone": "138****8000",
                          "idCard": "1101**********1234",
                          "email": "a****@example.com",
                          "note": "raw-note"
                        }
                        """));

        assertThat(auditRepository.findAll()).extracting("action").contains("PATIENT_READ");
    }

    @Test
    void filtersAuditEventsAndStatsByTenant() throws Exception {
        mockMvc.perform(get("/patients/demo").with(tenant("tenant-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value("TENANT-A-A####"));

        mockMvc.perform(get("/patients/demo").with(tenant("tenant-b")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value("TENANT-B-AXXXX"));

        mockMvc.perform(get("/patients/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value("CUSTOM-A****"));

        mockMvc.perform(get("/audit-events")
                        .param("action", "PATIENT_READ")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].details.tenant").value("tenant-a"))
                .andExpect(jsonPath("$[0].details.employeeCode").value("E#####4"))
                .andExpect(jsonPath("$[0].details.phone").value("138####8000"))
                .andExpect(jsonPath("$[0].details.idCard").doesNotExist());

        mockMvc.perform(get("/audit-events")
                        .param("action", "PATIENT_READ")
                        .param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].details.tenant").value("tenant-b"))
                .andExpect(jsonPath("$[0].details.phone").value("138XXXX8000"))
                .andExpect(jsonPath("$[0].details.employeeCode").doesNotExist())
                .andExpect(jsonPath("$[0].details.idCard").doesNotExist());

        mockMvc.perform(get("/audit-events")
                        .param("action", "PATIENT_READ")
                        .param("tenant", "public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].details.tenant").value("public"))
                .andExpect(jsonPath("$[0].details.phone").value("138****8000"))
                .andExpect(jsonPath("$[0].details.employeeCode").value("EMP1234"))
                .andExpect(jsonPath("$[0].details.idCard").value("1101**********1234"));

        mockMvc.perform(get("/audit-events/stats")
                        .param("action", "PATIENT_READ")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.byAction.PATIENT_READ").value(1))
                .andExpect(jsonPath("$.byResourceType.Patient").value(1));
    }

    @Test
    void exposesTenantContextAndProtectedTenantPolicyOverview() throws Exception {
        mockMvc.perform(get("/demo-tenants/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantModeEnabled").value(true))
                .andExpect(jsonPath("$.headerName").value("X-Privacy-Tenant"))
                .andExpect(jsonPath("$.defaultTenant").value("public"))
                .andExpect(jsonPath("$.currentTenant").value("public"))
                .andExpect(jsonPath("$.instanceId").value("default"))
                .andExpect(jsonPath("$.configuredTenants[0]").value("public"))
                .andExpect(jsonPath("$.managementFacade").value("PrivacyTenantAuditManagementService"));

        mockMvc.perform(get("/demo-tenants/current").with(tenant("tenant-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTenant").value("tenant-a"));

        mockMvc.perform(get("/demo-tenants/policies"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/demo-tenants/policies").with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-Privacy-Tenant"))
                .andExpect(jsonPath("$.defaultTenant").value("public"))
                .andExpect(jsonPath("$.tenants.length()").value(3))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].fallbackMaskChar").value(hasItem("#")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].textAdditionalPatternCount").value(hasItem(1)))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].auditIncludeDetailKeys[*]").value(hasItem("phone")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].auditAttachTenantId").value(hasItem(true)))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].deadLetterWarningThreshold").value(hasItem(1)))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].deadLetterDownThreshold").value(hasItem(2)))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].deadLetterNotifyOnRecovery").value(hasItem(true)))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].loggingMdcEnabledOverride").value(hasItem(true)))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].loggingMdcIncludeKeys[*]").value(hasItem("email")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].deadLetterAlertReceiverPathPattern")
                        .value(hasItem("/demo-alert-receiver/tenant-a")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].deadLetterAlertReceiverReplayNamespace")
                        .value(hasItem("tenant-a-receiver")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].deadLetterAlertLoggingEnabledOverride")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].deadLetterAlertEnabledOverride")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-b')].loggingStructuredEnabledOverride").value(hasItem(true)))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-b')].loggingStructuredIncludeKeys[*]").value(hasItem("phone")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-b')].auditTenantDetailKey").value(hasItem("tenant")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-b')].deadLetterNotifyOnRecovery").value(hasItem(false)))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-b')].deadLetterAlertEnabledOverride")
                        .value(hasItem(false)))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-b')].deadLetterAlertLoggingEnabledOverride")
                        .value(hasItem(false)))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-b')].deadLetterAlertReceiverPathPattern")
                        .value(hasItem("/demo-alert-receiver/tenant-b")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-b')].deadLetterAlertReceiverReplayNamespace")
                        .value(hasItem("tenant-b-receiver")));
    }

    @Test
    void exposesProtectedTenantObservabilitySnapshot() throws Exception {
        mockMvc.perform(get("/demo-tenants/observability"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/patients/demo").with(tenant("tenant-a")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit-events")
                        .param("action", "PATIENT_READ")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit-events/stats")
                        .param("action", "PATIENT_READ")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/demo-tenants/observability").with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.instanceId").value("default"))
                .andExpect(jsonPath("$.receiverReplayStore.backend").value("IN_MEMORY"))
                .andExpect(jsonPath("$.receiverReplayStore.namespace").value("demo-default"))
                .andExpect(jsonPath("$.auditRepositoryType").value("IN_MEMORY"))
                .andExpect(jsonPath("$.deadLetterRepositoryType").value("IN_MEMORY"))
                .andExpect(jsonPath("$.repositoryCapabilities.audit.tenantReadNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.audit.tenantWriteNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantFindByIdNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantDeleteByIdNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantReplayByIdNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantExchangeReadNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantImportNative").value(true))
                .andExpect(jsonPath("$.expectedPaths.read.audit").value("native"))
                .andExpect(jsonPath("$.expectedPaths.read.deadLetterFindById").value("native"))
                .andExpect(jsonPath("$.expectedPaths.read.deadLetterExport").value("native"))
                .andExpect(jsonPath("$.expectedPaths.write.auditWrite").value("native"))
                .andExpect(jsonPath("$.expectedPaths.write.deadLetterImport").value("native"))
                .andExpect(jsonPath("$.expectedPaths.write.deadLetterDeleteById").value("native"))
                .andExpect(jsonPath("$.expectedPaths.write.deadLetterReplay").value("native"))
                .andExpect(jsonPath("$.expectedPaths.write.deadLetterReplayById").value("native"))
                .andExpect(jsonPath("$.deadLetterBacklog.available").value(true))
                .andExpect(jsonPath("$.deadLetterBacklog.global.total").value(0))
                .andExpect(jsonPath("$.deadLetterBacklog.global.state").value("CLEAR"))
                .andExpect(jsonPath("$.deadLetterBacklog.currentTenant").value("public"))
                .andExpect(jsonPath("$.tenantAlerting.enabled").value(true))
                .andExpect(jsonPath("$.tenantAlerting.tenantIds.length()").value(1))
                .andExpect(jsonPath("$.tenantAlerting.tenantIds[0]").value("tenant-a"))
                .andExpect(jsonPath("$.readPaths.audit.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.readPaths.auditStats.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.readPaths.deadLetterFindById.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.readPaths.deadLetterExport.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.readPaths.deadLetterManifest.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.auditWrite.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.auditBatchWrite.fallback").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterImport.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterDeleteById.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterReplayById.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.tenantOperationalMetrics.alertTransitions['tenant-a'].warning").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.tenantOperationalMetrics.alertDeliveries['tenant-a'].loggingSuccess").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.tenantOperationalMetrics.receiverRouteFailures['/demo-alert-receiver'].invalidSignature").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.actuatorQueries.length()").value(17))
                .andExpect(jsonPath("$.actuatorQueries[0]").value("/actuator/health"))
                .andExpect(jsonPath("$.actuatorQueries[7]").value("/actuator/metrics/privacy.audit.tenant.write.path?tag=domain:audit_batch_write&tag=path:fallback"));
    }

    @Test
    void exposesTenantScopedDeadLetterBacklogView() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
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

        DemoDeadLetterAlertCallback.TenantAlertRecord tenantAlert = awaitTenantAlert(Duration.ofSeconds(2));
        assertThat(tenantAlert.tenantId()).isEqualTo("tenant-a");
        assertThat(tenantAlert.event().currentSnapshot().state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.WARNING);

        mockMvc.perform(get("/demo-tenants/observability").with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadLetterBacklog.global.total").value(1))
                .andExpect(jsonPath("$.deadLetterBacklog.global.state").value("WARNING"))
                .andExpect(jsonPath("$.deadLetterBacklog.currentTenantSnapshot.total").value(0))
                .andExpect(jsonPath("$.deadLetterBacklog.currentTenantSnapshot.state").value("CLEAR"))
                .andExpect(jsonPath("$.deadLetterBacklog.configuredTenants['tenant-a'].total").value(1))
                .andExpect(jsonPath("$.deadLetterBacklog.configuredTenants['tenant-a'].state").value("WARNING"))
                .andExpect(jsonPath("$.deadLetterBacklog.configuredTenants['tenant-a'].warningThreshold").value(1))
                .andExpect(jsonPath("$.deadLetterBacklog.configuredTenants['tenant-a'].downThreshold").value(2))
                .andExpect(jsonPath("$.deadLetterBacklog.configuredTenants['tenant-b'].total").value(0))
                .andExpect(jsonPath("$.deadLetterBacklog.configuredTenants['tenant-b'].state").value("CLEAR"))
                .andExpect(jsonPath("$.tenantAlerting.lastTenantAlert.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.tenantAlerting.lastTenantAlert.recovery").value(false))
                .andExpect(jsonPath("$.tenantAlerting.lastTenantAlert.snapshot.total").value(1))
                .andExpect(jsonPath("$.tenantAlerting.lastTenantAlert.snapshot.state").value("WARNING"))
                .andExpect(jsonPath("$.tenantOperationalMetrics.alertTransitions['tenant-a'].warning").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.tenantOperationalMetrics.alertDeliveries['tenant-a'].loggingSuccess").value(greaterThanOrEqualTo(1.0)));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.privacyAuditTenantDeadLetters.status").value("UP"))
                .andExpect(jsonPath("$.components.privacyAuditTenantDeadLetters.details.state").value("WARNING"))
                .andExpect(jsonPath("$.components.privacyAuditTenantDeadLetters.details.tenantCount").value(3))
                .andExpect(jsonPath("$.components.privacyAuditTenantDeadLetters.details.warningTenants[0]").value("tenant-a"))
                .andExpect(jsonPath("$.components.privacyAuditTenantDeadLetters.details.tenants['tenant-a'].state").value("WARNING"))
                .andExpect(jsonPath("$.components.privacyAuditTenantDeadLetters.details.tenants['tenant-b'].state").value("CLEAR"));
    }

    @Test
    void exportsAndImportsDeadLettersWithTenantScope() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
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
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "tenant b failure",
                Instant.parse("2026-03-06T00:00:00Z"),
                "READ",
                "Patient",
                "dead-letter-tenant-b",
                "actor",
                "OK",
                Map.of("tenant", "tenant-b", "phone", "138XXXX8000")
        ));

        String tenantJson = mockMvc.perform(get("/audit-dead-letters/export.json").with(adminToken()).param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/audit-dead-letters/export.manifest").with(adminToken())
                        .param("format", "json")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        assertThat(tenantJson).contains("dead-letter-tenant-a");
        assertThat(tenantJson).doesNotContain("dead-letter-tenant-b");

        deadLetterRepository.clear();

        mockMvc.perform(post("/audit-dead-letters/import.json").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("tenant", "tenant-b")
                        .content(tenantJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        mockMvc.perform(get("/audit-dead-letters").with(adminToken()).param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].details.tenant").value("tenant-b"))
                .andExpect(jsonPath("$[0].resourceId").value("dead-letter-tenant-a"));

        mockMvc.perform(get("/demo-tenants/observability").with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readPaths.deadLetterExport.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.readPaths.deadLetterManifest.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterImport.native").value(greaterThanOrEqualTo(1.0)));
    }

    @Test
    void filtersDeadLettersAndStatsByTenant() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
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
                Instant.now(),
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

        mockMvc.perform(get("/audit-dead-letters").with(adminToken()).param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].resourceId").value("dead-letter-tenant-a"))
                .andExpect(jsonPath("$[0].details.tenant").value("tenant-a"));

        mockMvc.perform(get("/audit-dead-letters/stats").with(adminToken()).param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.byAction.READ").value(1))
                .andExpect(jsonPath("$.byResourceType.Patient").value(1))
                .andExpect(jsonPath("$.byErrorType['java.lang.IllegalStateException']").value(1));
    }

    @Test
    void deletesAndReplaysDeadLettersByTenant() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "tenant a failure",
                Instant.parse("2026-03-06T00:00:00Z"),
                "READ",
                "Patient",
                "dead-letter-delete-tenant-a",
                "actor",
                "OK",
                Map.of("tenant", "tenant-a", "phone", "138####8000")
        ));
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                4,
                "java.lang.IllegalStateException",
                "tenant b failure",
                Instant.parse("2026-03-06T00:10:00Z"),
                "READ",
                "Patient",
                "dead-letter-replay-tenant-b",
                "actor",
                "OK",
                Map.of("tenant", "tenant-b", "phone", "138XXXX8000")
        ));

        mockMvc.perform(delete("/audit-dead-letters").with(adminToken()).param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        assertThat(deadLetterRepository.findAll()).extracting(PrivacyAuditDeadLetterEntry::resourceId)
                .containsExactly("dead-letter-replay-tenant-b");

        mockMvc.perform(post("/audit-dead-letters/replay").with(adminToken()).param("tenant", "tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(1))
                .andExpect(jsonPath("$.replayed").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        assertThat(deadLetterRepository.findAll()).isEmpty();
        assertThat(auditRepository.findAll()).extracting(PrivacyAuditEvent::resourceId)
                .contains("dead-letter-replay-tenant-b");
        assertThat(auditRepository.findAll()).extracting(PrivacyAuditEvent::action)
                .contains("AUDIT_DEAD_LETTERS_DELETE", "AUDIT_DEAD_LETTERS_REPLAY", "READ");

        mockMvc.perform(get("/demo-tenants/observability").with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writePaths.deadLetterDelete.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterDeleteById.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterReplay.native").value(greaterThanOrEqualTo(1.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterReplayById.native").value(greaterThanOrEqualTo(0.0)));
    }

    private DemoDeadLetterAlertCallback.TenantAlertRecord awaitTenantAlert(Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            java.util.Optional<DemoDeadLetterAlertCallback.TenantAlertRecord> alert = demoDeadLetterAlertCallback.lastTenantAlert();
            if (alert.isPresent()) {
                return alert.get();
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("Timed out waiting for tenant dead-letter alert callback");
    }

    private RequestPostProcessor adminToken() {
        return request -> {
            request.addHeader(ADMIN_TOKEN_HEADER, ADMIN_TOKEN_VALUE);
            return request;
        };
    }

    private RequestPostProcessor tenant(String tenantId) {
        return request -> {
            request.addHeader("X-Privacy-Tenant", tenantId);
            return request;
        };
    }
}
