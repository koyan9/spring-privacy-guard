/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterAlertEvent;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterBacklogState;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterEntry;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookSignatureSupport;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PrivacyDemoApplicationTest {

    private static final String ADMIN_TOKEN_HEADER = "X-Demo-Admin-Token";
    private static final String ADMIN_TOKEN_VALUE = "demo-admin-token";
    private static final String RECEIVER_BEARER_TOKEN = "demo-receiver-token";
    private static final String RECEIVER_SIGNATURE_SECRET = "demo-receiver-secret";
    private static final String RECEIVER_SIGNATURE_ALGORITHM = "HmacSHA256";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryPrivacyAuditDeadLetterRepository deadLetterRepository;

    @Autowired
    private InMemoryPrivacyAuditRepository auditRepository;

    @Autowired
    private DemoDeadLetterAlertCallback demoDeadLetterAlertCallback;

    @Autowired
    private DemoWebhookAlertReceiverStore receiverStore;

    @Autowired
    private PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    @BeforeEach
    void resetRepositories() throws Exception {
        deadLetterRepository.clear();
        auditRepository.clear();
        receiverStore.clear();
        replayStore.clear();
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
    void acceptsSignedWebhookAlertsAndStoresLastPayload() throws Exception {
        String payload = """
                {
                  "state": "WARNING",
                  "total": 2,
                  "warningThreshold": 1,
                  "downThreshold": 5
                }
                """;
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-1";
        String signature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(
                timestamp + "." + nonce + "." + payload,
                RECEIVER_SIGNATURE_SECRET,
                RECEIVER_SIGNATURE_ALGORITHM
        );

        mockMvc.perform(post("/demo-alert-receiver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + RECEIVER_BEARER_TOKEN)
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", signature)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.verified").value(true));

        mockMvc.perform(get("/demo-alert-receiver/last").with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.state").value("WARNING"))
                .andExpect(jsonPath("$.payload.total").value(2));
    }

    @Test
    void managesReplayStoreThroughProtectedEndpoints() throws Exception {
        String payload = """
                {
                  "state": "WARNING"
                }
                """;
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-manage";
        String signature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(
                timestamp + "." + nonce + "." + payload,
                RECEIVER_SIGNATURE_SECRET,
                RECEIVER_SIGNATURE_ALGORITHM
        );

        mockMvc.perform(post("/demo-alert-receiver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + RECEIVER_BEARER_TOKEN)
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", signature)
                        .content(payload))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/demo-alert-receiver/replay-store").with(adminToken()).param("limit", "1").param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.entries[0].nonce").value("nonce-manage"));

        mockMvc.perform(get("/demo-alert-receiver/replay-store/stats").with(adminToken()).param("expiringWithin", "PT10M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.expiringWithin").value("PT10M"))
                .andExpect(jsonPath("$.expiringSoon").value(1));

        mockMvc.perform(delete("/demo-alert-receiver/replay-store").with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleared").value(1));

        mockMvc.perform(get("/demo-alert-receiver/replay-store").with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        assertThat(auditRepository.findAll()).extracting("action")
                .contains("DEMO_ALERT_REPLAY_STORE_QUERY", "DEMO_ALERT_REPLAY_STORE_STATS_QUERY", "DEMO_ALERT_REPLAY_STORE_CLEAR");
    }

    @Test
    void rejectsWebhookAlertsWithReplayNonce() throws Exception {
        String payload = """
                {
                  "state": "WARNING"
                }
                """;
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-replay";
        String signature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(
                timestamp + "." + nonce + "." + payload,
                RECEIVER_SIGNATURE_SECRET,
                RECEIVER_SIGNATURE_ALGORITHM
        );

        mockMvc.perform(post("/demo-alert-receiver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + RECEIVER_BEARER_TOKEN)
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", signature)
                        .content(payload))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/demo-alert-receiver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + RECEIVER_BEARER_TOKEN)
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", signature)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsWebhookAlertsWithInvalidSignature() throws Exception {
        String payload = "{" + "\"state\":\"WARNING\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-invalid";

        mockMvc.perform(post("/demo-alert-receiver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + RECEIVER_BEARER_TOKEN)
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", "invalid-signature")
                        .content(payload))
                .andExpect(status().isUnauthorized());

        assertThat(receiverStore.lastReceived()).isEmpty();
    }

    @Test
    void exposesDeadLetterHealthMetricsAndAlertCallbackViaActuator() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "failure",
                Instant.parse("2026-03-08T00:00:00Z"),
                "READ",
                "Patient",
                "dead-letter-health",
                "actor",
                "OK",
                Map.of("phone", "138****8000")
        ));

        mockMvc.perform(get("/actuator/health/privacyAuditDeadLetters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.details.state").value("WARNING"))
                .andExpect(jsonPath("$.details.total").value(1))
                .andExpect(jsonPath("$.details.warningThreshold").value(1))
                .andExpect(jsonPath("$.details.downThreshold").value(5));

        mockMvc.perform(get("/actuator/metrics/privacy.audit.deadletters.total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("privacy.audit.deadletters.total"))
                .andExpect(jsonPath("$.measurements[0].value").value(1.0));

        mockMvc.perform(get("/actuator/metrics/privacy.audit.deadletters.state").param("tag", "state:warning"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("privacy.audit.deadletters.state"))
                .andExpect(jsonPath("$.measurements[0].value").value(1.0));

        mockMvc.perform(get("/actuator/metrics/privacy.audit.deadletters.threshold").param("tag", "level:down"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("privacy.audit.deadletters.threshold"))
                .andExpect(jsonPath("$.measurements[0].value").value(5.0));

        PrivacyAuditDeadLetterAlertEvent alertEvent = awaitAlert(Duration.ofSeconds(2));
        assertThat(alertEvent.currentSnapshot().state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.WARNING);
        assertThat(alertEvent.currentSnapshot().total()).isEqualTo(1L);
    }

    @Test
    void rejectsDeadLetterManagementRequestsWithoutAdminTokenAndAuditsAttempt() throws Exception {
        mockMvc.perform(get("/audit-dead-letters"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {
                          "error": "Missing or invalid X-Demo-Admin-Token header"
                        }
                        """));

        assertThat(auditRepository.findAll()).extracting("action")
                .contains("AUDIT_DEAD_LETTERS_ACCESS_DENIED");
    }

    @Test
    void exportsImportsAndAuditsDeadLettersThroughHttpEndpoints() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "java.lang.IllegalStateException", "failure", Instant.parse("2026-03-06T00:00:00Z"), "READ", "Patient", "dead-letter-demo", "actor", "OK", Map.of("phone", "138****8000")));

        String json = mockMvc.perform(get("/audit-dead-letters/export.json").with(adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String csv = mockMvc.perform(get("/audit-dead-letters/export.csv").with(adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String checksum = mockMvc.perform(get("/audit-dead-letters/export.manifest").with(adminToken()).param("format", "json"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json).contains("dead-letter-demo");
        assertThat(csv).contains("dead-letter-demo").contains("failed_at");
        assertThat(checksum).contains("sha256");

        deadLetterRepository.clear();

        mockMvc.perform(post("/audit-dead-letters/import.json").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("deduplicate", "true")
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "received": 1,
                          "imported": 1,
                          "skippedDuplicates": 0
                        }
                        """, false));

        mockMvc.perform(post("/audit-dead-letters/import.json").with(adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("deduplicate", "true")
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "received": 1,
                          "imported": 0,
                          "skippedDuplicates": 1
                        }
                        """, false));

        deadLetterRepository.clear();

        mockMvc.perform(post("/audit-dead-letters/import.csv").with(adminToken())
                        .contentType("text/csv")
                        .param("deduplicate", "false")
                        .content(csv))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "received": 1,
                          "imported": 1,
                          "skippedDuplicates": 0
                        }
                        """, false));

        assertThat(deadLetterRepository.findAll()).hasSize(1);
        assertThat(auditRepository.findAll()).extracting("action")
                .contains("AUDIT_DEAD_LETTERS_EXPORT", "AUDIT_DEAD_LETTERS_EXPORT_MANIFEST", "AUDIT_DEAD_LETTERS_IMPORT");
    }

    @Test
    void filtersDeletesAndReportsDeadLetterStatsThroughHttpEndpoints() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 3, "java.lang.IllegalStateException", "first failure", Instant.parse("2026-03-06T00:00:00Z"), "READ", "Patient", "dead-letter-a", "actor", "OK", Map.of()));
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(null, Instant.now(), 4, "java.lang.IllegalStateException", "second failure", Instant.parse("2026-03-06T00:10:00Z"), "WRITE", "Order", "dead-letter-b", "actor", "DENIED", Map.of()));
        long id = deadLetterRepository.findAll().stream().filter(entry -> "dead-letter-a".equals(entry.resourceId())).findFirst().orElseThrow().id();

        mockMvc.perform(get("/audit-dead-letters").with(adminToken()).param("resourceIdLike", "dead-letter-a"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {
                            "resourceId": "dead-letter-a"
                          }
                        ]
                        """, false));

        mockMvc.perform(get("/audit-dead-letters/stats").with(adminToken()).param("errorType", "java.lang.IllegalStateException"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "total": 2,
                          "byAction": {
                            "READ": 1,
                            "WRITE": 1
                          },
                          "byErrorType": {
                            "java.lang.IllegalStateException": 2
                          }
                        }
                        """, false));

        mockMvc.perform(delete("/audit-dead-letters/{id}", id).with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        mockMvc.perform(delete("/audit-dead-letters").with(adminToken()).param("errorType", "java.lang.IllegalStateException"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        assertThat(deadLetterRepository.findAll()).isEmpty();
        assertThat(auditRepository.findAll()).extracting("action")
                .contains("AUDIT_DEAD_LETTERS_QUERY", "AUDIT_DEAD_LETTERS_STATS_QUERY", "AUDIT_DEAD_LETTER_DELETE", "AUDIT_DEAD_LETTERS_DELETE");
    }

    @Test
    void replaysDeadLetterEntryThroughHttpEndpointAndAuditsOperation() throws Exception {
        deadLetterRepository.save(new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "failure",
                Instant.parse("2026-03-06T00:00:00Z"),
                "READ",
                "Patient",
                "dead-letter-demo",
                "actor",
                "OK",
                Map.of("phone", "138****8000")
        ));
        long id = deadLetterRepository.findAll().get(0).id();

        mockMvc.perform(post("/audit-dead-letters/{id}/replay", id).with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        assertThat(deadLetterRepository.findAll()).isEmpty();
        assertThat(auditRepository.findAll()).extracting("resourceId").contains("dead-letter-demo");
        assertThat(auditRepository.findAll()).extracting("action").contains("AUDIT_DEAD_LETTER_REPLAY", "READ");
    }

    private PrivacyAuditDeadLetterAlertEvent awaitAlert(Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            java.util.Optional<PrivacyAuditDeadLetterAlertEvent> alert = demoDeadLetterAlertCallback.lastAlert();
            if (alert.isPresent()) {
                return alert.get();
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("Timed out waiting for dead-letter alert callback");
    }

    private RequestPostProcessor adminToken() {
        return request -> {
            request.addHeader(ADMIN_TOKEN_HEADER, ADMIN_TOKEN_VALUE);
            return request;
        };
    }
}



