/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivacyAuditDeadLetterWebhookAlertCallbackTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsSignedAlertPayloadAndRecordsSuccessMetrics() throws Exception {
        LinkedBlockingQueue<CapturedRequest> requests = new LinkedBlockingQueue<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/alerts", exchange -> handleRequest(exchange, requests, 204));
        server.start();

        PrivacyGuardProperties.AlertWebhook properties = webhookProperties(server, 3, Duration.ofMillis(5));
        properties.setBearerToken("demo-token");
        properties.setSignatureSecret("demo-secret");

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PrivacyAuditDeadLetterWebhookAlertCallback callback = new PrivacyAuditDeadLetterWebhookAlertCallback(
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build(),
                new ObjectMapper(),
                properties,
                new MicrometerPrivacyAuditDeadLetterWebhookAlertTelemetry(meterRegistry)
        );

        callback.handle(event(PrivacyAuditDeadLetterBacklogState.WARNING, null, 2));

        CapturedRequest request = requests.poll(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.authorization()).isEqualTo("Bearer demo-token");
        assertThat(request.signature()).isEqualTo(sign(request.timestamp() + "." + request.nonce() + "." + request.body(), "demo-secret", "HmacSHA256"));
        assertThat(meterRegistry.get("privacy.audit.deadletters.alert.webhook.attempts").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("privacy.audit.deadletters.alert.webhook.deliveries").tag("outcome", "success").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("privacy.audit.deadletters.alert.webhook.last_delivery_seconds").tag("outcome", "success").gauge().value()).isPositive();
        assertThat(meterRegistry.get("privacy.audit.deadletters.alert.webhook.last_delivery_seconds").tag("outcome", "failure").gauge().value()).isZero();
    }

    @Test
    void retriesBeforeSucceeding() throws Exception {
        LinkedBlockingQueue<CapturedRequest> requests = new LinkedBlockingQueue<>();
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/alerts", exchange -> {
            int attempt = requestCount.incrementAndGet();
            handleRequest(exchange, requests, attempt == 1 ? 500 : 204);
        });
        server.start();

        PrivacyGuardProperties.AlertWebhook properties = webhookProperties(server, 2, Duration.ofMillis(5));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PrivacyAuditDeadLetterWebhookAlertCallback callback = new PrivacyAuditDeadLetterWebhookAlertCallback(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                properties,
                new MicrometerPrivacyAuditDeadLetterWebhookAlertTelemetry(meterRegistry)
        );

        callback.handle(event(PrivacyAuditDeadLetterBacklogState.WARNING, null, 2));

        assertThat(requestCount.get()).isEqualTo(2);
        assertThat(meterRegistry.get("privacy.audit.deadletters.alert.webhook.attempts").counter().count()).isEqualTo(2.0d);
        assertThat(meterRegistry.get("privacy.audit.deadletters.alert.webhook.retries").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("privacy.audit.deadletters.alert.webhook.deliveries").tag("outcome", "success").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void recordsFailureMetricAfterExhaustingRetries() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/alerts", exchange -> handleRequest(exchange, new LinkedBlockingQueue<>(), 500));
        server.start();

        PrivacyGuardProperties.AlertWebhook properties = webhookProperties(server, 2, Duration.ofMillis(5));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PrivacyAuditDeadLetterWebhookAlertCallback callback = new PrivacyAuditDeadLetterWebhookAlertCallback(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                properties,
                new MicrometerPrivacyAuditDeadLetterWebhookAlertTelemetry(meterRegistry)
        );

        assertThatThrownBy(() -> callback.handle(event(PrivacyAuditDeadLetterBacklogState.DOWN, PrivacyAuditDeadLetterBacklogState.WARNING, 5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status 500");
        assertThat(meterRegistry.get("privacy.audit.deadletters.alert.webhook.attempts").counter().count()).isEqualTo(2.0d);
        assertThat(meterRegistry.get("privacy.audit.deadletters.alert.webhook.retries").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("privacy.audit.deadletters.alert.webhook.deliveries").tag("outcome", "failure").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("privacy.audit.deadletters.alert.webhook.last_delivery_seconds").tag("outcome", "failure").gauge().value()).isPositive();
    }

    private PrivacyGuardProperties.AlertWebhook webhookProperties(HttpServer httpServer, int maxAttempts, Duration backoff) {
        PrivacyGuardProperties.AlertWebhook properties = new PrivacyGuardProperties.AlertWebhook();
        properties.setUrl("http://127.0.0.1:" + httpServer.getAddress().getPort() + "/alerts");
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.setMaxAttempts(maxAttempts);
        properties.setBackoff(backoff);
        return properties;
    }

    private String sign(String value, String secret, String algorithm) throws Exception {
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void handleRequest(HttpExchange exchange, LinkedBlockingQueue<CapturedRequest> requests, int status) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.offer(new CapturedRequest(
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("X-Privacy-Alert-Timestamp"),
                exchange.getRequestHeaders().getFirst("X-Privacy-Alert-Nonce"),
                exchange.getRequestHeaders().getFirst("X-Privacy-Alert-Signature"),
                body
        ));
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private PrivacyAuditDeadLetterAlertEvent event(
            PrivacyAuditDeadLetterBacklogState state,
            PrivacyAuditDeadLetterBacklogState previousState,
            long total
    ) {
        PrivacyAuditDeadLetterBacklogSnapshot previous = previousState == null
                ? null
                : new PrivacyAuditDeadLetterBacklogSnapshot(1, 1, 5, previousState, Map.of(), Map.of(), Map.of(), Map.of());
        return new PrivacyAuditDeadLetterAlertEvent(
                Instant.now(),
                new PrivacyAuditDeadLetterBacklogSnapshot(total, 1, 5, state, Map.of("READ", total), Map.of(), Map.of(), Map.of()),
                previous
        );
    }

    record CapturedRequest(String authorization, String timestamp, String nonce, String signature, String body) {
    }
}
