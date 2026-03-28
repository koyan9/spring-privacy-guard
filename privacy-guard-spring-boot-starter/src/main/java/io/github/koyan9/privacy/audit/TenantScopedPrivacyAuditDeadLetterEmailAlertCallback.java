/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

public class TenantScopedPrivacyAuditDeadLetterEmailAlertCallback implements PrivacyTenantAuditDeadLetterAlertCallback {

    private final JavaMailSender mailSender;
    private final PrivacyGuardProperties.AlertEmail defaultProperties;
    private final PrivacyTenantAuditTelemetry telemetry;
    private final PrivacyTenantDeadLetterAlertRoutePolicyResolver routePolicyResolver;
    private final PrivacyTenantDeadLetterAlertDeliveryPolicyResolver deliveryPolicyResolver;

    public TenantScopedPrivacyAuditDeadLetterEmailAlertCallback(
            JavaMailSender mailSender,
            PrivacyGuardProperties.AlertEmail defaultProperties,
            PrivacyTenantAuditTelemetry telemetry,
            PrivacyTenantDeadLetterAlertRoutePolicyResolver routePolicyResolver
    ) {
        this(
                mailSender,
                defaultProperties,
                telemetry,
                routePolicyResolver,
                PrivacyTenantDeadLetterAlertDeliveryPolicyResolver.noop()
        );
    }

    public TenantScopedPrivacyAuditDeadLetterEmailAlertCallback(
            JavaMailSender mailSender,
            PrivacyGuardProperties.AlertEmail defaultProperties,
            PrivacyTenantAuditTelemetry telemetry,
            PrivacyTenantDeadLetterAlertRoutePolicyResolver routePolicyResolver,
            PrivacyTenantDeadLetterAlertDeliveryPolicyResolver deliveryPolicyResolver
    ) {
        this.mailSender = mailSender;
        this.defaultProperties = defaultProperties;
        this.telemetry = telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry;
        this.routePolicyResolver = routePolicyResolver == null
                ? PrivacyTenantDeadLetterAlertRoutePolicyResolver.noop()
                : routePolicyResolver;
        this.deliveryPolicyResolver = deliveryPolicyResolver == null
                ? PrivacyTenantDeadLetterAlertDeliveryPolicyResolver.noop()
                : deliveryPolicyResolver;
    }

    @Override
    public boolean supportsTenant(String tenantId) {
        return resolveEmailEnabled(tenantId) && resolveProperties(tenantId) != null;
    }

    @Override
    public void handle(String tenantId, PrivacyAuditDeadLetterAlertEvent event) {
        if (!resolveEmailEnabled(tenantId)) {
            return;
        }
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
        PrivacyTenantDeadLetterAlertRoutePolicy routePolicy = routePolicyResolver.resolve(tenantId);
        PrivacyTenantDeadLetterAlertEmailPolicy route = routePolicy == null
                ? PrivacyTenantDeadLetterAlertEmailPolicy.none()
                : routePolicy.email();
        boolean routeHasTarget = route != null && StringUtils.hasText(route.to());
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
        resolved.setFrom(firstNonNull(route.from(), defaultProperties.getFrom()));
        resolved.setTo(firstNonNull(route.to(), defaultProperties.getTo()));
        resolved.setSubjectPrefix(firstNonNull(route.subjectPrefix(), defaultProperties.getSubjectPrefix()));
        if (!StringUtils.hasText(resolved.getFrom()) || !StringUtils.hasText(resolved.getTo())) {
            return null;
        }
        return resolved;
    }

    private boolean resolveEmailEnabled(String tenantId) {
        PrivacyTenantDeadLetterAlertDeliveryPolicy policy = deliveryPolicyResolver.resolve(tenantId);
        if (policy == null) {
            policy = PrivacyTenantDeadLetterAlertDeliveryPolicy.none();
        }
        return policy.resolveEmailEnabled(true);
    }

    private <T> T firstNonNull(T routeValue, T defaultValue) {
        return routeValue != null ? routeValue : defaultValue;
    }
}
