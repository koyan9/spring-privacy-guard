/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.util.Objects;

@StableSpi
public record PrivacyTenantAuditWriteRequest(
        PrivacyAuditEvent event,
        String tenantId,
        String tenantDetailKey
) {

    public PrivacyTenantAuditWriteRequest {
        event = Objects.requireNonNull(event, "event must not be null");
        tenantId = normalize(tenantId);
        tenantDetailKey = normalizeDetailKey(tenantDetailKey);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeDetailKey(String value) {
        if (value == null || value.isBlank()) {
            return "tenantId";
        }
        return value.trim();
    }
}
