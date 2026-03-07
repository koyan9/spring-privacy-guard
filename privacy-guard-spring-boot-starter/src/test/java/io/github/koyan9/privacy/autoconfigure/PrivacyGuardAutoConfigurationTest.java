/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditEvent;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryService;
import io.github.koyan9.privacy.audit.PrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditSchemaInitializer;
import io.github.koyan9.privacy.audit.PrivacyAuditService;
import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.TextMaskingService;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import io.github.koyan9.privacy.logging.PrivacyLoggerFactory;
import io.github.koyan9.privacy.logging.logback.PrivacyLogbackConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PrivacyGuardAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PrivacyGuardAutoConfiguration.class));

    @Test
    void registersBeansWhenEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MaskingService.class);
            assertThat(context).hasSingleBean(TextMaskingService.class);
            assertThat(context).hasSingleBean(PrivacyLogSanitizer.class);
            assertThat(context).hasSingleBean(PrivacyLoggerFactory.class);
            assertThat(context).hasSingleBean(PrivacyLogbackConfigurer.class);
            assertThat(context).hasSingleBean(PrivacyAuditService.class);
            assertThat(context).hasSingleBean(PrivacyAuditQueryService.class);
            assertThat(context).hasBean("privacyGuardJacksonModule");
        });
    }

    @Test
    void backsOffWhenDisabled() {
        contextRunner
                .withPropertyValues("privacy.guard.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MaskingService.class);
                    assertThat(context).doesNotHaveBean(PrivacyLogSanitizer.class);
                    assertThat(context).doesNotHaveBean(PrivacyAuditService.class);
                    assertThat(context).doesNotHaveBean("privacyGuardJacksonModule");
                });
    }

    @Test
    void allowsAuditToBeDisabledSeparately() {
        contextRunner
                .withPropertyValues("privacy.guard.audit.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PrivacyAuditService.class));
    }

    @Test
    void wiresRepositoryWhenProvided() {
        contextRunner
                .withUserConfiguration(RepositoryConfig.class)
                .run(context -> {
                    PrivacyAuditService service = context.getBean(PrivacyAuditService.class);
                    CapturingRepository repository = (CapturingRepository) context.getBean(PrivacyAuditRepository.class);

                    service.record("READ", "Patient", "13800138000", "alice@example.com", "OK", java.util.Map.of("phone", "13800138000"));
                    assertThat(repository.events).hasSize(1);
                    assertThat(repository.events.get(0).resourceId()).isEqualTo("138****8000");
                });
    }

    @Test
    void createsInMemoryRepositoryWhenConfigured() {
        contextRunner
                .withPropertyValues("privacy.guard.audit.repository-type=IN_MEMORY")
                .run(context -> {
                    assertThat(context).hasSingleBean(InMemoryPrivacyAuditRepository.class);
                    assertThat(context).hasSingleBean(PrivacyAuditQueryService.class);
                });
    }

    @Test
    void createsJdbcRepositoryWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.repository-type=JDBC",
                        "privacy.guard.audit.jdbc.table-name=audit_log"
                )
                .withUserConfiguration(JdbcConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivacyAuditRepository.class);
                    assertThat(context).hasSingleBean(PrivacyAuditQueryService.class);
                });
    }

    @Test
    void createsSchemaInitializerWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.repository-type=JDBC",
                        "privacy.guard.audit.jdbc.initialize-schema=true"
                )
                .withUserConfiguration(JdbcConfig.class)
                .run(context -> assertThat(context).hasSingleBean(PrivacyAuditSchemaInitializer.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class RepositoryConfig {

        @Bean
        PrivacyAuditRepository privacyAuditRepository() {
            return new CapturingRepository();
        }
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

    static class CapturingRepository implements PrivacyAuditRepository {

        private final List<PrivacyAuditEvent> events = new ArrayList<>();

        @Override
        public void save(PrivacyAuditEvent event) {
            events.add(event);
        }
    }
}