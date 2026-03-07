/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class PrivacyAuditService {

    private final PrivacyAuditPublisher auditPublisher;
    private final PrivacyLogSanitizer logSanitizer;

    public PrivacyAuditService(PrivacyAuditPublisher auditPublisher, PrivacyLogSanitizer logSanitizer) {
        this.auditPublisher = auditPublisher;
        this.logSanitizer = logSanitizer;
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
        if (details == null || details.isEmpty()) {
            return Map.of();
        }

        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : details.entrySet()) {
            Object value = entry.getValue();
            sanitized.put(entry.getKey(), value == null ? null : sanitize(String.valueOf(value)));
        }
        return sanitized;
    }

    private String sanitize(String value) {
        return value == null ? null : logSanitizer.sanitize(value);
    }
}