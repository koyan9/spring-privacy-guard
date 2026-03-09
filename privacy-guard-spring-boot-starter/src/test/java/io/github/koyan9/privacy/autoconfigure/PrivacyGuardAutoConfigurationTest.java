/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterHandler;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditEvent;
import io.github.koyan9.privacy.audit.PrivacyAuditJdbcDialect;
import io.github.koyan9.privacy.audit.PrivacyAuditPublisher;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryService;
import io.github.koyan9.privacy.audit.PrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditRepositoryType;
import io.github.koyan9.privacy.audit.PrivacyAuditSchemaInitializer;
import io.github.koyan9.privacy.audit.PrivacyAuditService;
import io.github.koyan9.privacy.core.MaskingContext;
import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.MaskingStrategy;
import io.github.koyan9.privacy.core.SensitiveData;
import io.github.koyan9.privacy.core.SensitiveType;
import io.github.koyan9.privacy.core.TextMaskingService;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import io.github.koyan9.privacy.logging.PrivacyLoggerFactory;
import io.github.koyan9.privacy.logging.logback.PrivacyLogbackConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PrivacyGuardAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PrivacyGuardAutoConfiguration.class));

    private final ApplicationContextRunner jacksonContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, PrivacyGuardAutoConfiguration.class));

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
    void createsInMemoryDeadLetterRepositoryWhenConfigured() {
        contextRunner
                .withPropertyValues("privacy.guard.audit.dead-letter.repository-type=IN_MEMORY")
                .run(context -> {
                    assertThat(context).hasSingleBean(InMemoryPrivacyAuditDeadLetterRepository.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterHandler.class);
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

    @Test
    void createsBufferedRepositoryPublisherWhenBatchingIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.repository-type=IN_MEMORY",
                        "privacy.guard.audit.batch.enabled=true",
                        "privacy.guard.audit.batch.size=5",
                        "privacy.guard.audit.batch.flush-interval=250ms"
                )
                .run(context -> {
                    assertThat(context).hasBean("privacyAuditExecutor");
                    assertThat(context.getBean("privacyAuditExecutor", ScheduledExecutorService.class)).isNotNull();
                    assertThat(context.getBean(PrivacyAuditPublisher.class)).isNotNull();
                });
    }

    @Test
    void wrapsAuditPublisherAsynchronouslyWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.async.enabled=true",
                        "privacy.guard.audit.async.thread-name-prefix=audit-worker-"
                )
                .run(context -> {
                    assertThat(context).hasBean("privacyAuditExecutor");
                    assertThat(context.getBean("privacyAuditExecutor", ScheduledExecutorService.class)).isNotNull();
                    assertThat(context.getBean(PrivacyAuditPublisher.class)).isNotNull();
                });
    }

    @Test
    void usesCustomMaskingStrategyBeanWhenProvided() {
        contextRunner
                .withUserConfiguration(CustomStrategyConfig.class)
                .run(context -> {
                    MaskingService maskingService = context.getBean(MaskingService.class);

                    assertThat(maskingService.mask("Alice", SensitiveType.NAME)).isEqualTo("[bean]Alice");
                });
    }

    @Test
    void registersJacksonModuleIntoBootObjectMapper() throws Exception {
        jacksonContextRunner.run(context -> {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

            String json = objectMapper.writeValueAsString(new PatientView("Alice", "13800138000", "normal"));

            assertThat(json).contains("\"patientName\":\"A***e\"");
            assertThat(json).contains("\"phone\":\"138****8000\"");
            assertThat(json).contains("\"note\":\"normal\"");
        });
    }

    @Test
    void bindsNestedPropertiesFromEnvironment() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.fallback-mask-char=#",
                        "privacy.guard.logging.enabled=false",
                        "privacy.guard.logging.logback.install-turbo-filter=true",
                        "privacy.guard.logging.logback.block-unsafe-messages=false",
                        "privacy.guard.audit.enabled=true",
                        "privacy.guard.audit.log-events=false",
                        "privacy.guard.audit.repository-type=JDBC",
                        "privacy.guard.audit.async.enabled=true",
                        "privacy.guard.audit.async.thread-name-prefix=audit-worker-",
                        "privacy.guard.audit.batch.enabled=true",
                        "privacy.guard.audit.batch.size=25",
                        "privacy.guard.audit.batch.flush-interval=750ms",
                        "privacy.guard.audit.retry.max-attempts=5",
                        "privacy.guard.audit.retry.backoff=250ms",
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.audit.jdbc.initialize-schema=true",
                        "privacy.guard.audit.jdbc.table-name=audit_log",
                        "privacy.guard.audit.jdbc.schema-location=classpath:META-INF/privacy-guard/privacy-audit-schema-h2.sql",
                        "privacy.guard.audit.jdbc.dialect=H2"
                )
                .withUserConfiguration(JdbcConfig.class)
                .run(context -> {
                    PrivacyGuardProperties properties = context.getBean(PrivacyGuardProperties.class);

                    assertThat(properties.getFallbackMaskChar()).isEqualTo("#");
                    assertThat(properties.getLogging().isEnabled()).isFalse();
                    assertThat(properties.getLogging().getLogback().isInstallTurboFilter()).isTrue();
                    assertThat(properties.getLogging().getLogback().isBlockUnsafeMessages()).isFalse();
                    assertThat(properties.getAudit().isEnabled()).isTrue();
                    assertThat(properties.getAudit().isLogEvents()).isFalse();
                    assertThat(properties.getAudit().getRepositoryType()).isEqualTo(PrivacyAuditRepositoryType.JDBC);
                    assertThat(properties.getAudit().getAsync().isEnabled()).isTrue();
                    assertThat(properties.getAudit().getAsync().getThreadNamePrefix()).isEqualTo("audit-worker-");
                    assertThat(properties.getAudit().getBatch().isEnabled()).isTrue();
                    assertThat(properties.getAudit().getBatch().getSize()).isEqualTo(25);
                    assertThat(properties.getAudit().getBatch().getFlushInterval()).hasToString("PT0.75S");
                    assertThat(properties.getAudit().getRetry().getMaxAttempts()).isEqualTo(5);
                    assertThat(properties.getAudit().getRetry().getBackoff()).hasToString("PT0.25S");
                    assertThat(properties.getAudit().getDeadLetter().getRepositoryType()).isEqualTo(PrivacyAuditRepositoryType.IN_MEMORY);
                    assertThat(properties.getAudit().getJdbc().isInitializeSchema()).isTrue();
                    assertThat(properties.getAudit().getJdbc().getTableName()).isEqualTo("audit_log");
                    assertThat(properties.getAudit().getJdbc().getSchemaLocation())
                            .isEqualTo("classpath:META-INF/privacy-guard/privacy-audit-schema-h2.sql");
                    assertThat(properties.getAudit().getJdbc().getDialect()).isEqualTo(PrivacyAuditJdbcDialect.H2);
                });
    }

    @Test
    void initializesConfiguredJdbcSchemaWithBoundTableName() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.repository-type=JDBC",
                        "privacy.guard.audit.jdbc.initialize-schema=true",
                        "privacy.guard.audit.jdbc.schema-location=classpath:META-INF/privacy-guard/privacy-audit-schema-h2.sql",
                        "privacy.guard.audit.jdbc.table-name=audit_log"
                )
                .withUserConfiguration(JdbcConfig.class)
                .run(context -> {
                    JdbcOperations jdbcOperations = context.getBean(JdbcOperations.class);

                    assertThat(context).hasSingleBean(PrivacyAuditSchemaInitializer.class);
                    verify(jdbcOperations).execute(contains("create table if not exists audit_log"));
                });
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

    @Configuration(proxyBeanMethods = false)
    static class CustomStrategyConfig {

        @Bean
        MaskingStrategy customNameMaskingStrategy() {
            return new OrderedNameMaskingStrategy();
        }
    }

    static class OrderedNameMaskingStrategy implements MaskingStrategy, Ordered {

        @Override
        public boolean supports(MaskingContext context) {
            return context.sensitiveType() == SensitiveType.NAME;
        }

        @Override
        public String mask(String value, MaskingContext context) {
            return "[bean]" + value;
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }

    static class CapturingRepository implements PrivacyAuditRepository {

        private final List<PrivacyAuditEvent> events = new ArrayList<>();

        @Override
        public void save(PrivacyAuditEvent event) {
            events.add(event);
        }
    }

    static class PatientView {

        @SensitiveData(type = SensitiveType.NAME)
        private final String patientName;

        @SensitiveData(type = SensitiveType.PHONE)
        private final String phone;

        private final String note;

        PatientView(String patientName, String phone, String note) {
            this.patientName = patientName;
            this.phone = phone;
            this.note = note;
        }

        public String getPatientName() {
            return patientName;
        }

        public String getPhone() {
            return phone;
        }

        public String getNote() {
            return note;
        }
    }
}
