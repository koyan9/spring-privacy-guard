/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class MicrometerPrivacyAuditDeadLetterWebhookAlertTelemetry implements PrivacyAuditDeadLetterWebhookAlertTelemetry {

    private final Counter attemptCounter;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter retryCounter;
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();
    private final AtomicLong lastFailureEpochSeconds = new AtomicLong();

    public MicrometerPrivacyAuditDeadLetterWebhookAlertTelemetry(MeterRegistry meterRegistry) {
        this.attemptCounter = meterRegistry.counter("privacy.audit.deadletters.alert.webhook.attempts");
        this.successCounter = meterRegistry.counter("privacy.audit.deadletters.alert.webhook.deliveries", "outcome", "success");
        this.failureCounter = meterRegistry.counter("privacy.audit.deadletters.alert.webhook.deliveries", "outcome", "failure");
        this.retryCounter = meterRegistry.counter("privacy.audit.deadletters.alert.webhook.retries");
        Gauge.builder("privacy.audit.deadletters.alert.webhook.last_delivery_seconds", lastSuccessEpochSeconds, AtomicLong::doubleValue)
                .description("Last successful built-in webhook alert delivery timestamp as epoch seconds")
                .tag("outcome", "success")
                .register(meterRegistry);
        Gauge.builder("privacy.audit.deadletters.alert.webhook.last_delivery_seconds", lastFailureEpochSeconds, AtomicLong::doubleValue)
                .description("Last failed built-in webhook alert delivery timestamp as epoch seconds")
                .tag("outcome", "failure")
                .register(meterRegistry);
    }

    @Override
    public void recordAttempt() {
        attemptCounter.increment();
    }

    @Override
    public void recordSuccess(int attempts) {
        successCounter.increment();
        lastSuccessEpochSeconds.set(Instant.now().getEpochSecond());
    }

    @Override
    public void recordFailure(int attempts) {
        failureCounter.increment();
        lastFailureEpochSeconds.set(Instant.now().getEpochSecond());
    }

    @Override
    public void recordRetryScheduled(int nextAttempt) {
        retryCounter.increment();
    }
}
