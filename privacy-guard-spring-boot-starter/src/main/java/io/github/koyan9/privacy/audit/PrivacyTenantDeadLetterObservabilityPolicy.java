/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

@StableSpi
public record PrivacyTenantDeadLetterObservabilityPolicy(
        Long warningThreshold,
        Long downThreshold,
        Boolean notifyOnRecovery
) {

    public PrivacyTenantDeadLetterObservabilityPolicy {
        warningThreshold = normalizeThreshold(warningThreshold);
        downThreshold = normalizeThreshold(downThreshold);
    }

    public boolean hasThresholdOverrides() {
        return warningThreshold != null || downThreshold != null;
    }

    public boolean hasOverrides() {
        return hasThresholdOverrides() || notifyOnRecovery != null;
    }

    public long resolveWarningThreshold(long defaultWarningThreshold) {
        return warningThreshold == null
                ? Math.max(0L, defaultWarningThreshold)
                : warningThreshold;
    }

    public long resolveDownThreshold(long defaultWarningThreshold, long defaultDownThreshold) {
        long resolvedWarningThreshold = resolveWarningThreshold(defaultWarningThreshold);
        long candidate = downThreshold == null
                ? Math.max(0L, defaultDownThreshold)
                : downThreshold;
        return Math.max(resolvedWarningThreshold, candidate);
    }

    public boolean resolveNotifyOnRecovery(boolean defaultNotifyOnRecovery) {
        return notifyOnRecovery == null ? defaultNotifyOnRecovery : notifyOnRecovery;
    }

    public static PrivacyTenantDeadLetterObservabilityPolicy none() {
        return new PrivacyTenantDeadLetterObservabilityPolicy(null, null, null);
    }

    private static Long normalizeThreshold(Long value) {
        return value == null ? null : Math.max(0L, value);
    }
}
