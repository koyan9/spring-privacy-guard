/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

@StableSpi
public record PrivacyTenantDeadLetterAlertRoutePolicy(
        PrivacyTenantDeadLetterAlertWebhookPolicy webhook,
        PrivacyTenantDeadLetterAlertEmailPolicy email,
        PrivacyTenantDeadLetterAlertReceiverPolicy receiver
) {

    public PrivacyTenantDeadLetterAlertRoutePolicy {
        webhook = webhook == null ? PrivacyTenantDeadLetterAlertWebhookPolicy.none() : webhook;
        email = email == null ? PrivacyTenantDeadLetterAlertEmailPolicy.none() : email;
        receiver = receiver == null ? PrivacyTenantDeadLetterAlertReceiverPolicy.none() : receiver;
    }

    public boolean hasOverrides() {
        return webhook.hasOverrides() || email.hasOverrides() || receiver.hasOverrides();
    }

    public static PrivacyTenantDeadLetterAlertRoutePolicy merge(
            PrivacyTenantDeadLetterAlertRoutePolicy primary,
            PrivacyTenantDeadLetterAlertRoutePolicy fallback
    ) {
        PrivacyTenantDeadLetterAlertRoutePolicy normalizedPrimary = primary == null ? none() : primary;
        PrivacyTenantDeadLetterAlertRoutePolicy normalizedFallback = fallback == null ? none() : fallback;
        return new PrivacyTenantDeadLetterAlertRoutePolicy(
                PrivacyTenantDeadLetterAlertWebhookPolicy.merge(normalizedPrimary.webhook(), normalizedFallback.webhook()),
                PrivacyTenantDeadLetterAlertEmailPolicy.merge(normalizedPrimary.email(), normalizedFallback.email()),
                PrivacyTenantDeadLetterAlertReceiverPolicy.merge(normalizedPrimary.receiver(), normalizedFallback.receiver())
        );
    }

    public static PrivacyTenantDeadLetterAlertRoutePolicy none() {
        return new PrivacyTenantDeadLetterAlertRoutePolicy(
                PrivacyTenantDeadLetterAlertWebhookPolicy.none(),
                PrivacyTenantDeadLetterAlertEmailPolicy.none(),
                PrivacyTenantDeadLetterAlertReceiverPolicy.none()
        );
    }
}
