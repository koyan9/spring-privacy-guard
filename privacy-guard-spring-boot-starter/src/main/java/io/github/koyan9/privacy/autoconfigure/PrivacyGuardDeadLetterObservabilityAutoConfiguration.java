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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
                    "'${privacy.guard.audit.dead-letter.observability.alert.tenant.enabled:false}' == 'true' and " +
                    "'${privacy.guard.audit.dead-letter.observability.alert.logging.enabled:true}' == 'true'"
    )
    public LoggingPrivacyTenantAuditDeadLetterAlertCallback loggingPrivacyTenantAuditDeadLetterAlertCallback(
            ObjectProvider<PrivacyTenantAuditTelemetry> telemetryProvider
    ) {
        return new LoggingPrivacyTenantAuditDeadLetterAlertCallback(
                telemetryProvider.getIfAvailable(PrivacyTenantAuditTelemetry::noop)
        );
    }

    @Bean
    @ConditionalOnMissingBean(PrivacyAuditDeadLetterWebhookAlertTelemetry.class)
    public PrivacyAuditDeadLetterWebhookAlertTelemetry privacyAuditDeadLetterWebhookAlertTelemetry() {
        return PrivacyAuditDeadLetterWebhookAlertTelemetry.noop();
    }

    @Bean(name = "privacyAuditDeadLetterWebhookHttpClient")
    @ConditionalOnMissingBean(name = "privacyAuditDeadLetterWebhookHttpClient")
    @ConditionalOnExpression("'${privacy.guard.audit.dead-letter.observability.alert.enabled:false}' == 'true' and '${privacy.guard.audit.dead-letter.observability.alert.webhook.url:}' != ''")
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
    @ConditionalOnBean(PrivacyAuditDeadLetterWebhookAlertCallback.class)
    @ConditionalOnExpression(
            "'${privacy.guard.audit.dead-letter.observability.alert.enabled:false}' == 'true' and " +
                    "'${privacy.guard.audit.dead-letter.observability.alert.tenant.enabled:false}' == 'true' and " +
                    "'${privacy.guard.audit.dead-letter.observability.alert.webhook.url:}' != ''"
    )
    public PrivacyTenantAuditDeadLetterAlertCallback privacyTenantAuditDeadLetterWebhookAlertCallback(
            HttpClient privacyAuditDeadLetterWebhookHttpClient,
            ObjectMapper objectMapper,
            PrivacyGuardProperties properties,
            PrivacyAuditDeadLetterWebhookAlertTelemetry telemetry,
            ObjectProvider<PrivacyTenantAuditTelemetry> tenantTelemetryProvider
    ) {
        return new TenantScopedPrivacyAuditDeadLetterWebhookAlertCallback(
                privacyAuditDeadLetterWebhookHttpClient,
                objectMapper,
                properties.getAudit().getDeadLetter().getObservability().getAlert().getWebhook(),
                telemetry,
                tenantTelemetryProvider.getIfAvailable(PrivacyTenantAuditTelemetry::noop),
                properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().getRoutes()
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
            ObjectProvider<PrivacyTenantAuditTelemetry> telemetryProvider,
            PrivacyGuardProperties properties
    ) {
        PrivacyGuardProperties.Alert alert = properties.getAudit().getDeadLetter().getObservability().getAlert();
        List<PrivacyTenantAuditDeadLetterAlertCallback> callbackList = callbacks.orderedStream().toList();
        return new PrivacyTenantAuditDeadLetterAlertMonitor(
                observationService,
                callbackList,
                resolveTenantAlertTenantIds(properties),
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
        @ConditionalOnBean(PrivacyAuditDeadLetterEmailAlertCallback.class)
        @ConditionalOnExpression(
                "'${privacy.guard.audit.dead-letter.observability.alert.enabled:false}' == 'true' and " +
                        "'${privacy.guard.audit.dead-letter.observability.alert.tenant.enabled:false}' == 'true' and " +
                        "'${privacy.guard.audit.dead-letter.observability.alert.email.to:}' != '' and " +
                        "'${privacy.guard.audit.dead-letter.observability.alert.email.from:}' != ''"
        )
        PrivacyTenantAuditDeadLetterAlertCallback privacyTenantAuditDeadLetterEmailAlertCallback(
                org.springframework.mail.javamail.JavaMailSender javaMailSender,
                ObjectProvider<PrivacyTenantAuditTelemetry> telemetryProvider,
                PrivacyGuardProperties properties
        ) {
            return new TenantScopedPrivacyAuditDeadLetterEmailAlertCallback(
                    javaMailSender,
                    properties.getAudit().getDeadLetter().getObservability().getAlert().getEmail(),
                    telemetryProvider.getIfAvailable(PrivacyTenantAuditTelemetry::noop),
                    properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().getRoutes()
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

    private static List<String> resolveTenantAlertTenantIds(PrivacyGuardProperties properties) {
        LinkedHashSet<String> tenantIds = new LinkedHashSet<>();
        tenantIds.addAll(properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().getTenantIds());
        if (tenantIds.isEmpty()) {
            tenantIds.addAll(resolveTenantMetricIds(properties));
        }
        tenantIds.removeIf(value -> value == null || value.isBlank());
        return List.copyOf(tenantIds);
    }
}
