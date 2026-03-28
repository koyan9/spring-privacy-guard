/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

@StableSpi
public record PrivacyTenantDeadLetterAlertMonitoringPolicy(
        Boolean enabled
) {

    public boolean hasOverrides() {
        return enabled != null;
    }

    public boolean resolveEnabled(boolean defaultEnabled) {
        return enabled == null ? defaultEnabled : enabled;
    }

    public static PrivacyTenantDeadLetterAlertMonitoringPolicy merge(
            PrivacyTenantDeadLetterAlertMonitoringPolicy primary,
            PrivacyTenantDeadLetterAlertMonitoringPolicy fallback
    ) {
        PrivacyTenantDeadLetterAlertMonitoringPolicy normalizedPrimary = primary == null ? none() : primary;
        PrivacyTenantDeadLetterAlertMonitoringPolicy normalizedFallback = fallback == null ? none() : fallback;
        return new PrivacyTenantDeadLetterAlertMonitoringPolicy(
                normalizedPrimary.enabled() != null ? normalizedPrimary.enabled() : normalizedFallback.enabled()
        );
    }

    public static PrivacyTenantDeadLetterAlertMonitoringPolicy none() {
        return new PrivacyTenantDeadLetterAlertMonitoringPolicy(null);
    }
}
