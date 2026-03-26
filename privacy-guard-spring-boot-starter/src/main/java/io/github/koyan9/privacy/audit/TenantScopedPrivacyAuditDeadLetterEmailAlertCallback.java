/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

import java.util.Map;

public class TenantScopedPrivacyAuditDeadLetterEmailAlertCallback implements PrivacyTenantAuditDeadLetterAlertCallback {

    private final JavaMailSender mailSender;
    private final PrivacyGuardProperties.AlertEmail defaultProperties;
    private final PrivacyTenantAuditTelemetry telemetry;
    private final Map<String, PrivacyGuardProperties.AlertTenantRoute> routes;

    public TenantScopedPrivacyAuditDeadLetterEmailAlertCallback(
            JavaMailSender mailSender,
            PrivacyGuardProperties.AlertEmail defaultProperties,
            PrivacyTenantAuditTelemetry telemetry,
            Map<String, PrivacyGuardProperties.AlertTenantRoute> routes
    ) {
        this.mailSender = mailSender;
        this.defaultProperties = defaultProperties;
        this.telemetry = telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry;
        this.routes = routes == null ? Map.of() : Map.copyOf(routes);
    }

    @Override
    public boolean supportsTenant(String tenantId) {
        return resolveProperties(tenantId) != null;
    }

    @Override
    public void handle(String tenantId, PrivacyAuditDeadLetterAlertEvent event) {
        PrivacyGuardProperties.AlertEmail properties = resolveProperties(tenantId);
        if (properties == null) {
            return;
        }
        try {
            new PrivacyAuditDeadLetterEmailAlertCallback(mailSender, properties).handle(tenantId, event);
            telemetry.recordAlertDelivery(tenantId, "email", "success");
        } catch (RuntimeException ex) {
            telemetry.recordAlertDelivery(tenantId, "email", "failure");
            throw ex;
        }
    }

    private PrivacyGuardProperties.AlertEmail resolveProperties(String tenantId) {
        PrivacyGuardProperties.AlertTenantRouteEmail route = tenantRouteEmail(tenantId);
        boolean routeHasTarget = route != null && StringUtils.hasText(route.getTo());
        boolean defaultHasTarget = defaultProperties != null
                && StringUtils.hasText(defaultProperties.getFrom())
                && StringUtils.hasText(defaultProperties.getTo());
        if (!routeHasTarget && !defaultHasTarget) {
            return null;
        }

        if (!routeHasTarget) {
            return defaultProperties;
        }

        PrivacyGuardProperties.AlertEmail resolved = new PrivacyGuardProperties.AlertEmail();
        resolved.setFrom(firstNonNull(route.getFrom(), defaultProperties.getFrom()));
        resolved.setTo(firstNonNull(route.getTo(), defaultProperties.getTo()));
        resolved.setSubjectPrefix(firstNonNull(route.getSubjectPrefix(), defaultProperties.getSubjectPrefix()));
        if (!StringUtils.hasText(resolved.getFrom()) || !StringUtils.hasText(resolved.getTo())) {
            return null;
        }
        return resolved;
    }

    private PrivacyGuardProperties.AlertTenantRouteEmail tenantRouteEmail(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        PrivacyGuardProperties.AlertTenantRoute route = routes.get(tenantId.trim());
        return route == null ? null : route.getEmail();
    }

    private <T> T firstNonNull(T routeValue, T defaultValue) {
        return routeValue != null ? routeValue : defaultValue;
    }
}
