/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Tag;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("sample")
@Tag("sample-jdbc")
@Tag("sample-receiver")
@Tag("sample-multi-instance")
class PrivacyDemoJdbcTenantMultiInstanceReceiverRouteTest extends SampleMultiInstanceHttpTestSupport {

    @TempDir
    Path tempDir;

    @Test
    void rejectsReplayAcrossNode1AndNode2ForTenantSpecificRouteUsingSharedJdbcReplayStore() throws Exception {
        String jdbcUrl = buildFileBackedH2JdbcUrl(tempDir.resolve("privacy-demo-jdbc-tenant-route-shared"));

        try (ConfigurableApplicationContext node1 = startNode(
                jdbcUrl,
                new Class[]{PrivacyGuardDemoApplication.class},
                new String[]{"jdbc-tenant"},
                "--server.port=0",
                "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--privacy.guard.audit.jdbc.dialect=H2",
                "--privacy.guard.audit.dead-letter.jdbc.dialect=H2",
                "--privacy.guard.audit.dead-letter.observability.alert.receiver.filter.path-pattern=/demo-alert-receiver-default",
                "--privacy.guard.tenant.enabled=true",
                "--privacy.guard.tenant.policies.tenant-a.observability.dead-letter.alert.receiver.path-pattern=" + TENANT_A_RECEIVER_PATH,
                "--privacy.guard.tenant.policies.tenant-a.observability.dead-letter.alert.receiver.bearer-token=" + TENANT_A_RECEIVER_BEARER_TOKEN,
                "--privacy.guard.tenant.policies.tenant-a.observability.dead-letter.alert.receiver.signature-secret=" + TENANT_A_RECEIVER_SIGNATURE_SECRET,
                "--privacy.guard.tenant.policies.tenant-a.observability.dead-letter.alert.receiver.replay-namespace=" + TENANT_A_REPLAY_NAMESPACE
        );
             ConfigurableApplicationContext node2 = startNode(
                     jdbcUrl,
                     new Class[]{PrivacyGuardDemoApplication.class},
                     new String[]{"jdbc-tenant", "jdbc-tenant-node2"},
                     "--server.port=0",
                     "--spring.datasource.url=" + jdbcUrl,
                     "--spring.datasource.driver-class-name=org.h2.Driver",
                     "--spring.datasource.username=sa",
                     "--spring.datasource.password=",
                     "--privacy.guard.audit.jdbc.dialect=H2",
                     "--privacy.guard.audit.dead-letter.jdbc.dialect=H2",
                     "--privacy.guard.audit.dead-letter.observability.alert.receiver.filter.path-pattern=/demo-alert-receiver-default",
                     "--privacy.guard.tenant.enabled=true",
                     "--privacy.guard.tenant.policies.tenant-a.observability.dead-letter.alert.receiver.path-pattern=" + TENANT_A_RECEIVER_PATH,
                     "--privacy.guard.tenant.policies.tenant-a.observability.dead-letter.alert.receiver.bearer-token=" + TENANT_A_RECEIVER_BEARER_TOKEN,
                     "--privacy.guard.tenant.policies.tenant-a.observability.dead-letter.alert.receiver.signature-secret=" + TENANT_A_RECEIVER_SIGNATURE_SECRET,
                     "--privacy.guard.tenant.policies.tenant-a.observability.dead-letter.alert.receiver.replay-namespace=" + TENANT_A_REPLAY_NAMESPACE
             )) {

            HttpClient client = HttpClient.newHttpClient();
            int node1Port = port(node1);
            int node2Port = port(node2);

            assertThat(getJsonObject(client, node1Port, "/demo-tenants/current", Map.of()).get("instanceId")).isEqualTo("node-1");
            assertThat(getJsonObject(client, node2Port, "/demo-tenants/current", Map.of()).get("instanceId")).isEqualTo("node-2");

            String payload = """
                    {
                      "state": "WARNING",
                      "total": 2
                    }
                    """;
            String timestamp = currentTimestamp();
            String nonce = "multi-node-tenant-a-route-" + java.util.UUID.randomUUID();
            String signature = signPayload(payload, timestamp, nonce, TENANT_A_RECEIVER_SIGNATURE_SECRET);

            HttpResponse<String> accepted = postJson(
                    client,
                    node1Port,
                    TENANT_A_RECEIVER_PATH,
                    payload,
                    Map.of(
                            "Authorization", "Bearer " + TENANT_A_RECEIVER_BEARER_TOKEN,
                            "X-Privacy-Alert-Timestamp", timestamp,
                            "X-Privacy-Alert-Nonce", nonce,
                            "X-Privacy-Alert-Signature", signature
                    )
            );
            assertThat(accepted.statusCode()).isEqualTo(202);

            Map<String, Object> replayStore = getJsonObject(
                    client,
                    node2Port,
                    "/demo-alert-receiver/replay-store?limit=20&offset=0",
                    Map.of(
                            "X-Demo-Admin-Token", "demo-admin-token",
                            "X-Privacy-Tenant", "tenant-a"
                    )
            );
            assertThat(replayStore.get("count")).isEqualTo(1);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) replayStore.get("entries");
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).get("storageKey")).isEqualTo(TENANT_A_REPLAY_NAMESPACE + ":" + nonce);

            HttpResponse<String> replayed = postJson(
                    client,
                    node2Port,
                    TENANT_A_RECEIVER_PATH,
                    payload,
                    Map.of(
                            "Authorization", "Bearer " + TENANT_A_RECEIVER_BEARER_TOKEN,
                            "X-Privacy-Alert-Timestamp", timestamp,
                            "X-Privacy-Alert-Nonce", nonce,
                            "X-Privacy-Alert-Signature", signature
                    )
            );
            assertThat(replayed.statusCode()).isEqualTo(409);
            Map<String, Object> replayedBody = OBJECT_MAPPER.readValue(replayed.body(), MAP_TYPE);
            assertThat(replayedBody.get("error")).isEqualTo("Replay detected");
            assertThat(replayedBody.get("reason")).isEqualTo("REPLAY_DETECTED");

            List<Map<String, Object>> auditEvents = getJsonArray(
                    client,
                    node2Port,
                    "/audit-events?action=DEMO_ALERT_REPLAY_STORE_QUERY&tenant=tenant-a&limit=1",
                    Map.of()
            );
            assertThat(auditEvents).hasSize(1);
            assertThat(auditEvents.get(0).get("resourceId")).isEqualTo("receiver");
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) auditEvents.get(0).get("details");
            assertThat(details.get("tenant")).isEqualTo("tenant-a");
        }
    }
}
