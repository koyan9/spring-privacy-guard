/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import io.github.koyan9.privacy.audit.FilePrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.JdbcPrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreMetricsBinder;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreObservationService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookRequestVerifier;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationRouteRegistry;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationTelemetry;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationSettings;
import io.github.koyan9.privacy.audit.PrivacyAuditSchemaInitializer;
import io.github.koyan9.privacy.audit.RedisPrivacyAuditDeadLetterWebhookReplayStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcOperations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
                .withUserConfiguration(VerificationSettingsConfig.class, MeterRegistryConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookRequestVerifier.class);
                    assertThat(context).hasSingleBean(InMemoryPrivacyAuditDeadLetterWebhookReplayStore.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookReplayStoreObservationService.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookReplayStoreMetricsBinder.class);
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookVerificationTelemetry.class);
                });
    }

    @Test
    void createsVerificationSettingsFromProperties() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.verification.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.verification.bearer-token=token",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.verification.signature-secret=secret",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.verification.signature-algorithm=HmacSHA256",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.verification.signature-header=X-Signature",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.verification.timestamp-header=X-Timestamp",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.verification.nonce-header=X-Nonce",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.verification.max-skew=10s",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.namespace=tenant-a-receiver"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookVerificationSettings.class);
                    PrivacyAuditDeadLetterWebhookVerificationSettings settings = context.getBean(PrivacyAuditDeadLetterWebhookVerificationSettings.class);
                    assertThat(settings.bearerToken()).isEqualTo("token");
                    assertThat(settings.signatureSecret()).isEqualTo("secret");
                    assertThat(settings.signatureAlgorithm()).isEqualTo("HmacSHA256");
                    assertThat(settings.signatureHeader()).isEqualTo("X-Signature");
                    assertThat(settings.timestampHeader()).isEqualTo("X-Timestamp");
                    assertThat(settings.nonceHeader()).isEqualTo("X-Nonce");
                    assertThat(settings.maxSkew()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(settings.replayNamespace()).isEqualTo("tenant-a-receiver");
                    assertThat(context).hasSingleBean(InMemoryPrivacyAuditDeadLetterWebhookReplayStore.class);
                });
    }

    @Test
    void createsVerificationRouteRegistryFromTenantReceiverRoutes() {
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.filter.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.routes.tenant-a.receiver.path-pattern=/receiver/tenant-a-alerts",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.routes.tenant-a.receiver.bearer-token=tenant-a-token",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.routes.tenant-a.receiver.signature-secret=tenant-a-secret",
                        "privacy.guard.audit.dead-letter.observability.alert.tenant.routes.tenant-a.receiver.replay-namespace=tenant-a-receiver"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookVerificationRouteRegistry.class);
                    PrivacyAuditDeadLetterWebhookVerificationRouteRegistry registry =
                            context.getBean(PrivacyAuditDeadLetterWebhookVerificationRouteRegistry.class);
                    assertThat(registry.hasRoutes()).isTrue();
                    assertThat(registry.pathPatterns()).containsExactly("/receiver/tenant-a-alerts");
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
                    assertThat(context).doesNotHaveBean(PrivacyAuditDeadLetterWebhookVerificationTelemetry.class);
                });
    }

    @Test
    void stillLoadsWhenJdbcAndRedisClasspathSupportAreMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.jdbc", "org.springframework.data.redis"))
                .withPropertyValues("privacy.guard.audit.dead-letter.observability.alert.receiver.verification.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(PrivacyAuditDeadLetterWebhookRequestVerifier.class);
                    assertThat(context).hasSingleBean(InMemoryPrivacyAuditDeadLetterWebhookReplayStore.class);
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

    @Test
    void createsJdbcReplayStoreWhenEnabled() {
        contextRunner
                .withUserConfiguration(VerificationSettingsConfig.class, JdbcConfig.class)
                .withPropertyValues("privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcPrivacyAuditDeadLetterWebhookReplayStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryPrivacyAuditDeadLetterWebhookReplayStore.class);
                });
    }

    @Test
    void createsFileReplayStoreWhenEnabled() throws Exception {
        Path tempFile = Files.createTempFile("privacy-guard", ".json");
        Files.deleteIfExists(tempFile);
        contextRunner
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.verification.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file.path=" + tempFile
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FilePrivacyAuditDeadLetterWebhookReplayStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryPrivacyAuditDeadLetterWebhookReplayStore.class);
                });
    }

    @Test
    void createsRedisReplayStoreWhenEnabled() {
        contextRunner
                .withUserConfiguration(VerificationSettingsConfig.class, RedisConfig.class)
                .withPropertyValues("privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.redis.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisPrivacyAuditDeadLetterWebhookReplayStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryPrivacyAuditDeadLetterWebhookReplayStore.class);
                    assertThat(context).doesNotHaveBean(FilePrivacyAuditDeadLetterWebhookReplayStore.class);
                });
    }

    @Test
    void prefersJdbcReplayStoreWhenJdbcAndFileEnabled() throws Exception {
        Path tempFile = Files.createTempFile("privacy-guard", ".json");
        Files.deleteIfExists(tempFile);
        contextRunner
                .withUserConfiguration(JdbcConfig.class)
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.verification.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file.path=" + tempFile
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcPrivacyAuditDeadLetterWebhookReplayStore.class);
                    assertThat(context).doesNotHaveBean(FilePrivacyAuditDeadLetterWebhookReplayStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryPrivacyAuditDeadLetterWebhookReplayStore.class);
                });
    }

    @Test
    void prefersRedisReplayStoreWhenRedisAndFileEnabled() throws Exception {
        Path tempFile = Files.createTempFile("privacy-guard", ".json");
        Files.deleteIfExists(tempFile);
        contextRunner
                .withUserConfiguration(VerificationSettingsConfig.class, RedisConfig.class)
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.redis.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file.path=" + tempFile
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisPrivacyAuditDeadLetterWebhookReplayStore.class);
                    assertThat(context).doesNotHaveBean(FilePrivacyAuditDeadLetterWebhookReplayStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryPrivacyAuditDeadLetterWebhookReplayStore.class);
                });
    }

    @Test
    void prefersJdbcReplayStoreWhenJdbcAndRedisEnabled() {
        contextRunner
                .withUserConfiguration(VerificationSettingsConfig.class, JdbcConfig.class, RedisConfig.class)
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.redis.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcPrivacyAuditDeadLetterWebhookReplayStore.class);
                    assertThat(context).doesNotHaveBean(RedisPrivacyAuditDeadLetterWebhookReplayStore.class);
                });
    }

    @Test
    void initializesJdbcReplayStoreSchemaWhenConfigured() {
        contextRunner
                .withUserConfiguration(VerificationSettingsConfig.class, JdbcConfig.class)
                .withPropertyValues(
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.enabled=true",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.initialize-schema=true",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.schema-location=classpath:META-INF/privacy-guard/privacy-audit-dead-letter-webhook-replay-store-schema-h2.sql",
                        "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.table-name=receiver_replay_store"
                )
                .run(context -> {
                    JdbcOperations jdbcOperations = context.getBean(JdbcOperations.class);

                    assertThat(context).hasSingleBean(PrivacyAuditSchemaInitializer.class);
                    verify(jdbcOperations).execute(contains("create table if not exists receiver_replay_store"));
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
                    Duration.ofMinutes(5),
                    null
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

    @Configuration
    static class MeterRegistryConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcConfig {

        @Bean
        JdbcOperations jdbcOperations() {
            return mock(JdbcOperations.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }
}
