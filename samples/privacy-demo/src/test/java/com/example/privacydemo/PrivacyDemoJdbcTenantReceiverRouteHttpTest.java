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

import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jdbc-tenant")
@Tag("sample")
@Tag("sample-jdbc")
@Tag("sample-receiver")
class PrivacyDemoJdbcTenantReceiverRouteHttpTest extends SampleJdbcHttpTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcOperations jdbcOperations;

    @Autowired
    private PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    @Autowired
    private DemoWebhookAlertReceiverStore receiverStore;

    @BeforeEach
    void resetJdbcBackedState() {
        resetJdbcState(jdbcOperations, replayStore, JDBC_AUDIT_TABLE, JDBC_DEAD_LETTER_TABLE, JDBC_REPLAY_TABLE);
        receiverStore.clear();
    }

    @Test
    void acceptsTenantSpecificReceiverRouteAndAuditsReplayStoreQuery() throws Exception {
        String payload = """
                {
                  "state": "WARNING",
                  "total": 2
                }
                """;
        String timestamp = currentTimestamp();
        String nonce = "tenant-a-route-nonce";
        String signature = signPayload(payload, timestamp, nonce, TENANT_A_RECEIVER_SIGNATURE_SECRET);

        mockMvc.perform(post(TENANT_A_RECEIVER_PATH)
                        .contentType(APPLICATION_JSON)
                        .headers(httpHeaders(receiverHeaders(TENANT_A_RECEIVER_BEARER_TOKEN, timestamp, nonce, signature)))
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.verified").value(true));

        mockMvc.perform(get("/demo-alert-receiver/replay-store")
                        .headers(httpHeaders(tenantAdminHeaders("tenant-a")))
                        .param("limit", "20")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.entries[0].storageKey").value(TENANT_A_REPLAY_NAMESPACE + ":" + nonce));

        mockMvc.perform(get("/audit-events")
                        .param("action", "DEMO_ALERT_REPLAY_STORE_QUERY")
                        .param("tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].details.tenant").value("tenant-a"))
                .andExpect(jsonPath("$[0].resourceId").value("receiver"));
    }

    @Test
    void rejectsReplayNonceOnTenantSpecificReceiverRouteAndRecordsRouteTelemetry() throws Exception {
        String payload = """
                {
                  "state": "WARNING"
                }
                """;
        String timestamp = currentTimestamp();
        String nonce = "tenant-a-route-replay";
        String signature = signPayload(payload, timestamp, nonce, TENANT_A_RECEIVER_SIGNATURE_SECRET);

        mockMvc.perform(post(TENANT_A_RECEIVER_PATH)
                        .contentType(APPLICATION_JSON)
                        .headers(httpHeaders(receiverHeaders(TENANT_A_RECEIVER_BEARER_TOKEN, timestamp, nonce, signature)))
                        .content(payload))
                .andExpect(status().isAccepted());

        mockMvc.perform(post(TENANT_A_RECEIVER_PATH)
                        .contentType(APPLICATION_JSON)
                        .headers(httpHeaders(receiverHeaders(TENANT_A_RECEIVER_BEARER_TOKEN, timestamp, nonce, signature)))
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Replay detected"))
                .andExpect(jsonPath("$.reason").value("REPLAY_DETECTED"));

        mockMvc.perform(get("/demo-alert-receiver/replay-store")
                        .headers(httpHeaders(tenantAdminHeaders("tenant-a")))
                        .param("limit", "20")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.entries[0].storageKey").value(TENANT_A_REPLAY_NAMESPACE + ":" + nonce));
    }
}
