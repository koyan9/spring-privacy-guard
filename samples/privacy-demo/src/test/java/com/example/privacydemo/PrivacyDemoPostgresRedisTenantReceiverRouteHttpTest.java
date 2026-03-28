/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:privacy-demo-postgres-redis-route;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "privacy.guard.audit.jdbc.dialect=H2",
        "privacy.guard.audit.dead-letter.jdbc.dialect=H2",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("postgres-redis-tenant")
@Import(PrivacyDemoPostgresRedisTenantReceiverRouteHttpTest.RedisReceiverRouteConfig.class)
@Tag("sample")
@Tag("sample-postgres-redis")
@Tag("sample-receiver")
class PrivacyDemoPostgresRedisTenantReceiverRouteHttpTest extends SampleJdbcHttpTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcOperations jdbcOperations;

    @Autowired
    private PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    @Autowired
    private DemoWebhookAlertReceiverStore receiverStore;

    @BeforeEach
    void resetState() {
        resetJdbcState(jdbcOperations, replayStore, POSTGRES_SAMPLE_AUDIT_TABLE, POSTGRES_SAMPLE_DEAD_LETTER_TABLE, null);
        receiverStore.clear();
    }

    @Test
    void acceptsTenantSpecificReceiverRouteAndKeepsRouteSpecificReplayKey() throws Exception {
        String payload = """
                {
                  "state": "WARNING",
                  "total": 2
                }
                """;
        String timestamp = currentTimestamp();
        String nonce = "tenant-a-postgres-redis-route";
        String signature = signPayload(payload, timestamp, nonce, TENANT_A_RECEIVER_SIGNATURE_SECRET);

        mockMvc.perform(post(TENANT_A_RECEIVER_PATH)
                        .contentType(APPLICATION_JSON)
                        .headers(httpHeaders(receiverHeaders(TENANT_A_RECEIVER_BEARER_TOKEN, timestamp, nonce, signature)))
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.verified").value(true));

        mockMvc.perform(get("/demo-tenants/observability")
                        .headers(httpHeaders(adminHeaders())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiverReplayStore.backend").value("REDIS"))
                .andExpect(jsonPath("$.receiverReplayStore.namespace").value("demo-postgres-redis-shared"))
                .andExpect(jsonPath("$.receiverReplayStore.redisKeyPrefix").value("privacy:demo:postgres:webhook:replay:"));

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
    void rejectsReplayNonceOnTenantSpecificReceiverRoute() throws Exception {
        String payload = """
                {
                  "state": "WARNING"
                }
                """;
        String timestamp = currentTimestamp();
        String nonce = "tenant-a-postgres-redis-replay";
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

        assertThat(receiverStore.lastReceived()).isPresent();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisReceiverRouteConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean
        PrivacyAuditDeadLetterWebhookReplayStore privacyAuditDeadLetterWebhookReplayStore() {
            return new InMemoryPrivacyAuditDeadLetterWebhookReplayStore();
        }
    }
}
