/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.RedisPrivacyAuditDeadLetterWebhookReplayStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:privacy-demo-postgres-redis;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "privacy.guard.audit.jdbc.dialect=H2",
        "privacy.guard.audit.dead-letter.jdbc.dialect=H2",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("postgres-redis-tenant")
@Import(PrivacyDemoPostgresRedisTenantProfileTest.RedisConfig.class)
@Tag("sample")
@Tag("sample-postgres-redis")
class PrivacyDemoPostgresRedisTenantProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    @Test
    void exposesProductionLikePostgresRedisProfile() throws Exception {
        assertThat(replayStore).isInstanceOf(RedisPrivacyAuditDeadLetterWebhookReplayStore.class);

        mockMvc.perform(get("/demo-tenants/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("postgres-redis-node-1"));

        mockMvc.perform(get("/demo-tenants/policies")
                        .header("X-Demo-Admin-Token", "demo-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].deadLetterAlertReceiverPathPattern")
                        .value(hasItem("/demo-alert-receiver/tenant-a")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-a')].deadLetterAlertReceiverReplayNamespace")
                        .value(hasItem("tenant-a-receiver")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-b')].deadLetterAlertReceiverPathPattern")
                        .value(hasItem("/demo-alert-receiver/tenant-b")))
                .andExpect(jsonPath("$.tenants[?(@.tenantId=='tenant-b')].deadLetterAlertReceiverReplayNamespace")
                        .value(hasItem("tenant-b-receiver")));

        mockMvc.perform(get("/demo-tenants/observability")
                        .header("X-Demo-Admin-Token", "demo-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("postgres-redis-node-1"))
                .andExpect(jsonPath("$.receiverReplayStore.backend").value("REDIS"))
                .andExpect(jsonPath("$.receiverReplayStore.namespace").value("demo-postgres-redis-shared"))
                .andExpect(jsonPath("$.receiverReplayStore.redisKeyPrefix").value("privacy:demo:postgres:webhook:replay:"))
                .andExpect(jsonPath("$.auditRepositoryType").value("JDBC"))
                .andExpect(jsonPath("$.deadLetterRepositoryType").value("JDBC"))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantFindByIdNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantDeleteByIdNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantReplayByIdNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantExchangeReadNative").value(true))
                .andExpect(jsonPath("$.repositoryCapabilities.deadLetter.tenantImportNative").value(true))
                .andExpect(jsonPath("$.readPaths.deadLetterExport.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.readPaths.deadLetterManifest.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterImport.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterDeleteById.native").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.writePaths.deadLetterReplayById.native").value(greaterThanOrEqualTo(0.0)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }
}
