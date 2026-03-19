/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.time.Instant;
import java.util.Map;

@StableSpi
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
