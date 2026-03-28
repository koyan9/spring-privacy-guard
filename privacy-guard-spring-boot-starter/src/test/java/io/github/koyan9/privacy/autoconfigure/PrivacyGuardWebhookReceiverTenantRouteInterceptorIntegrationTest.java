/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookSignatureSupport;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationTelemetry;
import io.github.koyan9.privacy.audit.MicrometerPrivacyAuditDeadLetterWebhookVerificationTelemetry;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {
                PrivacyGuardWebhookReceiverTenantRouteInterceptorIntegrationTest.TestApplication.class,
                PrivacyGuardWebhookReceiverTenantRouteInterceptorIntegrationTest.AlertReceiverController.class
        },
        properties = {
                "privacy.guard.audit.enabled=false",
                "privacy.guard.audit.dead-letter.observability.alert.receiver.filter.enabled=false",
                "privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor.enabled=true",
                "privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor.path-pattern=/receiver/default-interceptor-alerts",
                "privacy.guard.tenant.enabled=true",
                "privacy.guard.tenant.policies.tenant-b.observability.dead-letter.alert.receiver.path-pattern=/receiver/tenant-b-alerts",
                "privacy.guard.tenant.policies.tenant-b.observability.dead-letter.alert.receiver.bearer-token=tenant-b-token",
                "privacy.guard.tenant.policies.tenant-b.observability.dead-letter.alert.receiver.signature-secret=tenant-b-secret",
                "privacy.guard.tenant.policies.tenant-b.observability.dead-letter.alert.receiver.replay-namespace=tenant-b-receiver"
        }
)
@AutoConfigureMockMvc
@Import(PrivacyGuardWebhookReceiverTenantRouteInterceptorIntegrationTest.TelemetryConfig.class)
class PrivacyGuardWebhookReceiverTenantRouteInterceptorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void verifiesTenantRouteWithRouteSpecificSecret() throws Exception {
        String body = "{\"state\":\"WARNING\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "tenant-b-nonce";
        String signature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(
                timestamp + "." + nonce + "." + body,
                "tenant-b-secret",
                "HmacSHA256"
        );

        mockMvc.perform(post("/receiver/tenant-b-alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer tenant-b-token")
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", signature)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(body));

        assertThat(replayStore.snapshot()).containsKey("tenant-b-receiver:" + nonce);
    }

    @Test
    void recordsRouteFailureMetricWhenTenantInterceptorRouteVerificationFails() throws Exception {
        String body = "{\"state\":\"WARNING\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "tenant-b-invalid";
        String signature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(
                timestamp + "." + nonce + "." + body,
                "wrong-secret",
                "HmacSHA256"
        );

        mockMvc.perform(post("/receiver/tenant-b-alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer tenant-b-token")
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", signature)
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertThat(meterRegistry.get("privacy.audit.deadletters.receiver.route.failures")
                .tag("route", "/receiver/tenant-b-alerts")
                .tag("reason", "invalid_signature")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void rejectsReplayedNonceOnTenantInterceptorRouteAndRecordsRouteMetric() throws Exception {
        String body = "{\"state\":\"WARNING\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "tenant-b-replay";
        String signature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(
                timestamp + "." + nonce + "." + body,
                "tenant-b-secret",
                "HmacSHA256"
        );

        mockMvc.perform(post("/receiver/tenant-b-alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer tenant-b-token")
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", signature)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/receiver/tenant-b-alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer tenant-b-token")
                        .header("X-Privacy-Alert-Timestamp", timestamp)
                        .header("X-Privacy-Alert-Nonce", nonce)
                        .header("X-Privacy-Alert-Signature", signature)
                        .content(body))
                .andExpect(status().isConflict());

        assertThat(replayStore.snapshot()).containsKey("tenant-b-receiver:" + nonce);
        assertThat(meterRegistry.get("privacy.audit.deadletters.receiver.route.failures")
                .tag("route", "/receiver/tenant-b-alerts")
                .tag("reason", "replay_detected")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @SpringBootApplication
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TelemetryConfig {

        @Bean
        PrivacyAuditDeadLetterWebhookVerificationTelemetry privacyAuditDeadLetterWebhookVerificationTelemetry(
                MeterRegistry meterRegistry
        ) {
            return new MicrometerPrivacyAuditDeadLetterWebhookVerificationTelemetry(meterRegistry);
        }
    }

    @RestController
    static class AlertReceiverController {

        @PostMapping("/receiver/tenant-b-alerts")
        String receiveTenant(@RequestBody String body) {
            return body;
        }

        @PostMapping("/receiver/default-interceptor-alerts")
        String receiveDefault(@RequestBody String body) {
            return body;
        }
    }
}
