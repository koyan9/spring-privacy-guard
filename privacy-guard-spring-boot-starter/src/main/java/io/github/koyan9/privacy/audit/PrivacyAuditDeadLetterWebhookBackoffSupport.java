/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;

import java.time.Duration;

final class PrivacyAuditDeadLetterWebhookBackoffSupport {

    private static final long DEFAULT_MAX_BACKOFF_MILLIS = 30_000L;

    private PrivacyAuditDeadLetterWebhookBackoffSupport() {
    }

    static long computeDelayMillis(PrivacyGuardProperties.AlertWebhook properties, int attempt, double jitterSample) {
        if (properties == null) {
            return 0L;
        }
        Duration backoff = properties.getBackoff();
        long baseMillis = Math.max(0L, backoff == null ? 0L : backoff.toMillis());
        if (baseMillis <= 0L) {
            return 0L;
        }
        int safeAttempt = Math.max(1, attempt);
        PrivacyGuardProperties.AlertWebhook.BackoffPolicy policy = properties.getBackoffPolicy();
        long delayMillis = policy == PrivacyGuardProperties.AlertWebhook.BackoffPolicy.EXPONENTIAL
                ? exponentialDelay(baseMillis, safeAttempt - 1)
                : baseMillis;
        long maxBackoffMillis = resolveMaxBackoffMillis(properties, baseMillis, policy);
        if (maxBackoffMillis > 0L && delayMillis > maxBackoffMillis) {
            delayMillis = maxBackoffMillis;
        }
        double jitter = clamp(properties.getJitter(), 0.0d, 1.0d);
        if (jitter > 0.0d && delayMillis > 0L) {
            double sample = clamp(jitterSample, 0.0d, 1.0d);
            double min = 1.0d - jitter;
            double max = 1.0d + jitter;
            double factor = min + (max - min) * sample;
            delayMillis = (long) Math.floor(delayMillis * factor);
            if (maxBackoffMillis > 0L && delayMillis > maxBackoffMillis) {
                delayMillis = maxBackoffMillis;
            }
        }
        return Math.max(0L, delayMillis);
    }

    private static long resolveMaxBackoffMillis(
            PrivacyGuardProperties.AlertWebhook properties,
            long baseMillis,
            PrivacyGuardProperties.AlertWebhook.BackoffPolicy policy
    ) {
        Duration configuredMax = properties.getMaxBackoff();
        boolean hasConfiguredMax = configuredMax != null && !configuredMax.isZero() && !configuredMax.isNegative();
        if (hasConfiguredMax) {
            return configuredMax.toMillis();
        }
        if (policy != PrivacyGuardProperties.AlertWebhook.BackoffPolicy.EXPONENTIAL || baseMillis <= 0L) {
            return 0L;
        }
        long derived = safeMultiply(baseMillis, 10L);
        return Math.min(derived, DEFAULT_MAX_BACKOFF_MILLIS);
    }

    private static long exponentialDelay(long baseMillis, int exponent) {
        if (exponent <= 0) {
            return baseMillis;
        }
        double value = baseMillis * Math.pow(2.0d, exponent);
        if (value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) value;
    }

    private static long safeMultiply(long base, long multiplier) {
        if (multiplier == 0L || base == 0L) {
            return 0L;
        }
        if (base > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return base * multiplier;
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
