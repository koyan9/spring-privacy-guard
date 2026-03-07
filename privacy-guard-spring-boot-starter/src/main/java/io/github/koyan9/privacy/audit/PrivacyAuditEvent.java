/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.time.Instant;
import java.util.Map;

public record PrivacyAuditEvent(
        Instant occurredAt,
        String action,
        String resourceType,
        String resourceId,
        String actor,
        String outcome,
        Map<String, String> details
) {

    public PrivacyAuditEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}