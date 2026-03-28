/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

@StableSpi
public record PrivacyTenantDeadLetterAlertDeliveryPolicy(
        Boolean loggingEnabled,
        Boolean webhookEnabled,
        Boolean emailEnabled
) {

    public boolean hasOverrides() {
        return loggingEnabled != null || webhookEnabled != null || emailEnabled != null;
    }

    public boolean resolveLoggingEnabled(boolean defaultEnabled) {
        return loggingEnabled == null ? defaultEnabled : loggingEnabled;
    }

    public boolean resolveWebhookEnabled(boolean defaultEnabled) {
        return webhookEnabled == null ? defaultEnabled : webhookEnabled;
    }

    public boolean resolveEmailEnabled(boolean defaultEnabled) {
        return emailEnabled == null ? defaultEnabled : emailEnabled;
    }

    public static PrivacyTenantDeadLetterAlertDeliveryPolicy merge(
            PrivacyTenantDeadLetterAlertDeliveryPolicy primary,
            PrivacyTenantDeadLetterAlertDeliveryPolicy fallback
    ) {
        PrivacyTenantDeadLetterAlertDeliveryPolicy normalizedPrimary = primary == null ? none() : primary;
        PrivacyTenantDeadLetterAlertDeliveryPolicy normalizedFallback = fallback == null ? none() : fallback;
        return new PrivacyTenantDeadLetterAlertDeliveryPolicy(
                firstNonNull(normalizedPrimary.loggingEnabled(), normalizedFallback.loggingEnabled()),
                firstNonNull(normalizedPrimary.webhookEnabled(), normalizedFallback.webhookEnabled()),
                firstNonNull(normalizedPrimary.emailEnabled(), normalizedFallback.emailEnabled())
        );
    }

    public static PrivacyTenantDeadLetterAlertDeliveryPolicy none() {
        return new PrivacyTenantDeadLetterAlertDeliveryPolicy(null, null, null);
    }

    private static <T> T firstNonNull(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }
}
