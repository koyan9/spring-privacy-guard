/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterCsvCodec;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterExchangeService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterHandler;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterStatsService;
import io.github.koyan9.privacy.audit.PrivacyAuditSchemaInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PrivacyGuardDeadLetterAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    PrivacyGuardAutoConfiguration.class,
                    PrivacyGuardDeadLetterExchangeAutoConfiguration.class
            ));

    private final ApplicationContextRunner unorderedContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PrivacyGuardDeadLetterExchangeAutoConfiguration.class,
                    PrivacyGuardAutoConfiguration.class,
                    JacksonAutoConfiguration.class
            ));

    @Test
    void createsInMemoryDeadLetterRepositoryHandlerAndRelatedServices() {
        contextRunner
                .withPropertyValues("privacy.guard.audit.dead-letter.repository-type=IN_MEMORY")
                .run(context -> {
                    assertThat(context).hasSingleBean(InMemoryPrivacyAuditDeadLetterRepository.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterHandler.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterService.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterStatsService.class);
                });
    }

    @Test
    void createsExchangeServiceWhenAutoConfigurationsAreDeclaredOutOfOrder() {
        unorderedContextRunner
                .withPropertyValues("privacy.guard.audit.dead-letter.repository-type=IN_MEMORY")
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterCsvCodec.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterExchangeService.class);
                });
    }

    @Test
    void createsJdbcDeadLetterRepositoryAndSchemaInitializer() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=JDBC",
                        "privacy.guard.audit.dead-letter.jdbc.initialize-schema=true",
                        "privacy.guard.audit.dead-letter.jdbc.table-name=privacy_audit_dead_letter_log",
                        "privacy.guard.audit.dead-letter.jdbc.schema-location=classpath:META-INF/privacy-guard/privacy-audit-dead-letter-schema-h2.sql"
                )
                .withUserConfiguration(JdbcConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterRepository.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterHandler.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterService.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterStatsService.class);
                    assertThat(context).hasSingleBean(PrivacyAuditSchemaInitializer.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcConfig {

        @Bean
        JdbcOperations jdbcOperations() {
            return mock(JdbcOperations.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
