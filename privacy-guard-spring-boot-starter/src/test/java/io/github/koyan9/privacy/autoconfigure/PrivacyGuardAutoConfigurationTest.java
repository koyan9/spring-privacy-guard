/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.JdbcPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterHandler;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditEvent;
import io.github.koyan9.privacy.audit.PrivacyAuditJdbcDialect;
import io.github.koyan9.privacy.audit.PrivacyAuditPublisher;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditQueryService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterOperationsService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterQueryService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterReplayRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditReadRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditWriteRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterObservabilityPolicyResolver;
import io.github.koyan9.privacy.audit.PrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditRepositoryType;
import io.github.koyan9.privacy.audit.PrivacyAuditSchemaInitializer;
import io.github.koyan9.privacy.audit.PrivacyAuditService;
import io.github.koyan9.privacy.core.MaskingContext;
import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.MaskingStrategy;
import io.github.koyan9.privacy.core.PrivacyTenantAwareMaskingStrategy;
import io.github.koyan9.privacy.core.PrivacyTenantContextHolder;
import io.github.koyan9.privacy.core.PrivacyTenantContextScope;
import io.github.koyan9.privacy.core.PrivacyTenantPolicyResolver;
import io.github.koyan9.privacy.core.PrivacyTenantProvider;
import io.github.koyan9.privacy.core.SensitiveData;
import io.github.koyan9.privacy.core.SensitiveType;
import io.github.koyan9.privacy.core.TextMaskingService;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import io.github.koyan9.privacy.logging.PrivacyLoggerFactory;
import io.github.koyan9.privacy.logging.PrivacyTenantLoggingPolicyResolver;
import io.github.koyan9.privacy.logging.logback.PrivacyLogbackConfigurer;
import io.github.koyan9.privacy.logging.logback.PrivacyLogbackRuntime;
import io.github.koyan9.privacy.tenant.PrivacyTenantContextFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcOperations;
import org.slf4j.event.KeyValuePair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PrivacyGuardAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PrivacyGuardAutoConfiguration.class));

    private final ApplicationContextRunner jacksonContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, PrivacyGuardAutoConfiguration.class));

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PrivacyGuardAutoConfiguration.class));

    @Test
    void registersBeansWhenEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MaskingService.class);
            assertThat(context).hasSingleBean(TextMaskingService.class);
            assertThat(context).hasSingleBean(PrivacyLogSanitizer.class);
            assertThat(context).hasSingleBean(PrivacyLoggerFactory.class);
            assertThat(context).hasSingleBean(PrivacyLogbackConfigurer.class);
            assertThat(context).hasSingleBean(PrivacyTenantProvider.class);
            assertThat(context).hasSingleBean(PrivacyTenantPolicyResolver.class);
            assertThat(context).hasSingleBean(PrivacyTenantDeadLetterObservabilityPolicyResolver.class);
            assertThat(context).hasSingleBean(PrivacyTenantLoggingPolicyResolver.class);
            assertThat(context).hasSingleBean(PrivacyAuditService.class);
            assertThat(context).hasSingleBean(PrivacyAuditQueryService.class);
            assertThat(context).hasSingleBean(PrivacyTenantAuditQueryService.class);
            assertThat(context).hasBean("privacyGuardJacksonModule");
        });
    }

    @Test
    void overridesTextMaskingPatternsFromProperties() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.masking.text.email-pattern=foo@bar.com",
                        "privacy.guard.masking.text.additional-patterns[0].type=GENERIC",
                        "privacy.guard.masking.text.additional-patterns[0].pattern=EMP\\d{4}"
                )
                .run(context -> {
                    TextMaskingService textMaskingService = context.getBean(TextMaskingService.class);
                    String sanitized = textMaskingService.sanitize("email=alice@example.com id=EMP1234");
                    assertThat(sanitized).contains("alice@example.com");
                    assertThat(sanitized).doesNotContain("EMP1234");
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
                    assertThat(context).hasSingleBean(PrivacyTenantAuditDeadLetterQueryService.class);
                    assertThat(context).hasSingleBean(PrivacyTenantAuditDeadLetterOperationsService.class);
                    assertThat(context).hasSingleBean(PrivacyTenantAuditDeadLetterReplayRepository.class);
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
                    assertThat(context).hasSingleBean(JdbcPrivacyAuditRepository.class);
                    assertThat(context).hasSingleBean(PrivacyAuditRepository.class);
                    assertThat(context).hasSingleBean(PrivacyTenantAuditReadRepository.class);
                    assertThat(context).hasSingleBean(PrivacyTenantAuditWriteRepository.class);
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
                        "privacy.guard.audit.async.thread-name-prefix=audit-worker-",
                        "privacy.guard.audit.async.thread-pool-size=3"
                )
                .run(context -> {
                    assertThat(context).hasBean("privacyAuditExecutor");
                    ScheduledExecutorService executor = context.getBean("privacyAuditExecutor", ScheduledExecutorService.class);
                    assertThat(executor).isNotNull();
                    assertThat(executor).isInstanceOf(ScheduledThreadPoolExecutor.class);
                    assertThat(((ScheduledThreadPoolExecutor) executor).getCorePoolSize()).isEqualTo(3);
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
    void appliesTenantPolicyFromProperties() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.default-tenant=tenant-a",
                        "privacy.guard.tenant.policies[tenant-a].fallback-mask-char=#",
                        "privacy.guard.tenant.policies[tenant-a].text.additional-patterns[0].type=GENERIC",
                        "privacy.guard.tenant.policies[tenant-a].text.additional-patterns[0].pattern=EMP\\d{4}"
                )
                .run(context -> {
                    MaskingService maskingService = context.getBean(MaskingService.class);
                    TextMaskingService textMaskingService = context.getBean(TextMaskingService.class);

                    assertThat(maskingService.mask("13800138000", SensitiveType.PHONE)).isEqualTo("138####8000");
                    assertThat(textMaskingService.sanitize("id=EMP1234 phone=13800138000"))
                            .contains("id=E#####4")
                            .contains("phone=138####8000");
                });
    }

    @Test
    void appliesTenantAuditPolicyFromProperties() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.default-tenant=tenant-a",
                        "privacy.guard.tenant.policies[tenant-a].fallback-mask-char=#",
                        "privacy.guard.tenant.policies[tenant-a].text.additional-patterns[0].type=GENERIC",
                        "privacy.guard.tenant.policies[tenant-a].text.additional-patterns[0].pattern=EMP\\d{4}",
                        "privacy.guard.tenant.policies[tenant-a].audit.include-detail-keys[0]=phone",
                        "privacy.guard.tenant.policies[tenant-a].audit.include-detail-keys[1]=employeeCode",
                        "privacy.guard.tenant.policies[tenant-a].audit.exclude-detail-keys[0]=idCard",
                        "privacy.guard.tenant.policies[tenant-a].audit.attach-tenant-id=true",
                        "privacy.guard.tenant.policies[tenant-a].audit.tenant-detail-key=tenant"
                )
                .withUserConfiguration(RepositoryConfig.class)
                .run(context -> {
                    PrivacyAuditService service = context.getBean(PrivacyAuditService.class);
                    CapturingRepository repository = (CapturingRepository) context.getBean(PrivacyAuditRepository.class);

                    service.record(
                            "READ",
                            "Patient",
                            "13800138000",
                            "alice@example.com",
                            "OK",
                            java.util.Map.of(
                                    "phone", "13800138000",
                                    "idCard", "110101199001011234",
                                    "employeeCode", "EMP1234"
                            )
                    );

                    assertThat(repository.events).hasSize(1);
                    assertThat(repository.events.get(0).details())
                            .containsEntry("phone", "138####8000")
                            .containsEntry("employeeCode", "E#####4")
                            .containsEntry("tenant", "tenant-a")
                            .doesNotContainKey("idCard");
                });
    }

    @Test
    void appliesTenantLoggingPolicyFromProperties() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.logging.mdc.enabled=true",
                        "privacy.guard.logging.mdc.include-keys[0]=email",
                        "privacy.guard.logging.structured.enabled=true",
                        "privacy.guard.logging.structured.include-keys[0]=phone",
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.default-tenant=public",
                        "privacy.guard.tenant.policies[tenant-a].logging.mdc.include-keys[0]=phone",
                        "privacy.guard.tenant.policies[tenant-a].logging.structured.enabled=false"
                )
                .run(context -> {
                    PrivacyTenantLoggingPolicyResolver resolver = context.getBean(PrivacyTenantLoggingPolicyResolver.class);
                    try (PrivacyTenantContextScope ignored = PrivacyTenantContextHolder.openScope("tenant-a")) {
                        java.util.Map<String, String> tenantMdc = PrivacyLogbackRuntime.sanitizeMdc(java.util.Map.of(
                                "email", "alice@example.com",
                                "phone", "13800138000"
                        ));
                        assertThat(tenantMdc.get("email")).isEqualTo("alice@example.com");
                        assertThat(tenantMdc.get("phone")).contains("138****8000");

                        java.util.List<KeyValuePair> tenantPairs = PrivacyLogbackRuntime.sanitizeKeyValuePairs(java.util.List.of(
                                new KeyValuePair("phone", "13800138000")
                        ));
                        assertThat(tenantPairs.get(0).value.toString()).isEqualTo("13800138000");
                    } finally {
                        PrivacyTenantContextHolder.clear();
                    }

                    assertThat(resolver.resolve("tenant-a").mdcIncludeKeys()).containsExactly("phone");
                    assertThat(resolver.resolve("tenant-a").structuredEnabled()).isFalse();
                    assertThat(resolver.resolve("tenant-b")).isEqualTo(io.github.koyan9.privacy.logging.PrivacyTenantLoggingPolicy.none());

                    java.util.Map<String, String> defaultMdc = PrivacyLogbackRuntime.sanitizeMdc(java.util.Map.of(
                            "email", "alice@example.com",
                            "phone", "13800138000"
                    ));
                    assertThat(defaultMdc.get("email")).contains("a****@example.com");
                    assertThat(defaultMdc.get("phone")).isEqualTo("13800138000");
                });
    }

    @Test
    void registersTenantContextFilterWhenTenantModeEnabled() {
        webContextRunner
                .withPropertyValues(
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.header-name=X-Privacy-Tenant"
                )
                .run(context -> assertThat(context).hasSingleBean(PrivacyTenantContextFilter.class));
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
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.header-name=X-Privacy-Tenant",
                        "privacy.guard.tenant.default-tenant=tenant-a",
                        "privacy.guard.tenant.policies[tenant-a].fallback-mask-char=#",
                        "privacy.guard.tenant.policies[tenant-a].text.phone-pattern=9\\d{5}",
                        "privacy.guard.tenant.policies[tenant-a].audit.include-detail-keys[0]=phone",
                        "privacy.guard.tenant.policies[tenant-a].audit.exclude-detail-keys[0]=idCard",
                        "privacy.guard.tenant.policies[tenant-a].audit.attach-tenant-id=true",
                        "privacy.guard.tenant.policies[tenant-a].audit.tenant-detail-key=tenant",
                        "privacy.guard.tenant.policies[tenant-a].observability.dead-letter.warning-threshold=2",
                        "privacy.guard.tenant.policies[tenant-a].observability.dead-letter.down-threshold=4",
                        "privacy.guard.tenant.policies[tenant-a].observability.dead-letter.notify-on-recovery=false",
                        "privacy.guard.tenant.policies[tenant-a].logging.mdc.enabled=true",
                        "privacy.guard.tenant.policies[tenant-a].logging.mdc.include-keys[0]=email",
                        "privacy.guard.tenant.policies[tenant-a].logging.structured.enabled=false",
                        "privacy.guard.tenant.policies[tenant-a].logging.structured.exclude-keys[0]=safe",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.tenant-ids[0]=tenant-a",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.routes[tenant-a].webhook.url=https://tenant-a.example.com/alerts",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.routes[tenant-a].email.to=tenant-a-ops@example.com",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.routes[tenant-a].email.subject-prefix=[tenant-a]",
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
                        "privacy.guard.audit.jdbc.tenant-column-name=tenant_id",
                        "privacy.guard.audit.jdbc.tenant-detail-key=tenant",
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
                    assertThat(properties.getTenant().isEnabled()).isTrue();
                    assertThat(properties.getTenant().getHeaderName()).isEqualTo("X-Privacy-Tenant");
                    assertThat(properties.getTenant().getDefaultTenant()).isEqualTo("tenant-a");
                    assertThat(properties.getTenant().getPolicies()).containsKey("tenant-a");
                    assertThat(properties.getTenant().getPolicies().get("tenant-a").getFallbackMaskChar()).isEqualTo("#");
                    assertThat(properties.getTenant().getPolicies().get("tenant-a").getText().getPhonePattern()).isEqualTo("9\\d{5}");
                    assertThat(properties.getTenant().getPolicies().get("tenant-a").getAudit().getIncludeDetailKeys()).containsExactly("phone");
                    assertThat(properties.getTenant().getPolicies().get("tenant-a").getAudit().getExcludeDetailKeys()).containsExactly("idCard");
                    assertThat(properties.getTenant().getPolicies().get("tenant-a").getAudit().isAttachTenantId()).isTrue();
                    assertThat(properties.getTenant().getPolicies().get("tenant-a").getAudit().getTenantDetailKey()).isEqualTo("tenant");
                    assertThat(properties.getTenant().getPolicies().get("tenant-a").getObservability().getDeadLetter().getWarningThreshold()).isEqualTo(2L);
                    assertThat(properties.getTenant().getPolicies().get("tenant-a").getObservability().getDeadLetter().getDownThreshold()).isEqualTo(4L);
                    assertThat(properties.getTenant().getPolicies().get("tenant-a").getObservability().getDeadLetter().getNotifyOnRecovery()).isFalse();
                    assertThat(properties.getTenant().getPolicies().get("tenant-a").getLogging().getMdc().getEnabled()).isTrue();
                    assertThat(properties.getTenant().getPolicies().get("tenant-a").getLogging().getMdc().getIncludeKeys()).containsExactly("email");
                    assertThat(properties.getTenant().getPolicies().get("tenant-a").getLogging().getStructured().getEnabled()).isFalse();
                    assertThat(properties.getTenant().getPolicies().get("tenant-a").getLogging().getStructured().getExcludeKeys()).containsExactly("safe");
                    assertThat(properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().isEnabled()).isTrue();
                    assertThat(properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().getTenantIds()).containsExactly("tenant-a");
                    assertThat(properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().getRoutes())
                            .containsKey("tenant-a");
                    assertThat(properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().getRoutes().get("tenant-a").getWebhook().getUrl())
                            .isEqualTo("https://tenant-a.example.com/alerts");
                    assertThat(properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().getRoutes().get("tenant-a").getEmail().getTo())
                            .isEqualTo("tenant-a-ops@example.com");
                    assertThat(properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().getRoutes().get("tenant-a").getEmail().getSubjectPrefix())
                            .isEqualTo("[tenant-a]");
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
                    assertThat(properties.getAudit().getJdbc().getTenantColumnName()).isEqualTo("tenant_id");
                    assertThat(properties.getAudit().getJdbc().getTenantDetailKey()).isEqualTo("tenant");
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
                        "privacy.guard.audit.jdbc.tenant-column-name=tenant_key",
                        "privacy.guard.audit.jdbc.schema-location=classpath:META-INF/privacy-guard/privacy-audit-schema-h2.sql",
                        "privacy.guard.audit.jdbc.table-name=audit_log"
                )
                .withUserConfiguration(JdbcConfig.class)
                .run(context -> {
                    JdbcOperations jdbcOperations = context.getBean(JdbcOperations.class);

                    assertThat(context).hasSingleBean(PrivacyAuditSchemaInitializer.class);
                    verify(jdbcOperations).execute(contains("create table if not exists audit_log"));
                    verify(jdbcOperations).execute(contains("tenant_key varchar(255)"));
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

    static class OrderedNameMaskingStrategy implements PrivacyTenantAwareMaskingStrategy, Ordered {

        @Override
        public boolean supports(String tenantId, MaskingContext context) {
            return context.sensitiveType() == SensitiveType.NAME;
        }

        @Override
        public String mask(String tenantId, String value, MaskingContext context) {
            return tenantId == null ? "[bean]" + value : "[bean:" + tenantId + "]" + value;
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
