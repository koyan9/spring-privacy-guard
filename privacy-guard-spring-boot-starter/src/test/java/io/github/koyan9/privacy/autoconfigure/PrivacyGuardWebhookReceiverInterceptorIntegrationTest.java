/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookSignatureSupport;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {
                PrivacyGuardWebhookReceiverInterceptorIntegrationTest.TestApplication.class,
                PrivacyGuardWebhookReceiverInterceptorIntegrationTest.AlertReceiverController.class
        },
        properties = {
                "privacy.guard.audit.enabled=false",
                "privacy.guard.audit.dead-letter.observability.alert.receiver.filter.enabled=false",
                "privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor.enabled=true",
                "privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor.path-pattern=/receiver/interceptor-alerts"
        }
)
@AutoConfigureMockMvc
class PrivacyGuardWebhookReceiverInterceptorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    @Test
    void allowsValidSignedWebhookRequests() throws Exception {
        String body = "{\"state\":\"WARNING\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-valid";
        String signature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(timestamp + "." + nonce + "." + body, "secret", "HmacSHA256");

        mockMvc.perform(post("/receiver/interceptor-alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token")
                        .header("X-Timestamp", timestamp)
                        .header("X-Nonce", nonce)
                        .header("X-Signature", signature)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(body));
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        String body = "{\"state\":\"WARNING\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        mockMvc.perform(post("/receiver/interceptor-alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token")
                        .header("X-Timestamp", timestamp)
                        .header("X-Nonce", "nonce-invalid")
                        .header("X-Signature", "invalid")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {
                          "error": "Invalid signature"
                        }
                        """));
    }

    @Test
    void rejectsReplayNonce() throws Exception {
        String body = "{\"state\":\"WARNING\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-replay";
        String signature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(timestamp + "." + nonce + "." + body, "secret", "HmacSHA256");

        mockMvc.perform(post("/receiver/interceptor-alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token")
                        .header("X-Timestamp", timestamp)
                        .header("X-Nonce", nonce)
                        .header("X-Signature", signature)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/receiver/interceptor-alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token")
                        .header("X-Timestamp", timestamp)
                        .header("X-Nonce", nonce)
                        .header("X-Signature", signature)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(content().json("""
                        {
                          "error": "Replay detected"
                        }
                        """));
    }

    @SpringBootApplication
    static class TestApplication {

        @Bean
        PrivacyAuditDeadLetterWebhookVerificationSettings privacyAuditDeadLetterWebhookVerificationSettings() {
            return new PrivacyAuditDeadLetterWebhookVerificationSettings(
                    "token",
                    "secret",
                    "HmacSHA256",
                    "X-Signature",
                    "X-Timestamp",
                    "X-Nonce",
                    Duration.ofMinutes(5),
                    null
            );
        }
    }

    @RestController
    static class AlertReceiverController {

        @PostMapping("/receiver/interceptor-alerts")
        String receive(@RequestBody String body) {
            return body;
        }
    }
}
