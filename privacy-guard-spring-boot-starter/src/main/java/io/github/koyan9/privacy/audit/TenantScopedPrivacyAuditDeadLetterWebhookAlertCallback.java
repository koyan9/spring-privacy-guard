/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
import org.springframework.util.StringUtils;

import java.net.http.HttpClient;
import java.util.Map;

public class TenantScopedPrivacyAuditDeadLetterWebhookAlertCallback implements PrivacyTenantAuditDeadLetterAlertCallback {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PrivacyGuardProperties.AlertWebhook defaultProperties;
    private final PrivacyAuditDeadLetterWebhookAlertTelemetry telemetry;
    private final PrivacyTenantAuditTelemetry tenantTelemetry;
    private final Map<String, PrivacyGuardProperties.AlertTenantRoute> routes;

    public TenantScopedPrivacyAuditDeadLetterWebhookAlertCallback(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            PrivacyGuardProperties.AlertWebhook defaultProperties,
            PrivacyAuditDeadLetterWebhookAlertTelemetry telemetry,
            PrivacyTenantAuditTelemetry tenantTelemetry,
            Map<String, PrivacyGuardProperties.AlertTenantRoute> routes
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.defaultProperties = defaultProperties;
        this.telemetry = telemetry == null ? PrivacyAuditDeadLetterWebhookAlertTelemetry.noop() : telemetry;
        this.tenantTelemetry = tenantTelemetry == null ? PrivacyTenantAuditTelemetry.noop() : tenantTelemetry;
        this.routes = routes == null ? Map.of() : Map.copyOf(routes);
    }

    @Override
    public boolean supportsTenant(String tenantId) {
        return resolveProperties(tenantId) != null;
    }

    @Override
    public void handle(String tenantId, PrivacyAuditDeadLetterAlertEvent event) {
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
        PrivacyGuardProperties.AlertTenantRouteWebhook route = tenantRouteWebhook(tenantId);
        boolean routeHasTarget = route != null && StringUtils.hasText(route.getUrl());
        boolean defaultHasTarget = defaultProperties != null && StringUtils.hasText(defaultProperties.getUrl());
        if (!routeHasTarget && !defaultHasTarget) {
            return null;
        }

        if (!routeHasTarget) {
            return defaultProperties;
        }

        PrivacyGuardProperties.AlertWebhook resolved = new PrivacyGuardProperties.AlertWebhook();
        resolved.setUrl(route.getUrl());
        resolved.setBearerToken(firstNonNull(route.getBearerToken(), defaultProperties.getBearerToken()));
        resolved.setSignatureSecret(firstNonNull(route.getSignatureSecret(), defaultProperties.getSignatureSecret()));
        resolved.setSignatureAlgorithm(firstNonNull(route.getSignatureAlgorithm(), defaultProperties.getSignatureAlgorithm()));
        resolved.setSignatureHeader(firstNonNull(route.getSignatureHeader(), defaultProperties.getSignatureHeader()));
        resolved.setTimestampHeader(firstNonNull(route.getTimestampHeader(), defaultProperties.getTimestampHeader()));
        resolved.setNonceHeader(firstNonNull(route.getNonceHeader(), defaultProperties.getNonceHeader()));
        resolved.setMaxAttempts(firstNonNull(route.getMaxAttempts(), defaultProperties.getMaxAttempts()));
        resolved.setBackoff(firstNonNull(route.getBackoff(), defaultProperties.getBackoff()));
        resolved.setBackoffPolicy(firstNonNull(route.getBackoffPolicy(), defaultProperties.getBackoffPolicy()));
        resolved.setMaxBackoff(firstNonNull(route.getMaxBackoff(), defaultProperties.getMaxBackoff()));
        resolved.setJitter(firstNonNull(route.getJitter(), defaultProperties.getJitter()));
        resolved.setConnectTimeout(firstNonNull(route.getConnectTimeout(), defaultProperties.getConnectTimeout()));
        resolved.setReadTimeout(firstNonNull(route.getReadTimeout(), defaultProperties.getReadTimeout()));
        return resolved;
    }

    private PrivacyGuardProperties.AlertTenantRouteWebhook tenantRouteWebhook(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        PrivacyGuardProperties.AlertTenantRoute route = routes.get(tenantId.trim());
        return route == null ? null : route.getWebhook();
    }

    private <T> T firstNonNull(T routeValue, T defaultValue) {
        return routeValue != null ? routeValue : defaultValue;
    }
}
