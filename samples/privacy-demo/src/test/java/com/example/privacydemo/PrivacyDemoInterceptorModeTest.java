/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookSignatureSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("interceptor")
@Tag("sample")
@Tag("sample-receiver")
@Tag("sample-interceptor")
class PrivacyDemoInterceptorModeTest {

    private static final String RECEIVER_BEARER_TOKEN = "demo-receiver-token";
    private static final String RECEIVER_SIGNATURE_SECRET = "demo-receiver-secret";
    private static final String RECEIVER_SIGNATURE_ALGORITHM = "HmacSHA256";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    @BeforeEach
    void resetReplayStore() {
        replayStore.clear();
    }

    @Test
    void acceptsSignedWebhookAlertsThroughInterceptorMode() throws Exception {
        String payload = """
                {
                  "state": "WARNING",
                  "total": 2
                }
                """;
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "interceptor-nonce-1";
        String signature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(
                timestamp + "." + nonce + "." + payload,
                RECEIVER_SIGNATURE_SECRET,
                RECEIVER_SIGNATURE_ALGORITHM
        );

        mockMvc.perform(post("/demo-alert-receiver")
                        .contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + RECEIVER_BEARER_TOKEN)
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", signature)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    void rejectsReplayNonceThroughInterceptorMode() throws Exception {
        String payload = "{\"state\":\"WARNING\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "interceptor-replay";
        String signature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(
                timestamp + "." + nonce + "." + payload,
                RECEIVER_SIGNATURE_SECRET,
                RECEIVER_SIGNATURE_ALGORITHM
        );

        mockMvc.perform(post("/demo-alert-receiver")
                        .contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + RECEIVER_BEARER_TOKEN)
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", signature)
                        .content(payload))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/demo-alert-receiver")
                        .contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + RECEIVER_BEARER_TOKEN)
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", signature)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Replay detected"));
    }
}
