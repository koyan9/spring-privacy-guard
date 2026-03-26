/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PrivacyAuditDeadLetterAlertPayloadFactory {

    private PrivacyAuditDeadLetterAlertPayloadFactory() {
    }

    public static Map<String, Object> createPayload(PrivacyAuditDeadLetterAlertEvent event) {
        return createPayload(event, null);
    }

    public static Map<String, Object> createPayload(PrivacyAuditDeadLetterAlertEvent event, String tenantId) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (tenantId != null && !tenantId.isBlank()) {
            body.put("tenantId", tenantId.trim());
        }
        body.put("occurredAt", event.occurredAt().toString());
        body.put("recovery", event.recovery());
        body.put("state", event.currentSnapshot().state().name());
        body.put("previousState", event.previousSnapshot() == null ? null : event.previousSnapshot().state().name());
        body.put("total", event.currentSnapshot().total());
        body.put("warningThreshold", event.currentSnapshot().warningThreshold());
        body.put("downThreshold", event.currentSnapshot().downThreshold());
        body.put("byAction", event.currentSnapshot().byAction());
        body.put("byOutcome", event.currentSnapshot().byOutcome());
        body.put("byResourceType", event.currentSnapshot().byResourceType());
        body.put("byErrorType", event.currentSnapshot().byErrorType());
        return body;
    }
}
