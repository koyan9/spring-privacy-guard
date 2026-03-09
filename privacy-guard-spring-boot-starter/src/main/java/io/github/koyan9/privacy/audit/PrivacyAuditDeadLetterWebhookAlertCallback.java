/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public class PrivacyAuditDeadLetterWebhookAlertCallback implements PrivacyAuditDeadLetterAlertCallback {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PrivacyGuardProperties.AlertWebhook properties;
    private final PrivacyAuditDeadLetterWebhookAlertTelemetry telemetry;

    public PrivacyAuditDeadLetterWebhookAlertCallback(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            PrivacyGuardProperties.AlertWebhook properties,
            PrivacyAuditDeadLetterWebhookAlertTelemetry telemetry
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.telemetry = telemetry == null ? PrivacyAuditDeadLetterWebhookAlertTelemetry.noop() : telemetry;
    }

    @Override
    public void handle(PrivacyAuditDeadLetterAlertEvent event) {
        String payload = toPayload(event);
        RuntimeException lastFailure = null;
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            telemetry.recordAttempt();
            try {
                deliver(payload);
                telemetry.recordSuccess(attempt);
                return;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt >= maxAttempts) {
                    telemetry.recordFailure(attempt);
                    throw ex;
                }
                telemetry.recordRetryScheduled(attempt + 1);
                pauseBeforeRetry();
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
    }

    private void deliver(String payload) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getUrl()))
                    .timeout(properties.getReadTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));
            if (StringUtils.hasText(properties.getBearerToken())) {
                builder.header("Authorization", "Bearer " + properties.getBearerToken().trim());
            }
            addSignatureHeaders(builder, payload);
            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Webhook alert callback returned status " + response.statusCode());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Webhook alert callback interrupted", ex);
        }
    }

    private void addSignatureHeaders(HttpRequest.Builder builder, String payload) {
        if (!StringUtils.hasText(properties.getSignatureSecret())) {
            return;
        }
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        builder.header(properties.getTimestampHeader(), timestamp);
        builder.header(properties.getNonceHeader(), nonce);
        builder.header(properties.getSignatureHeader(), sign(timestamp + "." + nonce + "." + payload));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(properties.getSignatureAlgorithm());
            mac.init(new SecretKeySpec(properties.getSignatureSecret().getBytes(StandardCharsets.UTF_8), properties.getSignatureAlgorithm()));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign webhook alert payload", ex);
        }
    }

    private String toPayload(PrivacyAuditDeadLetterAlertEvent event) {
        try {
            return objectMapper.writeValueAsString(PrivacyAuditDeadLetterAlertPayloadFactory.createPayload(event));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(Math.max(1L, properties.getBackoff().toMillis()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Webhook alert callback retry interrupted", ex);
        }
    }
}
