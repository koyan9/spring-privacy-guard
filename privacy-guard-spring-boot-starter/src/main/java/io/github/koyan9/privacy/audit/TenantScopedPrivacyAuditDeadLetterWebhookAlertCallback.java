/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
import org.springframework.util.StringUtils;

import java.net.http.HttpClient;

public class TenantScopedPrivacyAuditDeadLetterWebhookAlertCallback implements PrivacyTenantAuditDeadLetterAlertCallback {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PrivacyGuardProperties.AlertWebhook defaultProperties;
    private final PrivacyAuditDeadLetterWebhookAlertTelemetry telemetry;
    private final PrivacyTenantAuditTelemetry tenantTelemetry;
    private final PrivacyTenantDeadLetterAlertRoutePolicyResolver routePolicyResolver;
    private final PrivacyTenantDeadLetterAlertDeliveryPolicyResolver deliveryPolicyResolver;

    public TenantScopedPrivacyAuditDeadLetterWebhookAlertCallback(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            PrivacyGuardProperties.AlertWebhook defaultProperties,
            PrivacyAuditDeadLetterWebhookAlertTelemetry telemetry,
            PrivacyTenantAuditTelemetry tenantTelemetry,
            PrivacyTenantDeadLetterAlertRoutePolicyResolver routePolicyResolver
    ) {
        this(
                httpClient,
                objectMapper,
                defaultProperties,
                telemetry,
                tenantTelemetry,
                routePolicyResolver,
                PrivacyTenantDeadLetterAlertDeliveryPolicyResolver.noop()
        );
    }

    public TenantScopedPrivacyAuditDeadLetterWebhookAlertCallback(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            PrivacyGuardProperties.AlertWebhook defaultProperties,
            PrivacyAuditDeadLetterWebhookAlertTelemetry telemetry,
            PrivacyTenantAuditTelemetry tenantTelemetry,
            PrivacyTenantDeadLetterAlertRoutePolicyResolver routePolicyResolver,
            PrivacyTenantDeadLetterAlertDeliveryPolicyResolver deliveryPolicyResolver
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.defaultProperties = defaultProperties;
        this.telemetry = telemetry == null ? PrivacyAuditDeadLetterWebhookAlertTelemetry.noop() : telemetry;
        this.tenantTelemetry = tenantTelemetry == null ? PrivacyTenantAuditTelemetry.noop() : tenantTelemetry;
        this.routePolicyResolver = routePolicyResolver == null
                ? PrivacyTenantDeadLetterAlertRoutePolicyResolver.noop()
                : routePolicyResolver;
        this.deliveryPolicyResolver = deliveryPolicyResolver == null
                ? PrivacyTenantDeadLetterAlertDeliveryPolicyResolver.noop()
                : deliveryPolicyResolver;
    }

    @Override
    public boolean supportsTenant(String tenantId) {
        return resolveWebhookEnabled(tenantId) && resolveProperties(tenantId) != null;
    }

    @Override
    public void handle(String tenantId, PrivacyAuditDeadLetterAlertEvent event) {
        if (!resolveWebhookEnabled(tenantId)) {
            return;
        }
        PrivacyGuardProperties.AlertWebhook properties = resolveProperties(tenantId);
        if (properties == null) {
            return;
        }
        try {
            new PrivacyAuditDeadLetterWebhookAlertCallback(
                    httpClient,
                    objectMapper,
                    properties,
                    telemetry
            ).handle(tenantId, event);
            tenantTelemetry.recordAlertDelivery(tenantId, "webhook", "success");
        } catch (RuntimeException ex) {
            tenantTelemetry.recordAlertDelivery(tenantId, "webhook", "failure");
            throw ex;
        }
    }

    private PrivacyGuardProperties.AlertWebhook resolveProperties(String tenantId) {
        PrivacyTenantDeadLetterAlertRoutePolicy routePolicy = routePolicyResolver.resolve(tenantId);
        PrivacyTenantDeadLetterAlertWebhookPolicy route = routePolicy == null
                ? PrivacyTenantDeadLetterAlertWebhookPolicy.none()
                : routePolicy.webhook();
        boolean routeHasTarget = route != null && StringUtils.hasText(route.url());
        boolean defaultHasTarget = defaultProperties != null && StringUtils.hasText(defaultProperties.getUrl());
        if (!routeHasTarget && !defaultHasTarget) {
            return null;
        }

        if (!routeHasTarget) {
            return defaultProperties;
        }

        PrivacyGuardProperties.AlertWebhook resolved = new PrivacyGuardProperties.AlertWebhook();
        resolved.setUrl(route.url());
        resolved.setBearerToken(firstNonNull(route.bearerToken(), defaultProperties.getBearerToken()));
        resolved.setSignatureSecret(firstNonNull(route.signatureSecret(), defaultProperties.getSignatureSecret()));
        resolved.setSignatureAlgorithm(firstNonNull(route.signatureAlgorithm(), defaultProperties.getSignatureAlgorithm()));
        resolved.setSignatureHeader(firstNonNull(route.signatureHeader(), defaultProperties.getSignatureHeader()));
        resolved.setTimestampHeader(firstNonNull(route.timestampHeader(), defaultProperties.getTimestampHeader()));
        resolved.setNonceHeader(firstNonNull(route.nonceHeader(), defaultProperties.getNonceHeader()));
        resolved.setMaxAttempts(firstNonNull(route.maxAttempts(), defaultProperties.getMaxAttempts()));
        resolved.setBackoff(firstNonNull(route.backoff(), defaultProperties.getBackoff()));
        resolved.setBackoffPolicy(firstNonNull(toPropertiesBackoffPolicy(route.backoffPolicy()), defaultProperties.getBackoffPolicy()));
        resolved.setMaxBackoff(firstNonNull(route.maxBackoff(), defaultProperties.getMaxBackoff()));
        resolved.setJitter(firstNonNull(route.jitter(), defaultProperties.getJitter()));
        resolved.setConnectTimeout(firstNonNull(route.connectTimeout(), defaultProperties.getConnectTimeout()));
        resolved.setReadTimeout(firstNonNull(route.readTimeout(), defaultProperties.getReadTimeout()));
        return resolved;
    }

    private boolean resolveWebhookEnabled(String tenantId) {
        PrivacyTenantDeadLetterAlertDeliveryPolicy policy = deliveryPolicyResolver.resolve(tenantId);
        if (policy == null) {
            policy = PrivacyTenantDeadLetterAlertDeliveryPolicy.none();
        }
        return policy.resolveWebhookEnabled(true);
    }

    private PrivacyGuardProperties.AlertWebhook.BackoffPolicy toPropertiesBackoffPolicy(
            PrivacyTenantDeadLetterAlertWebhookPolicy.BackoffPolicy backoffPolicy
    ) {
        if (backoffPolicy == null) {
            return null;
        }
        return switch (backoffPolicy) {
            case FIXED -> PrivacyGuardProperties.AlertWebhook.BackoffPolicy.FIXED;
            case EXPONENTIAL -> PrivacyGuardProperties.AlertWebhook.BackoffPolicy.EXPONENTIAL;
        };
    }

    private <T> T firstNonNull(T routeValue, T defaultValue) {
        return routeValue != null ? routeValue : defaultValue;
    }
}
