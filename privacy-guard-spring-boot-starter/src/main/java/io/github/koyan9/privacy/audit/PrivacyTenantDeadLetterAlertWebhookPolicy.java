/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.time.Duration;

@StableSpi
public record PrivacyTenantDeadLetterAlertWebhookPolicy(
        String url,
        String bearerToken,
        String signatureSecret,
        String signatureAlgorithm,
        String signatureHeader,
        String timestampHeader,
        String nonceHeader,
        Integer maxAttempts,
        Duration backoff,
        BackoffPolicy backoffPolicy,
        Duration maxBackoff,
        Double jitter,
        Duration connectTimeout,
        Duration readTimeout
) {

    public PrivacyTenantDeadLetterAlertWebhookPolicy {
        url = normalize(url);
        bearerToken = normalize(bearerToken);
        signatureSecret = normalize(signatureSecret);
        signatureAlgorithm = normalize(signatureAlgorithm);
        signatureHeader = normalize(signatureHeader);
        timestampHeader = normalize(timestampHeader);
        nonceHeader = normalize(nonceHeader);
    }

    public boolean hasOverrides() {
        return url != null
                || bearerToken != null
                || signatureSecret != null
                || signatureAlgorithm != null
                || signatureHeader != null
                || timestampHeader != null
                || nonceHeader != null
                || maxAttempts != null
                || backoff != null
                || backoffPolicy != null
                || maxBackoff != null
                || jitter != null
                || connectTimeout != null
                || readTimeout != null;
    }

    public static PrivacyTenantDeadLetterAlertWebhookPolicy merge(
            PrivacyTenantDeadLetterAlertWebhookPolicy primary,
            PrivacyTenantDeadLetterAlertWebhookPolicy fallback
    ) {
        PrivacyTenantDeadLetterAlertWebhookPolicy normalizedPrimary = primary == null ? none() : primary;
        PrivacyTenantDeadLetterAlertWebhookPolicy normalizedFallback = fallback == null ? none() : fallback;
        return new PrivacyTenantDeadLetterAlertWebhookPolicy(
                firstNonNull(normalizedPrimary.url(), normalizedFallback.url()),
                firstNonNull(normalizedPrimary.bearerToken(), normalizedFallback.bearerToken()),
                firstNonNull(normalizedPrimary.signatureSecret(), normalizedFallback.signatureSecret()),
                firstNonNull(normalizedPrimary.signatureAlgorithm(), normalizedFallback.signatureAlgorithm()),
                firstNonNull(normalizedPrimary.signatureHeader(), normalizedFallback.signatureHeader()),
                firstNonNull(normalizedPrimary.timestampHeader(), normalizedFallback.timestampHeader()),
                firstNonNull(normalizedPrimary.nonceHeader(), normalizedFallback.nonceHeader()),
                firstNonNull(normalizedPrimary.maxAttempts(), normalizedFallback.maxAttempts()),
                firstNonNull(normalizedPrimary.backoff(), normalizedFallback.backoff()),
                firstNonNull(normalizedPrimary.backoffPolicy(), normalizedFallback.backoffPolicy()),
                firstNonNull(normalizedPrimary.maxBackoff(), normalizedFallback.maxBackoff()),
                firstNonNull(normalizedPrimary.jitter(), normalizedFallback.jitter()),
                firstNonNull(normalizedPrimary.connectTimeout(), normalizedFallback.connectTimeout()),
                firstNonNull(normalizedPrimary.readTimeout(), normalizedFallback.readTimeout())
        );
    }

    public static PrivacyTenantDeadLetterAlertWebhookPolicy none() {
        return new PrivacyTenantDeadLetterAlertWebhookPolicy(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
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

    public enum BackoffPolicy {
        FIXED,
        EXPONENTIAL
    }
}
