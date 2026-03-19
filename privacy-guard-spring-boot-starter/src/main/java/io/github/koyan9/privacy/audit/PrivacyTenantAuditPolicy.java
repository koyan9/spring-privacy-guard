/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.util.Set;

@StableSpi
public record PrivacyTenantAuditPolicy(
        Set<String> includeDetailKeys,
        Set<String> excludeDetailKeys,
        boolean attachTenantId,
        String tenantDetailKey
) {

    public PrivacyTenantAuditPolicy {
        includeDetailKeys = includeDetailKeys == null ? Set.of() : Set.copyOf(includeDetailKeys);
        excludeDetailKeys = excludeDetailKeys == null ? Set.of() : Set.copyOf(excludeDetailKeys);
        tenantDetailKey = (tenantDetailKey == null || tenantDetailKey.isBlank()) ? "tenantId" : tenantDetailKey.trim();
    }

    public boolean keepsDetailKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (!includeDetailKeys.isEmpty() && !includeDetailKeys.contains(key)) {
            return false;
        }
        return !excludeDetailKeys.contains(key);
    }

    public static PrivacyTenantAuditPolicy none() {
        return new PrivacyTenantAuditPolicy(Set.of(), Set.of(), false, "tenantId");
    }
}
