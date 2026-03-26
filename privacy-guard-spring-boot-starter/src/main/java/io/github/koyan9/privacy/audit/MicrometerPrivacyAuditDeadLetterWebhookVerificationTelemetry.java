/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.MeterRegistry;

public class MicrometerPrivacyAuditDeadLetterWebhookVerificationTelemetry implements PrivacyAuditDeadLetterWebhookVerificationTelemetry {

    private final MeterRegistry meterRegistry;

    public MicrometerPrivacyAuditDeadLetterWebhookVerificationTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordFailure(PrivacyAuditDeadLetterWebhookVerificationException.Reason reason) {
        String tag = reason == null ? "unknown" : reason.name().toLowerCase();
        meterRegistry.counter(
                "privacy.audit.deadletters.receiver.verification.failures",
                "reason",
                tag
        ).increment();
    }

    @Override
    public void recordRouteFailure(String route, PrivacyAuditDeadLetterWebhookVerificationException.Reason reason) {
        String normalizedRoute = route == null || route.isBlank() ? "default" : route.trim();
        String normalizedReason = reason == null ? "unknown" : reason.name().toLowerCase();
        meterRegistry.counter(
                "privacy.audit.deadletters.receiver.route.failures",
                "route",
                normalizedRoute,
                "reason",
                normalizedReason
        ).increment();
    }
}
