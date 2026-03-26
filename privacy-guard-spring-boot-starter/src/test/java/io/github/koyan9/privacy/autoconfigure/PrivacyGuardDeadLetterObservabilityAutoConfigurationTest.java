/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.LoggingPrivacyAuditDeadLetterAlertCallback;
import io.github.koyan9.privacy.audit.LoggingPrivacyTenantAuditDeadLetterAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterAlertMonitor;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterBacklogState;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterEmailAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterEntry;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterObservationService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterObservationService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterAlertMonitor;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterHealthIndicator;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterMetricsBinder;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryCriteria;
import io.github.koyan9.privacy.audit.PrivacyAuditService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditQueryService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditTelemetry;
import io.github.koyan9.privacy.core.PrivacyTenantContextHolder;
import io.github.koyan9.privacy.core.PrivacyTenantContextScope;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PrivacyGuardDeadLetterObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    PrivacyGuardAutoConfiguration.class,
                    PrivacyGuardDeadLetterExchangeAutoConfiguration.class,
                    PrivacyGuardDeadLetterObservabilityAutoConfiguration.class,
                    PrivacyGuardTenantObservabilityAutoConfiguration.class
            ));

    @Test
    void exposesObservationServiceForDeadLetters() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.audit.dead-letter.observability.health.warning-threshold=1",
                        "privacy.guard.audit.dead-letter.observability.health.down-threshold=3"
                )
                .run(context -> {
                    InMemoryPrivacyAuditDeadLetterRepository repository = context.getBean(InMemoryPrivacyAuditDeadLetterRepository.class);
                    repository.save(entry("dead-letter-a"));
                    repository.save(entry("dead-letter-b"));

                    PrivacyAuditDeadLetterObservationService observationService = context.getBean(PrivacyAuditDeadLetterObservationService.class);
                    assertThat(observationService.currentSnapshot().state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.WARNING);
                    assertThat(observationService.currentSnapshot().total()).isEqualTo(2L);
                    assertThat(context).hasSingleBean(PrivacyTenantAuditDeadLetterObservationService.class);
                });
    }

    @Test
    void appliesConfiguredThresholdsToObservationService() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.audit.dead-letter.observability.health.warning-threshold=1",
                        "privacy.guard.audit.dead-letter.observability.health.down-threshold=2"
                )
                .run(context -> {
                    InMemoryPrivacyAuditDeadLetterRepository repository = context.getBean(InMemoryPrivacyAuditDeadLetterRepository.class);
                    repository.save(entry("dead-letter-a"));
                    repository.save(entry("dead-letter-b"));

                    PrivacyAuditDeadLetterObservationService observationService = context.getBean(PrivacyAuditDeadLetterObservationService.class);
                    assertThat(observationService.currentSnapshot().state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.DOWN);
                    assertThat(observationService.currentSnapshot().downThreshold()).isEqualTo(2L);
                });
    }

    @Test
    void createsAlertMonitorWithDefaultLoggingCallbackWhenAlertingEnabled() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.audit.dead-letter.observability.alert.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.check-interval=10ms"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(LoggingPrivacyAuditDeadLetterAlertCallback.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterAlertMonitor.class);
                    assertThat(context).hasBean("privacyAuditDeadLetterAlertExecutor");
                });
    }

    @Test
    void createsTenantAlertMonitorWithDefaultLoggingCallbackWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.default-tenant=public",
                        "privacy.guard.tenant.policies.tenant-a.audit.attach-tenant-id=true",
                        "privacy.guard.tenant.policies.tenant-a.audit.tenant-detail-key=tenant",
                        "privacy.guard.audit.dead-letter.observability.alert.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.tenant-ids[0]=tenant-a",
                        "privacy.guard.audit.dead-letter.observability.alert.check-interval=10ms"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(LoggingPrivacyTenantAuditDeadLetterAlertCallback.class);
                    assertThat(context).hasSingleBean(PrivacyTenantAuditDeadLetterAlertMonitor.class);
                    assertThat(context).hasBean("privacyAuditTenantDeadLetterAlertExecutor");
                });
    }

    @Test
    void createsWebhookCallbackWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.audit.dead-letter.observability.alert.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.webhook.url=https://example.com/privacy-alerts"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookAlertCallback.class);
                    assertThat(context).hasBean("privacyAuditDeadLetterWebhookHttpClient");
                });
    }

    @Test
    void createsTenantScopedWebhookCallbackWhenTenantAlertingAndWebhookConfigured() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.default-tenant=public",
                        "privacy.guard.tenant.policies.tenant-a.audit.attach-tenant-id=true",
                        "privacy.guard.tenant.policies.tenant-a.audit.tenant-detail-key=tenant",
                        "privacy.guard.audit.dead-letter.observability.alert.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.tenant-ids[0]=tenant-a",
                        "privacy.guard.audit.dead-letter.observability.alert.webhook.url=https://example.com/privacy-alerts"
                )
                .run(context -> assertThat(context.getBeansOfType(PrivacyTenantAuditDeadLetterAlertCallback.class)).isNotEmpty());
    }

    @Test
    void createsTenantTelemetryWhenMicrometerAvailable() {
        contextRunner
                .withBean(io.micrometer.core.instrument.MeterRegistry.class, io.micrometer.core.instrument.simple.SimpleMeterRegistry::new)
                .run(context -> assertThat(context).hasSingleBean(PrivacyTenantAuditTelemetry.class));
    }

    @Test
    void recordsTenantMetricsWhenMeterRegistryComesFromBootAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MetricsAutoConfiguration.class,
                        CompositeMeterRegistryAutoConfiguration.class,
                        SimpleMetricsExportAutoConfiguration.class,
                        JacksonAutoConfiguration.class,
                        PrivacyGuardAutoConfiguration.class,
                        PrivacyGuardTenantObservabilityAutoConfiguration.class
                ))
                .withPropertyValues(
                        "management.simple.metrics.export.enabled=true",
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.default-tenant=public",
                        "privacy.guard.tenant.policies.tenant-a.audit.attach-tenant-id=true",
                        "privacy.guard.tenant.policies.tenant-a.audit.tenant-detail-key=tenant",
                        "privacy.guard.audit.repository-type=IN_MEMORY"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivacyTenantAuditTelemetry.class);

                    MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
                    PrivacyAuditService privacyAuditService = context.getBean(PrivacyAuditService.class);
                    PrivacyTenantAuditQueryService tenantAuditQueryService = context.getBean(PrivacyTenantAuditQueryService.class);

                    try (PrivacyTenantContextScope ignored = PrivacyTenantContextHolder.openScope("tenant-a")) {
                        privacyAuditService.record(
                                "PATIENT_READ",
                                "Patient",
                                "demo-patient",
                                "alice@example.com",
                                "SUCCESS",
                                Map.of("phone", "13800138000")
                        );
                    }

                    tenantAuditQueryService.findByCriteria("tenant-a", PrivacyAuditQueryCriteria.recent(20));

                    assertThat(meterRegistry.get("privacy.audit.tenant.write.path")
                            .tag("domain", "audit_write")
                            .tag("path", "native")
                            .counter()
                            .count()).isEqualTo(1.0d);
                    assertThat(meterRegistry.get("privacy.audit.tenant.read.path")
                            .tag("domain", "audit")
                            .tag("path", "native")
                            .counter()
                            .count()).isEqualTo(1.0d);
                });
    }

    @Test
    void exposesTenantScopedDeadLetterObservationService() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.default-tenant=public",
                        "privacy.guard.tenant.policies.tenant-a.audit.attach-tenant-id=true",
                        "privacy.guard.tenant.policies.tenant-a.audit.tenant-detail-key=tenant"
                )
                .run(context -> {
                    InMemoryPrivacyAuditDeadLetterRepository repository = context.getBean(InMemoryPrivacyAuditDeadLetterRepository.class);
                    repository.save(entry("dead-letter-tenant-a", "tenant-a"));

                    PrivacyTenantAuditDeadLetterObservationService observationService =
                            context.getBean(PrivacyTenantAuditDeadLetterObservationService.class);

                    assertThat(observationService.currentSnapshot("tenant-a").total()).isEqualTo(1L);
                    assertThat(observationService.currentSnapshot("tenant-a").state()).isEqualTo(PrivacyAuditDeadLetterBacklogState.WARNING);
                    assertThat(observationService.currentSnapshot("tenant-b").total()).isZero();
                });
    }

    @Test
    void createsTenantBacklogMetricsBinderWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.default-tenant=public",
                        "privacy.guard.tenant.policies.tenant-a.audit.attach-tenant-id=true",
                        "privacy.guard.tenant.policies.tenant-a.audit.tenant-detail-key=tenant",
                        "privacy.guard.audit.dead-letter.observability.metrics.tenant-enabled=true"
                )
                .run(context -> assertThat(context).hasSingleBean(PrivacyTenantAuditDeadLetterMetricsBinder.class));
    }

    @Test
    void createsTenantHealthIndicatorWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.default-tenant=public",
                        "privacy.guard.tenant.policies.tenant-a.audit.attach-tenant-id=true",
                        "privacy.guard.tenant.policies.tenant-a.audit.tenant-detail-key=tenant",
                        "privacy.guard.audit.dead-letter.observability.health.tenant-enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivacyTenantAuditDeadLetterHealthIndicator.class);
                    assertThat(context).hasBean("privacyAuditTenantDeadLettersHealthIndicator");
                });
    }

    @Test
    void createsEmailCallbackWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.audit.dead-letter.observability.alert.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.email.from=privacy@example.com",
                        "privacy.guard.audit.dead-letter.observability.alert.email.to=ops@example.com"
                )
                .withUserConfiguration(MailConfig.class)
                .run(context -> assertThat(context).hasSingleBean(PrivacyAuditDeadLetterEmailAlertCallback.class));
    }

    @Test
    void createsTenantScopedEmailCallbackWhenTenantAlertingAndEmailConfigured() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.default-tenant=public",
                        "privacy.guard.tenant.policies.tenant-a.audit.attach-tenant-id=true",
                        "privacy.guard.tenant.policies.tenant-a.audit.tenant-detail-key=tenant",
                        "privacy.guard.audit.dead-letter.observability.alert.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.tenant-ids[0]=tenant-a",
                        "privacy.guard.audit.dead-letter.observability.alert.email.from=privacy@example.com",
                        "privacy.guard.audit.dead-letter.observability.alert.email.to=ops@example.com"
                )
                .withUserConfiguration(MailConfig.class)
                .run(context -> assertThat(context.getBeansOfType(PrivacyTenantAuditDeadLetterAlertCallback.class)).isNotEmpty());
    }

    @Test
    void createsAlertMonitorWhenCustomCallbackProvidedAndLoggingDisabled() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.audit.dead-letter.observability.alert.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.logging.enabled=false",
                        "privacy.guard.audit.dead-letter.observability.alert.check-interval=10ms"
                )
                .withUserConfiguration(AlertCallbackConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LoggingPrivacyAuditDeadLetterAlertCallback.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterAlertMonitor.class);
                    assertThat(context).hasBean("privacyAuditDeadLetterAlertExecutor");
                });
    }

    @Test
    void createsTenantAlertMonitorWhenCustomTenantCallbackProvidedAndLoggingDisabled() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.default-tenant=public",
                        "privacy.guard.tenant.policies.tenant-a.audit.attach-tenant-id=true",
                        "privacy.guard.tenant.policies.tenant-a.audit.tenant-detail-key=tenant",
                        "privacy.guard.audit.dead-letter.observability.alert.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.tenant-ids[0]=tenant-a",
                        "privacy.guard.audit.dead-letter.observability.alert.logging.enabled=false",
                        "privacy.guard.audit.dead-letter.observability.alert.check-interval=10ms"
                )
                .withUserConfiguration(TenantAlertCallbackConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LoggingPrivacyTenantAuditDeadLetterAlertCallback.class);
                    assertThat(context).hasSingleBean(PrivacyTenantAuditDeadLetterAlertMonitor.class);
                    assertThat(context).hasBean("privacyAuditTenantDeadLetterAlertExecutor");
                });
    }

    @Test
    void backsOffAlertMonitorWhenNoCallbacksRemain() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.audit.dead-letter.observability.alert.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.logging.enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LoggingPrivacyAuditDeadLetterAlertCallback.class);
                    assertThat(context).doesNotHaveBean(PrivacyAuditDeadLetterAlertMonitor.class);
                    assertThat(context).doesNotHaveBean("privacyAuditDeadLetterAlertExecutor");
                });
    }

    @Test
    void backsOffTenantAlertMonitorWhenNoTenantCallbacksRemain() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.repository-type=IN_MEMORY",
                        "privacy.guard.tenant.enabled=true",
                        "privacy.guard.tenant.default-tenant=public",
                        "privacy.guard.tenant.policies.tenant-a.audit.attach-tenant-id=true",
                        "privacy.guard.tenant.policies.tenant-a.audit.tenant-detail-key=tenant",
                        "privacy.guard.audit.dead-letter.observability.alert.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.tenant-ids[0]=tenant-a",
                        "privacy.guard.audit.dead-letter.observability.alert.logging.enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LoggingPrivacyTenantAuditDeadLetterAlertCallback.class);
                    assertThat(context).doesNotHaveBean(PrivacyTenantAuditDeadLetterAlertMonitor.class);
                    assertThat(context).doesNotHaveBean("privacyAuditTenantDeadLetterAlertExecutor");
                });
    }

    @Test
    void backsOffCleanlyWhenActuatorAndMicrometerAreMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate", "io.micrometer"))
                .withPropertyValues("privacy.guard.audit.dead-letter.repository-type=IN_MEMORY")
                .run(context -> assertThat(context).hasSingleBean(PrivacyAuditDeadLetterObservationService.class));
    }

    private PrivacyAuditDeadLetterEntry entry(String resourceId) {
        return entry(resourceId, null);
    }

    private PrivacyAuditDeadLetterEntry entry(String resourceId, String tenantId) {
        Map<String, String> details = tenantId == null ? Map.of("phone", "138****8000") : Map.of("tenant", tenantId, "phone", "138****8000");
        return new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "failure",
                Instant.parse("2026-03-08T00:00:00Z"),
                "READ",
                "Patient",
                resourceId,
                "actor",
                "OK",
                details
        );
    }

    @Configuration(proxyBeanMethods = false)
    static class AlertCallbackConfig {

        @Bean
        PrivacyAuditDeadLetterAlertCallback privacyAuditDeadLetterAlertCallback() {
            return event -> {
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TenantAlertCallbackConfig {

        @Bean
        PrivacyTenantAuditDeadLetterAlertCallback privacyTenantAuditDeadLetterAlertCallback() {
            return (tenantId, event) -> {
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MailConfig {

        @Bean
        JavaMailSender javaMailSender() {
            return mock(JavaMailSender.class);
        }
    }
}
