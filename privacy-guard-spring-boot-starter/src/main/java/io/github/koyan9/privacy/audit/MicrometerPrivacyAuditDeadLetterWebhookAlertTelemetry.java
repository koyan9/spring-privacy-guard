/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class MicrometerPrivacyAuditDeadLetterWebhookAlertTelemetry implements PrivacyAuditDeadLetterWebhookAlertTelemetry {

    private final Counter attemptCounter;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter retryCounter;
    private final MeterRegistry meterRegistry;
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();
    private final AtomicLong lastFailureEpochSeconds = new AtomicLong();
    private final AtomicInteger lastFailureStatus = new AtomicInteger();
    private final AtomicInteger lastFailureRetryable = new AtomicInteger();
    private final AtomicReference<String> lastFailureType = new AtomicReference<>("unknown");

    public MicrometerPrivacyAuditDeadLetterWebhookAlertTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
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
        Gauge.builder("privacy.audit.deadletters.alert.webhook.last_failure_status", lastFailureStatus, AtomicInteger::doubleValue)
                .description("Last failed built-in webhook alert HTTP status code or 0 when unavailable")
                .register(meterRegistry);
        Gauge.builder("privacy.audit.deadletters.alert.webhook.last_failure_retryable", lastFailureRetryable, AtomicInteger::doubleValue)
                .description("Whether the last webhook alert failure was retryable (1 for retryable, 0 otherwise)")
                .register(meterRegistry);
        for (String type : Set.of("http_status", "io_error", "runtime_error", "unknown")) {
            Gauge.builder("privacy.audit.deadletters.alert.webhook.last_failure_type", () -> type.equals(lastFailureType.get()) ? 1.0d : 0.0d)
                    .description("Tracks the last webhook alert failure type as a 1/0 gauge")
                    .tag("type", type)
                    .register(meterRegistry);
        }
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

    @Override
    public void recordFailureDetail(WebhookAlertFailureDetail detail) {
        if (detail == null) {
            return;
        }
        String type = normalizeType(detail.failureType());
        String category = normalizeCategory(detail, type);
        lastFailureType.set(type);
        lastFailureStatus.set(Math.max(0, detail.statusCode()));
        lastFailureRetryable.set(detail.retryable() ? 1 : 0);
        meterRegistry.counter(
                "privacy.audit.deadletters.alert.webhook.failures",
                "type",
                type,
                "retryable",
                String.valueOf(detail.retryable()),
                "category",
                category
        ).increment();
    }

    private String normalizeType(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "http_status", "io_error", "runtime_error" -> normalized;
            default -> "unknown";
        };
    }

    private String normalizeCategory(WebhookAlertFailureDetail detail, String type) {
        if ("http_status".equals(type)) {
            int status = detail.statusCode();
            if (status == 429) {
                return "http_429";
            }
            if (status >= 500) {
                return "http_5xx";
            }
            if (status >= 400) {
                return "http_4xx";
            }
            return "http_unknown";
        }
        return switch (type) {
            case "io_error" -> "io_error";
            case "runtime_error" -> "runtime_error";
            default -> "unknown";
        };
    }
}
