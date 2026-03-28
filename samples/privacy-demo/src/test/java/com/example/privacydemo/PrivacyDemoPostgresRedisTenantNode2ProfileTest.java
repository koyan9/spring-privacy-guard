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
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:privacy-demo-postgres-redis-node2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "privacy.guard.audit.jdbc.dialect=H2",
        "privacy.guard.audit.dead-letter.jdbc.dialect=H2",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles({"postgres-redis-tenant", "postgres-redis-tenant-node2"})
@Import(PrivacyDemoPostgresRedisTenantNode2ProfileTest.RedisConfig.class)
@Tag("sample")
@Tag("sample-postgres-redis")
@Tag("sample-multi-instance")
class PrivacyDemoPostgresRedisTenantNode2ProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    @Test
    void exposesSecondProductionLikeInstanceMetadata() throws Exception {
        assertThat(replayStore).isInstanceOf(RedisPrivacyAuditDeadLetterWebhookReplayStore.class);

        mockMvc.perform(get("/demo-tenants/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("postgres-redis-node-2"));

        mockMvc.perform(get("/demo-tenants/observability")
                        .header("X-Demo-Admin-Token", "demo-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("postgres-redis-node-2"))
                .andExpect(jsonPath("$.receiverReplayStore.backend").value("REDIS"))
                .andExpect(jsonPath("$.auditRepositoryType").value("JDBC"))
                .andExpect(jsonPath("$.deadLetterRepositoryType").value("JDBC"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }
}
