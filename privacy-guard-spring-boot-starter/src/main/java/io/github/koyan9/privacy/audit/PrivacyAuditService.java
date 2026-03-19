/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantProvider;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class PrivacyAuditService {

    private final PrivacyAuditPublisher auditPublisher;
    private final PrivacyLogSanitizer logSanitizer;
    private final PrivacyTenantProvider tenantProvider;
    private final PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver;

    public PrivacyAuditService(PrivacyAuditPublisher auditPublisher, PrivacyLogSanitizer logSanitizer) {
        this(auditPublisher, logSanitizer, PrivacyTenantProvider.noop(), PrivacyTenantAuditPolicyResolver.noop());
    }

    public PrivacyAuditService(
            PrivacyAuditPublisher auditPublisher,
            PrivacyLogSanitizer logSanitizer,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver
    ) {
        this.auditPublisher = auditPublisher;
        this.logSanitizer = logSanitizer;
        this.tenantProvider = tenantProvider == null ? PrivacyTenantProvider.noop() : tenantProvider;
        this.tenantAuditPolicyResolver = tenantAuditPolicyResolver == null
                ? PrivacyTenantAuditPolicyResolver.noop()
                : tenantAuditPolicyResolver;
    }

    public PrivacyAuditEvent record(
            String action,
            String resourceType,
            String resourceId,
            String actor,
            String outcome,
            Map<String, ?> details
    ) {
        PrivacyAuditEvent event = new PrivacyAuditEvent(
                Instant.now(),
                sanitize(action),
                sanitize(resourceType),
                sanitize(resourceId),
                sanitize(actor),
                sanitize(outcome),
                sanitizeDetails(details)
        );
        auditPublisher.publish(event);
        return event;
    }

    private Map<String, String> sanitizeDetails(Map<String, ?> details) {
        String tenantId = currentTenantId();
        PrivacyTenantAuditPolicy tenantAuditPolicy = resolveAuditPolicy(tenantId);
        if (details == null || details.isEmpty()) {
            return enrichWithTenantId(Map.of(), tenantId, tenantAuditPolicy);
        }

        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : details.entrySet()) {
            if (!tenantAuditPolicy.keepsDetailKey(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            sanitized.put(entry.getKey(), value == null ? null : sanitize(String.valueOf(value)));
        }
        return enrichWithTenantId(sanitized, tenantId, tenantAuditPolicy);
    }

    private String sanitize(String value) {
        return value == null ? null : logSanitizer.sanitize(value);
    }

    private String currentTenantId() {
        String tenantId = tenantProvider.currentTenantId();
        return tenantId == null || tenantId.isBlank() ? null : tenantId.trim();
    }

    private PrivacyTenantAuditPolicy resolveAuditPolicy(String tenantId) {
        PrivacyTenantAuditPolicy policy = tenantAuditPolicyResolver.resolve(tenantId);
        return policy == null ? PrivacyTenantAuditPolicy.none() : policy;
    }

    private Map<String, String> enrichWithTenantId(
            Map<String, String> details,
            String tenantId,
            PrivacyTenantAuditPolicy tenantAuditPolicy
    ) {
        if (!tenantAuditPolicy.attachTenantId() || tenantId == null || tenantId.isBlank()) {
            return details.isEmpty() ? Map.of() : Map.copyOf(details);
        }
        Map<String, String> enriched = new LinkedHashMap<>(details);
        enriched.putIfAbsent(tenantAuditPolicy.tenantDetailKey(), tenantId);
        return Map.copyOf(enriched);
    }
}
