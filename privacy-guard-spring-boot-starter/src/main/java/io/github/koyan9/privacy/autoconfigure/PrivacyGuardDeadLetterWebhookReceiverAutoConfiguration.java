/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.FilePrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.JdbcPrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookBodyCachingFilter;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreRedisProperties;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreMetricsBinder;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreObservationService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookRequestVerifier;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationRouteRegistry;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationTelemetry;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationFilter;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationInterceptor;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationSettings;
import io.github.koyan9.privacy.audit.PrivacyAuditSchemaInitializer;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterAlertReceiverPolicy;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterAlertRoutePolicy;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterAlertRoutePolicyResolver;
import io.github.koyan9.privacy.audit.RedisPrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.MicrometerPrivacyAuditDeadLetterWebhookVerificationTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
public class PrivacyGuardDeadLetterWebhookReceiverAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(PrivacyGuardDeadLetterWebhookReceiverAutoConfiguration.class);

    static class ReceiverVerificationPropertiesEnabledCondition extends AnyNestedCondition {

        ReceiverVerificationPropertiesEnabledCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(
                prefix = "privacy.guard.audit.dead-letter.observability.alert.receiver.filter",
                name = "enabled",
                havingValue = "true"
        )
        static class FilterEnabled {
        }

        @ConditionalOnProperty(
                prefix = "privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor",
                name = "enabled",
                havingValue = "true"
        )
        static class InterceptorEnabled {
        }

        @ConditionalOnProperty(
                prefix = "privacy.guard.audit.dead-letter.observability.alert.receiver.verification",
                name = "enabled",
                havingValue = "true"
        )
        static class VerificationEnabled {
        }
    }

    static class ReceiverVerificationEnabledCondition extends AnyNestedCondition {

        ReceiverVerificationEnabledCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @Conditional(ReceiverVerificationPropertiesEnabledCondition.class)
        static class PropertiesEnabled {
        }

        @ConditionalOnBean(PrivacyAuditDeadLetterWebhookVerificationSettings.class)
        static class SettingsBeanPresent {
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public PrivacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver privacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver() {
        return new PrivacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(ReceiverVerificationPropertiesEnabledCondition.class)
    public PrivacyAuditDeadLetterWebhookVerificationSettings privacyAuditDeadLetterWebhookVerificationSettings(
            PrivacyGuardProperties properties
    ) {
        PrivacyGuardProperties.AlertReceiverVerification verification = properties.getAudit()
                .getDeadLetter()
                .getObservability()
                .getAlert()
                .getReceiver()
                .getVerification();
        if (!StringUtils.hasText(verification.getBearerToken()) && !StringUtils.hasText(verification.getSignatureSecret())) {
            logger.warn("Receiver verification is enabled but no bearer token or signature secret is configured; requests will not be rejected");
        }
        return new PrivacyAuditDeadLetterWebhookVerificationSettings(
                verification.getBearerToken(),
                verification.getSignatureSecret(),
                verification.getSignatureAlgorithm(),
                verification.getSignatureHeader(),
                verification.getTimestampHeader(),
                verification.getNonceHeader(),
                verification.getMaxSkew(),
                properties.getAudit()
                        .getDeadLetter()
                        .getObservability()
                        .getAlert()
                        .getReceiver()
                        .getReplayStore()
                        .getNamespace()
        );
    }

    @Bean
    @Conditional(ReceiverVerificationEnabledCondition.class)
    @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookReplayStore.class)
    @ConditionalOnExpression(
            "'${privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.enabled:false}' != 'true' and " +
                    "'${privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.redis.enabled:false}' != 'true' and " +
                    "'${privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file.enabled:false}' != 'true'"
    )
    public InMemoryPrivacyAuditDeadLetterWebhookReplayStore privacyAuditDeadLetterWebhookReplayStore() {
        return new InMemoryPrivacyAuditDeadLetterWebhookReplayStore();
    }

    @Bean
    @Conditional(ReceiverVerificationEnabledCondition.class)
    @ConditionalOnBean(PrivacyAuditDeadLetterWebhookReplayStore.class)
    @ConditionalOnMissingBean
    public PrivacyAuditDeadLetterWebhookRequestVerifier privacyAuditDeadLetterWebhookRequestVerifier(
            PrivacyAuditDeadLetterWebhookVerificationSettings settings,
            PrivacyAuditDeadLetterWebhookReplayStore replayStore
    ) {
        return new PrivacyAuditDeadLetterWebhookRequestVerifier(settings, replayStore);
    }

    @Bean
    @Conditional(ReceiverVerificationPropertiesEnabledCondition.class)
    @ConditionalOnBean(PrivacyAuditDeadLetterWebhookReplayStore.class)
    @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookVerificationRouteRegistry.class)
    public PrivacyAuditDeadLetterWebhookVerificationRouteRegistry privacyAuditDeadLetterWebhookVerificationRouteRegistry(
            PrivacyGuardProperties properties,
            PrivacyAuditDeadLetterWebhookReplayStore replayStore,
            PrivacyTenantDeadLetterAlertRoutePolicyResolver routePolicyResolver
    ) {
        List<PrivacyAuditDeadLetterWebhookVerificationRouteRegistry.Route> routes = new ArrayList<>();
        PrivacyAuditDeadLetterWebhookVerificationSettings defaults = verificationSettingsFrom(properties);
        for (String tenantId : resolveTenantAlertRouteTenantIds(properties)) {
            PrivacyTenantDeadLetterAlertRoutePolicy routePolicy = routePolicyResolver.resolve(tenantId);
            PrivacyTenantDeadLetterAlertReceiverPolicy receiverPolicy = routePolicy == null
                    ? PrivacyTenantDeadLetterAlertReceiverPolicy.none()
                    : routePolicy.receiver();
            String pathPattern = normalize(receiverPolicy.pathPattern());
            if (pathPattern == null) {
                continue;
            }
            PrivacyAuditDeadLetterWebhookVerificationSettings settings = mergeVerificationSettings(defaults, receiverPolicy);
            routes.add(new PrivacyAuditDeadLetterWebhookVerificationRouteRegistry.Route(
                    tenantId,
                    pathPattern,
                    new PrivacyAuditDeadLetterWebhookRequestVerifier(settings, replayStore)
            ));
        }
        return new PrivacyAuditDeadLetterWebhookVerificationRouteRegistry(routes);
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

    @Bean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
    @Conditional(ReceiverVerificationEnabledCondition.class)
    @ConditionalOnProperty(
            prefix = "privacy.guard.audit.dead-letter.observability.alert.receiver.metrics",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public PrivacyAuditDeadLetterWebhookVerificationTelemetry privacyAuditDeadLetterWebhookVerificationTelemetry(
            io.micrometer.core.instrument.MeterRegistry meterRegistry
    ) {
        return new MicrometerPrivacyAuditDeadLetterWebhookVerificationTelemetry(meterRegistry);
    }

    @Bean
    @ConditionalOnClass(ObjectMapper.class)
    @Conditional(ReceiverVerificationEnabledCondition.class)
    @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookReplayStore.class)
    @ConditionalOnProperty(
            prefix = "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file",
            name = "enabled",
            havingValue = "true"
    )
    @ConditionalOnExpression(
            "'${privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.enabled:false}' != 'true' and " +
                    "'${privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.redis.enabled:false}' != 'true'"
    )
    public FilePrivacyAuditDeadLetterWebhookReplayStore filePrivacyAuditDeadLetterWebhookReplayStore(
            ObjectMapper objectMapper,
            PrivacyGuardProperties properties
    ) {
        PrivacyGuardProperties.AlertReceiverReplayStoreFile file = properties.getAudit()
                .getDeadLetter()
                .getObservability()
                .getAlert()
                .getReceiver()
                .getReplayStore()
                .getFile();
        if (!StringUtils.hasText(file.getPath())) {
            throw new IllegalArgumentException("Replay-store file path must be configured when file replay-store is enabled");
        }
        return new FilePrivacyAuditDeadLetterWebhookReplayStore(Path.of(file.getPath()), objectMapper);
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(PrivacyAuditDeadLetterWebhookRequestVerifier.class)
    @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookVerificationFilter.class)
    @ConditionalOnExpression("'${privacy.guard.audit.dead-letter.observability.alert.receiver.filter.enabled:false}' == 'true' and '${privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor.enabled:false}' != 'true'")
    public PrivacyAuditDeadLetterWebhookVerificationFilter privacyAuditDeadLetterWebhookVerificationFilter(
            PrivacyAuditDeadLetterWebhookRequestVerifier verifier,
            ObjectProvider<PrivacyAuditDeadLetterWebhookVerificationRouteRegistry> routeRegistryProvider,
            PrivacyGuardProperties properties,
            ObjectProvider<PrivacyAuditDeadLetterWebhookVerificationTelemetry> telemetryProvider
    ) {
        return new PrivacyAuditDeadLetterWebhookVerificationFilter(
                verifier,
                properties.getAudit().getDeadLetter().getObservability().getAlert().getReceiver().getFilter().getPathPattern(),
                routeRegistryProvider.getIfAvailable(),
                telemetryProvider.getIfAvailable(PrivacyAuditDeadLetterWebhookVerificationTelemetry::noop)
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
            ObjectProvider<PrivacyAuditDeadLetterWebhookVerificationRouteRegistry> routeRegistryProvider,
            PrivacyGuardProperties properties
    ) {
        List<String> pathPatterns = new ArrayList<>();
        pathPatterns.add(properties.getAudit().getDeadLetter().getObservability().getAlert().getReceiver().getInterceptor().getPathPattern());
        PrivacyAuditDeadLetterWebhookVerificationRouteRegistry routeRegistry = routeRegistryProvider.getIfAvailable();
        if (routeRegistry != null) {
            pathPatterns.addAll(routeRegistry.pathPatterns());
        }
        return new PrivacyAuditDeadLetterWebhookBodyCachingFilter(
                pathPatterns
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
            ObjectProvider<PrivacyAuditDeadLetterWebhookVerificationRouteRegistry> routeRegistryProvider,
            PrivacyGuardProperties properties,
            ObjectProvider<PrivacyAuditDeadLetterWebhookVerificationTelemetry> telemetryProvider
    ) {
        return new PrivacyAuditDeadLetterWebhookVerificationInterceptor(
                verifier,
                properties.getAudit().getDeadLetter().getObservability().getAlert().getReceiver().getInterceptor().getPathPattern(),
                routeRegistryProvider.getIfAvailable(),
                telemetryProvider.getIfAvailable(PrivacyAuditDeadLetterWebhookVerificationTelemetry::noop)
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
            ObjectProvider<PrivacyAuditDeadLetterWebhookVerificationRouteRegistry> routeRegistryProvider,
            PrivacyGuardProperties properties
    ) {
        List<String> pathPatterns = new ArrayList<>();
        pathPatterns.add(properties.getAudit().getDeadLetter().getObservability().getAlert().getReceiver().getInterceptor().getPathPattern());
        PrivacyAuditDeadLetterWebhookVerificationRouteRegistry routeRegistry = routeRegistryProvider.getIfAvailable();
        if (routeRegistry != null) {
            pathPatterns.addAll(routeRegistry.pathPatterns());
        }
        return new PrivacyGuardDeadLetterWebhookReceiverMvcConfigurer(
                interceptor,
                pathPatterns
        );
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcOperations")
    static class JdbcReplayStoreConfiguration {

        @Bean
        @Conditional(ReceiverVerificationEnabledCondition.class)
        @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookReplayStore.class)
        @ConditionalOnProperty(
                prefix = "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc",
                name = "enabled",
                havingValue = "true"
        )
        public JdbcPrivacyAuditDeadLetterWebhookReplayStore jdbcPrivacyAuditDeadLetterWebhookReplayStore(
                org.springframework.jdbc.core.JdbcOperations jdbcOperations,
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
                    jdbcProperties.getCleanupInterval(),
                    jdbcProperties.getCleanupBatchSize()
            );
        }

        @Bean
        @Conditional(ReceiverVerificationEnabledCondition.class)
        @ConditionalOnProperty(
                prefix = "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc",
                name = {"enabled", "initialize-schema"},
                havingValue = "true"
        )
        public PrivacyAuditSchemaInitializer privacyAuditDeadLetterWebhookReplayStoreSchemaInitializer(
                org.springframework.jdbc.core.JdbcOperations jdbcOperations,
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

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
    static class RedisReplayStoreConfiguration {

        @Bean
        @ConditionalOnBean(type = "org.springframework.data.redis.connection.RedisConnectionFactory")
        @Conditional(ReceiverVerificationEnabledCondition.class)
        @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookReplayStore.class)
        @ConditionalOnProperty(
                prefix = "privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.redis",
                name = "enabled",
                havingValue = "true"
        )
        @ConditionalOnExpression("'${privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.enabled:false}' != 'true'")
        public RedisPrivacyAuditDeadLetterWebhookReplayStore redisPrivacyAuditDeadLetterWebhookReplayStore(
                org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory,
                PrivacyGuardProperties properties
        ) {
            PrivacyAuditDeadLetterWebhookReplayStoreRedisProperties redisProperties = properties.getAudit()
                    .getDeadLetter()
                    .getObservability()
                    .getAlert()
                    .getReceiver()
                    .getReplayStore()
                    .getRedis();
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate =
                    new org.springframework.data.redis.core.StringRedisTemplate(redisConnectionFactory);
            redisTemplate.afterPropertiesSet();
            return new RedisPrivacyAuditDeadLetterWebhookReplayStore(
                    redisTemplate,
                    redisProperties.getKeyPrefix(),
                    redisProperties.getScanBatchSize()
            );
        }
    }

    private PrivacyAuditDeadLetterWebhookVerificationSettings verificationSettingsFrom(PrivacyGuardProperties properties) {
        PrivacyGuardProperties.AlertReceiverVerification verification = properties.getAudit()
                .getDeadLetter()
                .getObservability()
                .getAlert()
                .getReceiver()
                .getVerification();
        return new PrivacyAuditDeadLetterWebhookVerificationSettings(
                verification.getBearerToken(),
                verification.getSignatureSecret(),
                verification.getSignatureAlgorithm(),
                verification.getSignatureHeader(),
                verification.getTimestampHeader(),
                verification.getNonceHeader(),
                verification.getMaxSkew(),
                properties.getAudit()
                        .getDeadLetter()
                        .getObservability()
                        .getAlert()
                        .getReceiver()
                        .getReplayStore()
                        .getNamespace()
        );
    }

    private PrivacyAuditDeadLetterWebhookVerificationSettings mergeVerificationSettings(
            PrivacyAuditDeadLetterWebhookVerificationSettings defaults,
            PrivacyTenantDeadLetterAlertReceiverPolicy route
    ) {
        return new PrivacyAuditDeadLetterWebhookVerificationSettings(
                firstNonNull(route.bearerToken(), defaults.bearerToken()),
                firstNonNull(route.signatureSecret(), defaults.signatureSecret()),
                firstNonNull(route.signatureAlgorithm(), defaults.signatureAlgorithm()),
                firstNonNull(route.signatureHeader(), defaults.signatureHeader()),
                firstNonNull(route.timestampHeader(), defaults.timestampHeader()),
                firstNonNull(route.nonceHeader(), defaults.nonceHeader()),
                firstNonNull(route.maxSkew(), defaults.maxSkew()),
                firstNonNull(route.replayNamespace(), defaults.replayNamespace())
        );
    }

    private List<String> resolveTenantAlertRouteTenantIds(PrivacyGuardProperties properties) {
        java.util.LinkedHashSet<String> tenantIds = new java.util.LinkedHashSet<>();
        tenantIds.addAll(properties.getTenant().getPolicies().keySet());
        tenantIds.addAll(properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().getRoutes().keySet());
        tenantIds.removeIf(value -> !StringUtils.hasText(value));
        return tenantIds.stream()
                .map(String::trim)
                .toList();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private <T> T firstNonNull(T routeValue, T defaultValue) {
        return routeValue != null ? routeValue : defaultValue;
    }
}
