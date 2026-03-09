/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.LoggingPrivacyAuditDeadLetterAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterAlertMonitor;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterBacklogState;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterEmailAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterEntry;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterObservationService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookAlertCallback;
import org.junit.jupiter.api.Test;
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
                    PrivacyGuardDeadLetterObservabilityAutoConfiguration.class
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
    void backsOffCleanlyWhenActuatorAndMicrometerAreMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate", "io.micrometer"))
                .withPropertyValues("privacy.guard.audit.dead-letter.repository-type=IN_MEMORY")
                .run(context -> assertThat(context).hasSingleBean(PrivacyAuditDeadLetterObservationService.class));
    }

    private PrivacyAuditDeadLetterEntry entry(String resourceId) {
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
                Map.of("phone", "138****8000")
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
    static class MailConfig {

        @Bean
        JavaMailSender javaMailSender() {
            return mock(JavaMailSender.class);
        }
    }
}
