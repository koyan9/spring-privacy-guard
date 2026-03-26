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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TenantScopedPrivacyAuditDeadLetterWebhookAlertCallbackTest {

    private HttpServer globalServer;
    private HttpServer tenantServer;

    @AfterEach
    void stopServers() {
        if (globalServer != null) {
            globalServer.stop(0);
        }
        if (tenantServer != null) {
            tenantServer.stop(0);
        }
    }

    @Test
    void routesTenantToTenantSpecificWebhookTarget() throws Exception {
        LinkedBlockingQueue<String> globalBodies = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<String> tenantBodies = new LinkedBlockingQueue<>();
        globalServer = server(globalBodies);
        tenantServer = server(tenantBodies);

        PrivacyGuardProperties.AlertWebhook defaultProperties = webhookProperties(globalServer);
        PrivacyGuardProperties.AlertTenantRoute route = new PrivacyGuardProperties.AlertTenantRoute();
        route.getWebhook().setUrl("http://127.0.0.1:" + tenantServer.getAddress().getPort() + "/alerts");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        TenantScopedPrivacyAuditDeadLetterWebhookAlertCallback callback =
                new TenantScopedPrivacyAuditDeadLetterWebhookAlertCallback(
                        HttpClient.newHttpClient(),
                        new ObjectMapper(),
                        defaultProperties,
                        PrivacyAuditDeadLetterWebhookAlertTelemetry.noop(),
                        new MicrometerPrivacyTenantAuditTelemetry(registry),
                        Map.of("tenant-a", route)
                );

        callback.handle("tenant-a", event());

        assertThat(tenantBodies.poll(2, TimeUnit.SECONDS)).contains("\"tenantId\":\"tenant-a\"");
        assertThat(globalBodies.poll(200, TimeUnit.MILLISECONDS)).isNull();
        assertThat(registry.get("privacy.audit.deadletters.alert.tenant.deliveries")
                .tag("tenant", "tenant-a")
                .tag("channel", "webhook")
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void fallsBackToDefaultWebhookTargetWhenNoTenantRouteExists() throws Exception {
        LinkedBlockingQueue<String> globalBodies = new LinkedBlockingQueue<>();
        globalServer = server(globalBodies);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        TenantScopedPrivacyAuditDeadLetterWebhookAlertCallback callback =
                new TenantScopedPrivacyAuditDeadLetterWebhookAlertCallback(
                        HttpClient.newHttpClient(),
                        new ObjectMapper(),
                        webhookProperties(globalServer),
                        PrivacyAuditDeadLetterWebhookAlertTelemetry.noop(),
                        new MicrometerPrivacyTenantAuditTelemetry(registry),
                        Map.of()
                );

        callback.handle("tenant-b", event());

        assertThat(globalBodies.poll(2, TimeUnit.SECONDS)).contains("\"tenantId\":\"tenant-b\"");
        assertThat(registry.get("privacy.audit.deadletters.alert.tenant.deliveries")
                .tag("tenant", "tenant-b")
                .tag("channel", "webhook")
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    private HttpServer server(LinkedBlockingQueue<String> bodies) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/alerts", exchange -> handle(exchange, bodies));
        server.start();
        return server;
    }

    private void handle(HttpExchange exchange, LinkedBlockingQueue<String> bodies) throws IOException {
        bodies.offer(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private PrivacyGuardProperties.AlertWebhook webhookProperties(HttpServer server) {
        PrivacyGuardProperties.AlertWebhook properties = new PrivacyGuardProperties.AlertWebhook();
        properties.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/alerts");
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.setMaxAttempts(1);
        properties.setBackoff(Duration.ZERO);
        return properties;
    }

    private PrivacyAuditDeadLetterAlertEvent event() {
        return new PrivacyAuditDeadLetterAlertEvent(
                Instant.now(),
                new PrivacyAuditDeadLetterBacklogSnapshot(2, 1, 5, PrivacyAuditDeadLetterBacklogState.WARNING, Map.of("READ", 2L), Map.of(), Map.of(), Map.of()),
                null
        );
    }
}
