/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

public final class PrivacyTenantContextHolder {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private PrivacyTenantContextHolder() {
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setTenantId(String tenantId) {
        String normalizedTenantId = normalize(tenantId);
        if (normalizedTenantId == null) {
            clear();
            return;
        }
        CURRENT_TENANT.set(normalizedTenantId);
    }

    public static PrivacyTenantContextScope openScope(String tenantId) {
        String previousTenantId = getTenantId();
        setTenantId(tenantId);
        return new PrivacyTenantContextScope(previousTenantId);
    }

    public static PrivacyTenantContextSnapshot snapshot() {
        return PrivacyTenantContextSnapshot.capture();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    static String normalize(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantId.trim();
    }

    static void restore(String tenantId) {
        String normalizedTenantId = normalize(tenantId);
        if (normalizedTenantId == null) {
            clear();
            return;
        }
        CURRENT_TENANT.set(normalizedTenantId);
    }
}
