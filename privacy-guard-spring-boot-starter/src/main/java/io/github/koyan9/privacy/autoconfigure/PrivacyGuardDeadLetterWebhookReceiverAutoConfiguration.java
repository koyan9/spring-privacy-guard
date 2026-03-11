/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.JdbcPrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookBodyCachingFilter;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreMetricsBinder;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreObservationService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookRequestVerifier;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationFilter;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationInterceptor;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationSettings;
import io.github.koyan9.privacy.audit.PrivacyAuditSchemaInitializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcOperations;

import javax.sql.DataSource;

@AutoConfiguration
public class PrivacyGuardDeadLetterWebhookReceiverAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PrivacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver privacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver() {
        return new PrivacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver();
    }

    @Bean
    @ConditionalOnBean(PrivacyAuditDeadLetterWebhookVerificationSettings.class)
    @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookReplayStore.class)
    public InMemoryPrivacyAuditDeadLetterWebhookReplayStore privacyAuditDeadLetterWebhookReplayStore() {
        return new InMemoryPrivacyAuditDeadLetterWebhookReplayStore();
    }

    @Bean
    @ConditionalOnBean({PrivacyAuditDeadLetterWebhookVerificationSettings.class, PrivacyAuditDeadLetterWebhookReplayStore.class})
    @ConditionalOnMissingBean
    public PrivacyAuditDeadLetterWebhookRequestVerifier privacyAuditDeadLetterWebhookRequestVerifier(
            PrivacyAuditDeadLetterWebhookVerificationSettings settings,
            PrivacyAuditDeadLetterWebhookReplayStore replayStore
    ) {
        return new PrivacyAuditDeadLetterWebhookRequestVerifier(settings, replayStore);
    }

    @Bean
    @ConditionalOnBean(PrivacyAuditDeadLetterWebhookReplayStore.class)
    @ConditionalOnMissingBean
    public PrivacyAuditDeadLetterWebhookReplayStoreObservationService privacyAuditDeadLetterWebhookReplayStoreObservationService(
            PrivacyAuditDeadLetterWebhookReplayStore replayStore,
            PrivacyGuardProperties properties
    ) {
        return new PrivacyAuditDeadLetterWebhookReplayStoreObservationService(
                replayStore,
                properties.getAudit().getDeadLetter().getObservability().getAlert().getReceiver().getMetrics().getExpiringSoonWindow()
        );
    }

    @Bean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.binder.MeterBinder")
    @ConditionalOnBean(PrivacyAuditDeadLetterWebhookReplayStoreObservationService.class)
    @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookReplayStoreMetricsBinder.class)
    @ConditionalOnProperty(
            prefix = "privacy.guard.audit.dead-letter.observability.alert.receiver.metrics",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public PrivacyAuditDeadLetterWebhookReplayStoreMetricsBinder privacyAuditDeadLetterWebhookReplayStoreMetricsBinder(
            PrivacyAuditDeadLetterWebhookReplayStoreObservationService observationService
    ) {
        return new PrivacyAuditDeadLetterWebhookReplayStoreMetricsBinder(observationService);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcOperations")
    static class JdbcReplayStoreConfiguration {

        @Bean
        @ConditionalOnBean(PrivacyAuditDeadLetterWebhookVerificationSettings.class)
        @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookReplayStore.class)
        @ConditionalOnProperty(
                prefix = "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc",
                name = "enabled",
                havingValue = "true"
        )
        public JdbcPrivacyAuditDeadLetterWebhookReplayStore jdbcPrivacyAuditDeadLetterWebhookReplayStore(
                JdbcOperations jdbcOperations,
                PrivacyGuardProperties properties
        ) {
            PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties jdbcProperties = properties.getAudit()
                    .getDeadLetter()
                    .getObservability()
                    .getAlert()
                    .getReceiver()
                    .getReplayStore()
                    .getJdbc();
            return new JdbcPrivacyAuditDeadLetterWebhookReplayStore(
                    jdbcOperations,
                    jdbcProperties.getTableName(),
                    jdbcProperties.getCleanupInterval()
            );
        }

        @Bean
        @ConditionalOnBean(PrivacyAuditDeadLetterWebhookVerificationSettings.class)
        @ConditionalOnExpression(
                "'${privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.enabled:false}' == 'true' " +
                        "and '${privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.initialize-schema:false}' == 'true'"
        )
        public PrivacyAuditSchemaInitializer privacyAuditDeadLetterWebhookReplayStoreSchemaInitializer(
                JdbcOperations jdbcOperations,
                ResourceLoader resourceLoader,
                ObjectProvider<DataSource> dataSourceProvider,
                PrivacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver schemaLocationResolver,
                PrivacyGuardProperties properties
        ) {
            PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties jdbcProperties = properties.getAudit()
                    .getDeadLetter()
                    .getObservability()
                    .getAlert()
                    .getReceiver()
                    .getReplayStore()
                    .getJdbc();
            String schemaLocation = schemaLocationResolver.resolve(jdbcProperties, dataSourceProvider.getIfAvailable());
            PrivacyAuditSchemaInitializer initializer = new PrivacyAuditSchemaInitializer(
                    jdbcOperations,
                    resourceLoader,
                    schemaLocation,
                    jdbcProperties.getTableName()
            );
            initializer.initialize();
            return initializer;
        }
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(PrivacyAuditDeadLetterWebhookRequestVerifier.class)
    @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookVerificationFilter.class)
    @ConditionalOnExpression("'${privacy.guard.audit.dead-letter.observability.alert.receiver.filter.enabled:false}' == 'true' and '${privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor.enabled:false}' != 'true'")
    public PrivacyAuditDeadLetterWebhookVerificationFilter privacyAuditDeadLetterWebhookVerificationFilter(
            PrivacyAuditDeadLetterWebhookRequestVerifier verifier,
            PrivacyGuardProperties properties
    ) {
        return new PrivacyAuditDeadLetterWebhookVerificationFilter(
                verifier,
                properties.getAudit().getDeadLetter().getObservability().getAlert().getReceiver().getFilter().getPathPattern()
        );
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(PrivacyAuditDeadLetterWebhookRequestVerifier.class)
    @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookBodyCachingFilter.class)
    @ConditionalOnProperty(
            prefix = "privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor",
            name = "enabled",
            havingValue = "true"
    )
    public PrivacyAuditDeadLetterWebhookBodyCachingFilter privacyAuditDeadLetterWebhookBodyCachingFilter(
            PrivacyGuardProperties properties
    ) {
        return new PrivacyAuditDeadLetterWebhookBodyCachingFilter(
                properties.getAudit().getDeadLetter().getObservability().getAlert().getReceiver().getInterceptor().getPathPattern()
        );
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.servlet.HandlerInterceptor")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(PrivacyAuditDeadLetterWebhookRequestVerifier.class)
    @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookVerificationInterceptor.class)
    @ConditionalOnProperty(
            prefix = "privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor",
            name = "enabled",
            havingValue = "true"
    )
    public PrivacyAuditDeadLetterWebhookVerificationInterceptor privacyAuditDeadLetterWebhookVerificationInterceptor(
            PrivacyAuditDeadLetterWebhookRequestVerifier verifier,
            PrivacyGuardProperties properties
    ) {
        return new PrivacyAuditDeadLetterWebhookVerificationInterceptor(
                verifier,
                properties.getAudit().getDeadLetter().getObservability().getAlert().getReceiver().getInterceptor().getPathPattern()
        );
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(PrivacyAuditDeadLetterWebhookVerificationInterceptor.class)
    @ConditionalOnMissingBean(PrivacyGuardDeadLetterWebhookReceiverMvcConfigurer.class)
    @ConditionalOnProperty(
            prefix = "privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor",
            name = "enabled",
            havingValue = "true"
    )
    public PrivacyGuardDeadLetterWebhookReceiverMvcConfigurer privacyAuditDeadLetterWebhookReceiverMvcConfigurer(
            PrivacyAuditDeadLetterWebhookVerificationInterceptor interceptor,
            PrivacyGuardProperties properties
    ) {
        return new PrivacyGuardDeadLetterWebhookReceiverMvcConfigurer(
                interceptor,
                properties.getAudit().getDeadLetter().getObservability().getAlert().getReceiver().getInterceptor().getPathPattern()
        );
    }
}
