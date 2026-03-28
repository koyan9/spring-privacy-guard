/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.LoggingPrivacyAuditDeadLetterAlertCallback;
import io.github.koyan9.privacy.audit.LoggingPrivacyTenantAuditDeadLetterAlertCallback;
import io.github.koyan9.privacy.audit.MicrometerPrivacyAuditDeadLetterWebhookAlertTelemetry;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterAlertMonitor;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterEmailAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterHealthIndicator;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterMetricsBinder;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterObservationService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterStatsService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookAlertTelemetry;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterHealthIndicator;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterMetricsBinder;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterAlertMonitor;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterObservationService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterQueryService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditTelemetry;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterAlertDeliveryPolicyResolver;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterAlertMonitoringPolicy;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterAlertMonitoringPolicyResolver;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterAlertRoutePolicyResolver;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterObservabilityPolicyResolver;
import io.github.koyan9.privacy.audit.TenantScopedPrivacyAuditDeadLetterEmailAlertCallback;
import io.github.koyan9.privacy.audit.TenantScopedPrivacyAuditDeadLetterWebhookAlertCallback;
import io.github.koyan9.privacy.core.PrivacyTenantProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.net.http.HttpClient;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@AutoConfiguration(after = PrivacyGuardAutoConfiguration.class)
@ConditionalOnProperty(prefix = "privacy.guard.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PrivacyGuardDeadLetterObservabilityAutoConfiguration {

    static class TenantWebhookTargetConfiguredCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            if (hasConfiguredProperty(context, "privacy.guard.audit.dead-letter.observability.alert.webhook.url")) {
                return true;
            }
            return hasAnyConfiguredProperty(
                    context,
                    "privacy.guard.tenant.policies",
                    ".observability.dead-letter.alert.webhook.url"
            ) || hasAnyConfiguredProperty(
                    context,
                    "privacy.guard.audit.dead-letter.observability.alert.tenant.routes",
                    ".webhook.url"
            );
        }
    }

    static class TenantEmailTargetConfiguredCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            boolean globalFromConfigured = hasConfiguredProperty(
                    context,
                    "privacy.guard.audit.dead-letter.observability.alert.email.from"
            );
            boolean globalToConfigured = hasConfiguredProperty(
                    context,
                    "privacy.guard.audit.dead-letter.observability.alert.email.to"
            );
            if (globalFromConfigured && globalToConfigured) {
                return true;
            }
            boolean anyTenantEmailToConfigured = hasAnyConfiguredProperty(
                    context,
                    "privacy.guard.tenant.policies",
                    ".observability.dead-letter.alert.email.to"
            ) || hasAnyConfiguredProperty(
                    context,
                    "privacy.guard.audit.dead-letter.observability.alert.tenant.routes",
                    ".email.to"
            );
            if (!anyTenantEmailToConfigured) {
                return false;
            }
            return globalFromConfigured
                    || hasAnyConfiguredProperty(
                    context,
                    "privacy.guard.tenant.policies",
                    ".observability.dead-letter.alert.email.from"
            )
                    || hasAnyConfiguredProperty(
                    context,
                    "privacy.guard.audit.dead-letter.observability.alert.tenant.routes",
                    ".email.from"
            );
        }
    }

    static class TenantLoggingDeliveryConfiguredCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String globalLoggingEnabled = context.getEnvironment().getProperty(
                    "privacy.guard.audit.dead-letter.observability.alert.logging.enabled"
            );
            if (globalLoggingEnabled == null || Boolean.parseBoolean(globalLoggingEnabled)) {
                return true;
            }
            return hasAnyConfiguredBooleanProperty(
                    context,
                    "privacy.guard.tenant.policies",
                    ".observability.dead-letter.alert.logging.enabled",
                    true
            ) || hasAnyConfiguredBooleanProperty(
                    context,
                    "privacy.guard.audit.dead-letter.observability.alert.tenant.routes",
                    ".logging.enabled",
                    true
            );
        }
    }

    @Bean
    @ConditionalOnBean(PrivacyAuditDeadLetterStatsService.class)
    @ConditionalOnMissingBean
    public PrivacyAuditDeadLetterObservationService privacyAuditDeadLetterObservationService(
            PrivacyAuditDeadLetterStatsService statsService,
            PrivacyGuardProperties properties
    ) {
        PrivacyGuardProperties.Health health = properties.getAudit().getDeadLetter().getObservability().getHealth();
        return new PrivacyAuditDeadLetterObservationService(
                statsService,
                health.getWarningThreshold(),
                health.getDownThreshold()
        );
    }

    @Bean
    @ConditionalOnBean(PrivacyTenantAuditDeadLetterQueryService.class)
    @ConditionalOnMissingBean
    public PrivacyTenantAuditDeadLetterObservationService privacyTenantAuditDeadLetterObservationService(
            PrivacyTenantAuditDeadLetterQueryService tenantQueryService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantDeadLetterObservabilityPolicyResolver observabilityPolicyResolver,
            PrivacyGuardProperties properties
    ) {
        PrivacyGuardProperties.Health health = properties.getAudit().getDeadLetter().getObservability().getHealth();
        return new PrivacyTenantAuditDeadLetterObservationService(
                tenantQueryService,
                tenantProvider,
                observabilityPolicyResolver,
                health.getWarningThreshold(),
                health.getDownThreshold()
        );
    }

    @Bean(name = "privacyAuditDeadLettersHealthIndicator")
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    @ConditionalOnBean(PrivacyAuditDeadLetterObservationService.class)
    @ConditionalOnMissingBean(name = "privacyAuditDeadLettersHealthIndicator")
    @ConditionalOnProperty(
            prefix = "privacy.guard.audit.dead-letter.observability.health",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public PrivacyAuditDeadLetterHealthIndicator privacyAuditDeadLettersHealthIndicator(
            PrivacyAuditDeadLetterObservationService observationService
    ) {
        return new PrivacyAuditDeadLetterHealthIndicator(observationService);
    }

    @Bean(name = "privacyAuditTenantDeadLettersHealthIndicator")
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    @ConditionalOnBean(PrivacyTenantAuditDeadLetterObservationService.class)
    @ConditionalOnMissingBean(name = "privacyAuditTenantDeadLettersHealthIndicator")
    @ConditionalOnExpression(
            "'${privacy.guard.audit.dead-letter.observability.health.enabled:true}' == 'true' and " +
                    "'${privacy.guard.audit.dead-letter.observability.health.tenant-enabled:false}' == 'true'"
    )
    public PrivacyTenantAuditDeadLetterHealthIndicator privacyAuditTenantDeadLettersHealthIndicator(
            PrivacyTenantAuditDeadLetterObservationService observationService,
            PrivacyGuardProperties properties
    ) {
        return new PrivacyTenantAuditDeadLetterHealthIndicator(
                observationService,
                resolveTenantHealthIds(properties)
        );
    }

    @Bean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.binder.MeterBinder")
    @ConditionalOnBean(PrivacyAuditDeadLetterObservationService.class)
    @ConditionalOnMissingBean(PrivacyAuditDeadLetterMetricsBinder.class)
    @ConditionalOnProperty(
            prefix = "privacy.guard.audit.dead-letter.observability.metrics",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public PrivacyAuditDeadLetterMetricsBinder privacyAuditDeadLetterMetricsBinder(
            PrivacyAuditDeadLetterObservationService observationService
    ) {
        return new PrivacyAuditDeadLetterMetricsBinder(observationService);
    }

    @Bean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.binder.MeterBinder")
    @ConditionalOnBean(PrivacyTenantAuditDeadLetterObservationService.class)
    @ConditionalOnMissingBean(PrivacyTenantAuditDeadLetterMetricsBinder.class)
    @ConditionalOnExpression(
            "'${privacy.guard.audit.dead-letter.observability.metrics.enabled:true}' == 'true' and " +
                    "'${privacy.guard.audit.dead-letter.observability.metrics.tenant-enabled:false}' == 'true'"
    )
    public PrivacyTenantAuditDeadLetterMetricsBinder privacyTenantAuditDeadLetterMetricsBinder(
            PrivacyTenantAuditDeadLetterObservationService observationService,
            PrivacyGuardProperties properties
    ) {
        return new PrivacyTenantAuditDeadLetterMetricsBinder(
                observationService,
                resolveTenantMetricIds(properties)
        );
    }

    @Bean
    @ConditionalOnMissingBean(LoggingPrivacyAuditDeadLetterAlertCallback.class)
    @ConditionalOnExpression("'${privacy.guard.audit.dead-letter.observability.alert.enabled:false}' == 'true' and '${privacy.guard.audit.dead-letter.observability.alert.logging.enabled:true}' == 'true'")
    public LoggingPrivacyAuditDeadLetterAlertCallback loggingPrivacyAuditDeadLetterAlertCallback() {
        return new LoggingPrivacyAuditDeadLetterAlertCallback();
    }

    @Bean
    @ConditionalOnMissingBean(LoggingPrivacyTenantAuditDeadLetterAlertCallback.class)
    @ConditionalOnExpression(
            "'${privacy.guard.audit.dead-letter.observability.alert.enabled:false}' == 'true' and " +
                    "'${privacy.guard.audit.dead-letter.observability.alert.tenant.enabled:false}' == 'true'"
    )
    @org.springframework.context.annotation.Conditional(TenantLoggingDeliveryConfiguredCondition.class)
    public LoggingPrivacyTenantAuditDeadLetterAlertCallback loggingPrivacyTenantAuditDeadLetterAlertCallback(
            ObjectProvider<PrivacyTenantAuditTelemetry> telemetryProvider,
            PrivacyTenantDeadLetterAlertDeliveryPolicyResolver deliveryPolicyResolver,
            PrivacyGuardProperties properties
    ) {
        return new LoggingPrivacyTenantAuditDeadLetterAlertCallback(
                telemetryProvider.getIfAvailable(PrivacyTenantAuditTelemetry::noop),
                deliveryPolicyResolver,
                properties.getAudit().getDeadLetter().getObservability().getAlert().getLogging().isEnabled()
        );
    }

    @Bean
    @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookAlertTelemetry.class)
    public PrivacyAuditDeadLetterWebhookAlertTelemetry privacyAuditDeadLetterWebhookAlertTelemetry() {
        return PrivacyAuditDeadLetterWebhookAlertTelemetry.noop();
    }

    @Bean(name = "privacyAuditDeadLetterWebhookHttpClient")
    @ConditionalOnMissingBean(name = "privacyAuditDeadLetterWebhookHttpClient")
    @ConditionalOnExpression("'${privacy.guard.audit.dead-letter.observability.alert.enabled:false}' == 'true'")
    @org.springframework.context.annotation.Conditional(TenantWebhookTargetConfiguredCondition.class)
    HttpClient privacyAuditDeadLetterWebhookHttpClient(PrivacyGuardProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getAudit().getDeadLetter().getObservability().getAlert().getWebhook().getConnectTimeout())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookAlertCallback.class)
    @ConditionalOnBean({PrivacyAuditDeadLetterObservationService.class, ObjectMapper.class})
    @ConditionalOnExpression("'${privacy.guard.audit.dead-letter.observability.alert.enabled:false}' == 'true' and '${privacy.guard.audit.dead-letter.observability.alert.webhook.url:}' != ''")
    public PrivacyAuditDeadLetterWebhookAlertCallback privacyAuditDeadLetterWebhookAlertCallback(
            HttpClient privacyAuditDeadLetterWebhookHttpClient,
            ObjectMapper objectMapper,
            PrivacyGuardProperties properties,
            PrivacyAuditDeadLetterWebhookAlertTelemetry telemetry
    ) {
        return new PrivacyAuditDeadLetterWebhookAlertCallback(
                privacyAuditDeadLetterWebhookHttpClient,
                objectMapper,
                properties.getAudit().getDeadLetter().getObservability().getAlert().getWebhook(),
                telemetry
        );
    }

    @Bean
    @ConditionalOnMissingBean(TenantScopedPrivacyAuditDeadLetterWebhookAlertCallback.class)
    @ConditionalOnBean(ObjectMapper.class)
    @ConditionalOnExpression(
            "'${privacy.guard.audit.dead-letter.observability.alert.enabled:false}' == 'true' and " +
                    "'${privacy.guard.audit.dead-letter.observability.alert.tenant.enabled:false}' == 'true'"
    )
    @org.springframework.context.annotation.Conditional(TenantWebhookTargetConfiguredCondition.class)
    public PrivacyTenantAuditDeadLetterAlertCallback privacyTenantAuditDeadLetterWebhookAlertCallback(
            HttpClient privacyAuditDeadLetterWebhookHttpClient,
            ObjectMapper objectMapper,
            PrivacyGuardProperties properties,
            PrivacyAuditDeadLetterWebhookAlertTelemetry telemetry,
            ObjectProvider<PrivacyTenantAuditTelemetry> tenantTelemetryProvider,
            PrivacyTenantDeadLetterAlertRoutePolicyResolver routePolicyResolver,
            PrivacyTenantDeadLetterAlertDeliveryPolicyResolver deliveryPolicyResolver
    ) {
        return new TenantScopedPrivacyAuditDeadLetterWebhookAlertCallback(
                privacyAuditDeadLetterWebhookHttpClient,
                objectMapper,
                properties.getAudit().getDeadLetter().getObservability().getAlert().getWebhook(),
                telemetry,
                tenantTelemetryProvider.getIfAvailable(PrivacyTenantAuditTelemetry::noop),
                routePolicyResolver,
                deliveryPolicyResolver
        );
    }

    @Bean(name = "privacyAuditDeadLetterAlertExecutor", destroyMethod = "shutdown")
    @ConditionalOnBean({PrivacyAuditDeadLetterObservationService.class, PrivacyAuditDeadLetterAlertCallback.class})
    @ConditionalOnMissingBean(name = "privacyAuditDeadLetterAlertExecutor")
    @ConditionalOnProperty(
            prefix = "privacy.guard.audit.dead-letter.observability.alert",
            name = "enabled",
            havingValue = "true"
    )
    public ScheduledExecutorService privacyAuditDeadLetterAlertExecutor() {
        AtomicInteger threadCounter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("privacy-audit-dead-letter-alert-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    @Bean
    @ConditionalOnBean({PrivacyAuditDeadLetterObservationService.class, PrivacyAuditDeadLetterAlertCallback.class})
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "privacy.guard.audit.dead-letter.observability.alert",
            name = "enabled",
            havingValue = "true"
    )
    public PrivacyAuditDeadLetterAlertMonitor privacyAuditDeadLetterAlertMonitor(
            PrivacyAuditDeadLetterObservationService observationService,
            ObjectProvider<PrivacyAuditDeadLetterAlertCallback> callbacks,
            @Qualifier("privacyAuditDeadLetterAlertExecutor")
            ScheduledExecutorService privacyAuditDeadLetterAlertExecutor,
            PrivacyGuardProperties properties
    ) {
        PrivacyGuardProperties.Alert alert = properties.getAudit().getDeadLetter().getObservability().getAlert();
        List<PrivacyAuditDeadLetterAlertCallback> callbackList = callbacks.orderedStream().toList();
        return new PrivacyAuditDeadLetterAlertMonitor(
                observationService,
                callbackList,
                privacyAuditDeadLetterAlertExecutor,
                alert.getCheckInterval(),
                alert.isNotifyOnRecovery()
        );
    }

    @Bean(name = "privacyAuditTenantDeadLetterAlertExecutor", destroyMethod = "shutdown")
    @ConditionalOnBean({PrivacyTenantAuditDeadLetterObservationService.class, PrivacyTenantAuditDeadLetterAlertCallback.class})
    @ConditionalOnMissingBean(name = "privacyAuditTenantDeadLetterAlertExecutor")
    @ConditionalOnExpression(
            "'${privacy.guard.audit.dead-letter.observability.alert.enabled:false}' == 'true' and " +
                    "'${privacy.guard.audit.dead-letter.observability.alert.tenant.enabled:false}' == 'true'"
    )
    public ScheduledExecutorService privacyAuditTenantDeadLetterAlertExecutor() {
        AtomicInteger threadCounter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("privacy-audit-tenant-dead-letter-alert-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    @Bean
    @ConditionalOnBean({PrivacyTenantAuditDeadLetterObservationService.class, PrivacyTenantAuditDeadLetterAlertCallback.class})
    @ConditionalOnMissingBean
    @ConditionalOnExpression(
            "'${privacy.guard.audit.dead-letter.observability.alert.enabled:false}' == 'true' and " +
                    "'${privacy.guard.audit.dead-letter.observability.alert.tenant.enabled:false}' == 'true'"
    )
    public PrivacyTenantAuditDeadLetterAlertMonitor privacyTenantAuditDeadLetterAlertMonitor(
            PrivacyTenantAuditDeadLetterObservationService observationService,
            ObjectProvider<PrivacyTenantAuditDeadLetterAlertCallback> callbacks,
            @Qualifier("privacyAuditTenantDeadLetterAlertExecutor")
            ScheduledExecutorService privacyAuditTenantDeadLetterAlertExecutor,
            PrivacyTenantDeadLetterObservabilityPolicyResolver observabilityPolicyResolver,
            PrivacyTenantDeadLetterAlertMonitoringPolicyResolver monitoringPolicyResolver,
            ObjectProvider<PrivacyTenantAuditTelemetry> telemetryProvider,
            PrivacyGuardProperties properties
    ) {
        PrivacyGuardProperties.Alert alert = properties.getAudit().getDeadLetter().getObservability().getAlert();
        List<PrivacyTenantAuditDeadLetterAlertCallback> callbackList = callbacks.orderedStream().toList();
        return new PrivacyTenantAuditDeadLetterAlertMonitor(
                observationService,
                callbackList,
                resolveTenantAlertTenantIds(properties, monitoringPolicyResolver),
                privacyAuditTenantDeadLetterAlertExecutor,
                alert.getCheckInterval(),
                observabilityPolicyResolver,
                alert.isNotifyOnRecovery(),
                telemetryProvider.getIfAvailable(PrivacyTenantAuditTelemetry::noop)
        );
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class WebhookTelemetryConfiguration {

        @Bean
        @Primary
        @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
        PrivacyAuditDeadLetterWebhookAlertTelemetry micrometerPrivacyAuditDeadLetterWebhookAlertTelemetry(
                io.micrometer.core.instrument.MeterRegistry meterRegistry
        ) {
            return new MicrometerPrivacyAuditDeadLetterWebhookAlertTelemetry(meterRegistry);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.mail.javamail.JavaMailSender")
    static class EmailAlertConfiguration {

        @Bean
        @ConditionalOnMissingBean(PrivacyAuditDeadLetterEmailAlertCallback.class)
        @ConditionalOnBean(org.springframework.mail.javamail.JavaMailSender.class)
        @ConditionalOnExpression("'${privacy.guard.audit.dead-letter.observability.alert.enabled:false}' == 'true' and '${privacy.guard.audit.dead-letter.observability.alert.email.to:}' != '' and '${privacy.guard.audit.dead-letter.observability.alert.email.from:}' != ''")
        PrivacyAuditDeadLetterEmailAlertCallback privacyAuditDeadLetterEmailAlertCallback(
                org.springframework.mail.javamail.JavaMailSender javaMailSender,
                PrivacyGuardProperties properties
        ) {
            return new PrivacyAuditDeadLetterEmailAlertCallback(
                    javaMailSender,
                    properties.getAudit().getDeadLetter().getObservability().getAlert().getEmail()
            );
        }

        @Bean
        @ConditionalOnMissingBean(TenantScopedPrivacyAuditDeadLetterEmailAlertCallback.class)
        @ConditionalOnBean(org.springframework.mail.javamail.JavaMailSender.class)
        @ConditionalOnExpression(
                "'${privacy.guard.audit.dead-letter.observability.alert.enabled:false}' == 'true' and " +
                        "'${privacy.guard.audit.dead-letter.observability.alert.tenant.enabled:false}' == 'true'"
        )
        @org.springframework.context.annotation.Conditional(TenantEmailTargetConfiguredCondition.class)
        PrivacyTenantAuditDeadLetterAlertCallback privacyTenantAuditDeadLetterEmailAlertCallback(
                org.springframework.mail.javamail.JavaMailSender javaMailSender,
                ObjectProvider<PrivacyTenantAuditTelemetry> telemetryProvider,
                PrivacyGuardProperties properties,
                PrivacyTenantDeadLetterAlertRoutePolicyResolver routePolicyResolver,
                PrivacyTenantDeadLetterAlertDeliveryPolicyResolver deliveryPolicyResolver
        ) {
            return new TenantScopedPrivacyAuditDeadLetterEmailAlertCallback(
                    javaMailSender,
                    properties.getAudit().getDeadLetter().getObservability().getAlert().getEmail(),
                    telemetryProvider.getIfAvailable(PrivacyTenantAuditTelemetry::noop),
                    routePolicyResolver,
                    deliveryPolicyResolver
            );
        }
    }

    private static List<String> resolveTenantMetricIds(PrivacyGuardProperties properties) {
        LinkedHashSet<String> tenantIds = new LinkedHashSet<>();
        PrivacyGuardProperties.Metrics metrics = properties.getAudit().getDeadLetter().getObservability().getMetrics();
        tenantIds.addAll(metrics.getTenantIds());

        String defaultTenant = properties.getTenant().getDefaultTenant();
        if (defaultTenant != null && !defaultTenant.isBlank()) {
            tenantIds.add(defaultTenant.trim());
        }
        tenantIds.addAll(properties.getTenant().getPolicies().keySet());
        tenantIds.removeIf(value -> value == null || value.isBlank());
        return List.copyOf(tenantIds);
    }

    private static List<String> resolveTenantHealthIds(PrivacyGuardProperties properties) {
        LinkedHashSet<String> tenantIds = new LinkedHashSet<>();
        PrivacyGuardProperties.Health health = properties.getAudit().getDeadLetter().getObservability().getHealth();
        tenantIds.addAll(health.getTenantIds());
        tenantIds.addAll(resolveTenantMetricIds(properties));
        tenantIds.removeIf(value -> value == null || value.isBlank());
        return List.copyOf(tenantIds);
    }

    static List<String> resolveTenantAlertTenantIds(
            PrivacyGuardProperties properties,
            PrivacyTenantDeadLetterAlertMonitoringPolicyResolver monitoringPolicyResolver
    ) {
        LinkedHashSet<String> tenantIds = new LinkedHashSet<>();
        List<String> configuredTenantIds = properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().getTenantIds();
        tenantIds.addAll(configuredTenantIds);
        boolean hasExplicitTenantIds = !tenantIds.isEmpty();
        if (!hasExplicitTenantIds) {
            tenantIds.addAll(resolveTenantMetricIds(properties));
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>(tenantIds);
        candidates.addAll(resolveTenantMetricIds(properties));
        candidates.addAll(properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().getRoutes().keySet());
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            PrivacyTenantDeadLetterAlertMonitoringPolicy policy = monitoringPolicyResolver == null
                    ? PrivacyTenantDeadLetterAlertMonitoringPolicy.none()
                    : monitoringPolicyResolver.resolve(candidate);
            if (policy == null || !policy.hasOverrides()) {
                continue;
            }
            if (policy.resolveEnabled(!hasExplicitTenantIds || tenantIds.contains(candidate))) {
                tenantIds.add(candidate);
            } else {
                tenantIds.remove(candidate);
            }
        }
        tenantIds.removeIf(value -> value == null || value.isBlank());
        return List.copyOf(tenantIds);
    }

    private static boolean hasConfiguredProperty(ConditionContext context, String propertyName) {
        return org.springframework.util.StringUtils.hasText(context.getEnvironment().getProperty(propertyName));
    }

    private static boolean hasAnyConfiguredProperty(ConditionContext context, String rootPrefix, String propertySuffix) {
        if (!(context.getEnvironment() instanceof ConfigurableEnvironment environment)) {
            return false;
        }
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource)) {
                continue;
            }
            for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                if (!propertyName.startsWith(rootPrefix) || !propertyName.contains(propertySuffix)) {
                    continue;
                }
                Object value = enumerablePropertySource.getProperty(propertyName);
                if (value != null && org.springframework.util.StringUtils.hasText(value.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAnyConfiguredBooleanProperty(
            ConditionContext context,
            String rootPrefix,
            String propertySuffix,
            boolean expectedValue
    ) {
        if (!(context.getEnvironment() instanceof ConfigurableEnvironment environment)) {
            return false;
        }
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource)) {
                continue;
            }
            for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                if (!propertyName.startsWith(rootPrefix) || !propertyName.contains(propertySuffix)) {
                    continue;
                }
                Object value = enumerablePropertySource.getProperty(propertyName);
                if (value != null && Boolean.parseBoolean(value.toString()) == expectedValue) {
                    return true;
                }
            }
        }
        return false;
    }
}
