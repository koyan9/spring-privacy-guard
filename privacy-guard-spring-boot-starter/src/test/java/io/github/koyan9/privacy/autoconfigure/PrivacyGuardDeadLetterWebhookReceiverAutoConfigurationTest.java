/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreMetricsBinder;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreObservationService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookRequestVerifier;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationSettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyGuardDeadLetterWebhookReceiverAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    PrivacyGuardAutoConfiguration.class,
                    PrivacyGuardDeadLetterWebhookReceiverAutoConfiguration.class
            ));

    @Test
    void createsVerifierAndDefaultReplayStoreWhenSettingsBeanExists() {
        contextRunner
                .withUserConfiguration(VerificationSettingsConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookRequestVerifier.class);
                    assertThat(context).hasSingleBean(InMemoryPrivacyAuditDeadLetterWebhookReplayStore.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookReplayStoreObservationService.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookReplayStoreMetricsBinder.class);
                });
    }

    @Test
    void backsOffDefaultReplayStoreWhenCustomStoreProvided() {
        contextRunner
                .withUserConfiguration(CustomReplayStoreConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookRequestVerifier.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookReplayStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryPrivacyAuditDeadLetterWebhookReplayStore.class);
                });
    }

    @Test
    void backsOffReplayStoreMetricsWhenMicrometerMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("io.micrometer"))
                .withUserConfiguration(VerificationSettingsConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookReplayStoreObservationService.class);
                    assertThat(context).doesNotHaveBean(PrivacyAuditDeadLetterWebhookReplayStoreMetricsBinder.class);
                });
    }

    @Test
    void doesNothingWhenSettingsBeanMissing() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(PrivacyAuditDeadLetterWebhookRequestVerifier.class);
            assertThat(context).doesNotHaveBean(PrivacyAuditDeadLetterWebhookReplayStore.class);
            assertThat(context).doesNotHaveBean(PrivacyAuditDeadLetterWebhookReplayStoreObservationService.class);
            assertThat(context).doesNotHaveBean(PrivacyAuditDeadLetterWebhookReplayStoreMetricsBinder.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class VerificationSettingsConfig {

        @Bean
        PrivacyAuditDeadLetterWebhookVerificationSettings privacyAuditDeadLetterWebhookVerificationSettings() {
            return new PrivacyAuditDeadLetterWebhookVerificationSettings(
                    "token",
                    "secret",
                    "HmacSHA256",
                    "X-Signature",
                    "X-Timestamp",
                    "X-Nonce",
                    Duration.ofMinutes(5)
            );
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomReplayStoreConfig extends VerificationSettingsConfig {

        @Bean
        PrivacyAuditDeadLetterWebhookReplayStore privacyAuditDeadLetterWebhookReplayStore() {
            return new PrivacyAuditDeadLetterWebhookReplayStore() {
                @Override
                public boolean markIfNew(String nonce, java.time.Instant now, java.time.Duration ttl) {
                    return true;
                }

                @Override
                public java.util.Map<String, java.time.Instant> snapshot() {
                    return java.util.Map.of();
                }

                @Override
                public void clear() {
                }
            };
        }
    }
}
