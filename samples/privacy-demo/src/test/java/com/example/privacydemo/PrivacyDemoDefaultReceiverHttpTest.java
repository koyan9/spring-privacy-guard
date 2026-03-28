/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("sample")
@Tag("sample-default")
@Tag("sample-receiver")
class PrivacyDemoDefaultReceiverHttpTest extends SampleReceiverRouteTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryPrivacyAuditRepository auditRepository;

    @Autowired
    private DemoWebhookAlertReceiverStore receiverStore;

    @Autowired
    private PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    @BeforeEach
    void resetState() {
        auditRepository.clear();
        receiverStore.clear();
        replayStore.clear();
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
        String timestamp = currentTimestamp();
        String nonce = "nonce-1";
        String signature = signPayload(payload, timestamp, nonce, DEFAULT_RECEIVER_SIGNATURE_SECRET);

        mockMvc.perform(post(DEFAULT_RECEIVER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(httpHeaders(receiverHeaders(DEFAULT_RECEIVER_BEARER_TOKEN, timestamp, nonce, signature)))
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.verified").value(true));

        mockMvc.perform(get("/demo-alert-receiver/last")
                        .headers(httpHeaders(adminHeaders())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.state").value("WARNING"))
                .andExpect(jsonPath("$.payload.total").value(2));
    }

    @Test
    void acceptsSignedWebhookAlertsThroughTenantSpecificReceiverRoute() throws Exception {
        String payload = """
                {
                  "state": "WARNING",
                  "tenantId": "tenant-a"
                }
                """;
        String timestamp = currentTimestamp();
        String nonce = "tenant-a-route";
        String signature = signPayload(payload, timestamp, nonce, TENANT_A_RECEIVER_SIGNATURE_SECRET);

        mockMvc.perform(post(TENANT_A_RECEIVER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(httpHeaders(receiverHeaders(TENANT_A_RECEIVER_BEARER_TOKEN, timestamp, nonce, signature)))
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.verified").value(true));

        assertThat(receiverStore.lastReceived()).isPresent();
        assertThat(replayStore.snapshot()).containsKey(TENANT_A_REPLAY_NAMESPACE + ":" + nonce);
    }

    @Test
    void managesReplayStoreThroughProtectedEndpoints() throws Exception {
        String payload = """
                {
                  "state": "WARNING"
                }
                """;
        String timestamp = currentTimestamp();
        String nonce = "nonce-manage";
        String signature = signPayload(payload, timestamp, nonce, DEFAULT_RECEIVER_SIGNATURE_SECRET);

        mockMvc.perform(post(DEFAULT_RECEIVER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(httpHeaders(receiverHeaders(DEFAULT_RECEIVER_BEARER_TOKEN, timestamp, nonce, signature)))
                        .content(payload))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/demo-alert-receiver/replay-store")
                        .headers(httpHeaders(adminHeaders()))
                        .param("limit", "1")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.namespace").value("demo-default"))
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.entries[0].nonce").value("nonce-manage"))
                .andExpect(jsonPath("$.entries[0].storageKey").value("demo-default:nonce-manage"));

        mockMvc.perform(get("/demo-alert-receiver/replay-store/stats")
                        .headers(httpHeaders(adminHeaders()))
                        .param("expiringWithin", "PT10M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.namespace").value("demo-default"))
                .andExpect(jsonPath("$.expiringWithin").value("PT10M"))
                .andExpect(jsonPath("$.expiringSoon").value(1));

        mockMvc.perform(delete("/demo-alert-receiver/replay-store")
                        .headers(httpHeaders(adminHeaders())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleared").value(1));

        mockMvc.perform(get("/demo-alert-receiver/replay-store")
                        .headers(httpHeaders(adminHeaders())))
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
        String timestamp = currentTimestamp();
        String nonce = "nonce-replay";
        String signature = signPayload(payload, timestamp, nonce, DEFAULT_RECEIVER_SIGNATURE_SECRET);

        mockMvc.perform(post(DEFAULT_RECEIVER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(httpHeaders(receiverHeaders(DEFAULT_RECEIVER_BEARER_TOKEN, timestamp, nonce, signature)))
                        .content(payload))
                .andExpect(status().isAccepted());

        mockMvc.perform(post(DEFAULT_RECEIVER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(httpHeaders(receiverHeaders(DEFAULT_RECEIVER_BEARER_TOKEN, timestamp, nonce, signature)))
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsWebhookAlertsWithInvalidSignature() throws Exception {
        String payload = "{\"state\":\"WARNING\"}";
        String timestamp = currentTimestamp();
        String nonce = "nonce-invalid";

        mockMvc.perform(post(DEFAULT_RECEIVER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + DEFAULT_RECEIVER_BEARER_TOKEN)
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", "invalid-signature")
                        .content(payload))
                .andExpect(status().isUnauthorized());

        assertThat(receiverStore.lastReceived()).isEmpty();
    }

    @Test
    void exposesTenantSpecificReceiverRoutesInObservabilityView() throws Exception {
        String payload = "{\"state\":\"WARNING\"}";
        String timestamp = currentTimestamp();
        String nonce = "tenant-a-invalid";

        mockMvc.perform(post(TENANT_A_RECEIVER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TENANT_A_RECEIVER_BEARER_TOKEN)
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", "invalid-signature")
                        .content(payload))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/demo-tenants/observability")
                        .headers(httpHeaders(adminHeaders())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantOperationalMetrics.receiverRouteFailures['/demo-alert-receiver/tenant-a'].invalidSignature")
                        .value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.tenantOperationalMetrics.receiverRouteFailures['/demo-alert-receiver/tenant-b'].invalidSignature")
                        .value(greaterThanOrEqualTo(0.0)));
    }
}
