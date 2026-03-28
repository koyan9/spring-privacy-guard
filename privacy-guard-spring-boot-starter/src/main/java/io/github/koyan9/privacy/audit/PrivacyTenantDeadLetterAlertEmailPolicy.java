/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

@StableSpi
public record PrivacyTenantDeadLetterAlertEmailPolicy(
        String from,
        String to,
        String subjectPrefix
) {

    public PrivacyTenantDeadLetterAlertEmailPolicy {
        from = normalize(from);
        to = normalize(to);
        subjectPrefix = normalize(subjectPrefix);
    }

    public boolean hasOverrides() {
        return from != null || to != null || subjectPrefix != null;
    }

    public static PrivacyTenantDeadLetterAlertEmailPolicy merge(
            PrivacyTenantDeadLetterAlertEmailPolicy primary,
            PrivacyTenantDeadLetterAlertEmailPolicy fallback
    ) {
        PrivacyTenantDeadLetterAlertEmailPolicy normalizedPrimary = primary == null ? none() : primary;
        PrivacyTenantDeadLetterAlertEmailPolicy normalizedFallback = fallback == null ? none() : fallback;
        return new PrivacyTenantDeadLetterAlertEmailPolicy(
                firstNonNull(normalizedPrimary.from(), normalizedFallback.from()),
                firstNonNull(normalizedPrimary.to(), normalizedFallback.to()),
                firstNonNull(normalizedPrimary.subjectPrefix(), normalizedFallback.subjectPrefix())
        );
    }

    public static PrivacyTenantDeadLetterAlertEmailPolicy none() {
        return new PrivacyTenantDeadLetterAlertEmailPolicy(null, null, null);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static <T> T firstNonNull(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }
}
