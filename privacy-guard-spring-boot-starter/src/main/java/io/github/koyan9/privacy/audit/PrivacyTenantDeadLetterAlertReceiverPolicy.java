/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.time.Duration;

@StableSpi
public record PrivacyTenantDeadLetterAlertReceiverPolicy(
        String pathPattern,
        String bearerToken,
        String signatureSecret,
        String signatureAlgorithm,
        String signatureHeader,
        String timestampHeader,
        String nonceHeader,
        Duration maxSkew,
        String replayNamespace
) {

    public PrivacyTenantDeadLetterAlertReceiverPolicy {
        pathPattern = normalize(pathPattern);
        bearerToken = normalize(bearerToken);
        signatureSecret = normalize(signatureSecret);
        signatureAlgorithm = normalize(signatureAlgorithm);
        signatureHeader = normalize(signatureHeader);
        timestampHeader = normalize(timestampHeader);
        nonceHeader = normalize(nonceHeader);
        replayNamespace = normalize(replayNamespace);
    }

    public boolean hasOverrides() {
        return pathPattern != null
                || bearerToken != null
                || signatureSecret != null
                || signatureAlgorithm != null
                || signatureHeader != null
                || timestampHeader != null
                || nonceHeader != null
                || maxSkew != null
                || replayNamespace != null;
    }

    public static PrivacyTenantDeadLetterAlertReceiverPolicy merge(
            PrivacyTenantDeadLetterAlertReceiverPolicy primary,
            PrivacyTenantDeadLetterAlertReceiverPolicy fallback
    ) {
        PrivacyTenantDeadLetterAlertReceiverPolicy normalizedPrimary = primary == null ? none() : primary;
        PrivacyTenantDeadLetterAlertReceiverPolicy normalizedFallback = fallback == null ? none() : fallback;
        return new PrivacyTenantDeadLetterAlertReceiverPolicy(
                firstNonNull(normalizedPrimary.pathPattern(), normalizedFallback.pathPattern()),
                firstNonNull(normalizedPrimary.bearerToken(), normalizedFallback.bearerToken()),
                firstNonNull(normalizedPrimary.signatureSecret(), normalizedFallback.signatureSecret()),
                firstNonNull(normalizedPrimary.signatureAlgorithm(), normalizedFallback.signatureAlgorithm()),
                firstNonNull(normalizedPrimary.signatureHeader(), normalizedFallback.signatureHeader()),
                firstNonNull(normalizedPrimary.timestampHeader(), normalizedFallback.timestampHeader()),
                firstNonNull(normalizedPrimary.nonceHeader(), normalizedFallback.nonceHeader()),
                firstNonNull(normalizedPrimary.maxSkew(), normalizedFallback.maxSkew()),
                firstNonNull(normalizedPrimary.replayNamespace(), normalizedFallback.replayNamespace())
        );
    }

    public static PrivacyTenantDeadLetterAlertReceiverPolicy none() {
        return new PrivacyTenantDeadLetterAlertReceiverPolicy(null, null, null, null, null, null, null, null, null);
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
