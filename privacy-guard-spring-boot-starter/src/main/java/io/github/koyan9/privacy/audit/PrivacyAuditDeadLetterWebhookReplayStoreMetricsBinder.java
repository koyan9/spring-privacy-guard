/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

public class PrivacyAuditDeadLetterWebhookReplayStoreMetricsBinder implements MeterBinder {

    private static final String COUNT_METRIC = "privacy.audit.deadletters.receiver.replay_store.count";
    private static final String EXPIRING_SOON_METRIC = "privacy.audit.deadletters.receiver.replay_store.expiring_soon";
    private static final String EXPIRY_METRIC = "privacy.audit.deadletters.receiver.replay_store.expiry_seconds";
    private static final String CLEANUP_COUNT_METRIC = "privacy.audit.deadletters.receiver.replay_store.cleanup.last_count";
    private static final String CLEANUP_DURATION_METRIC = "privacy.audit.deadletters.receiver.replay_store.cleanup.last_duration_ms";
    private static final String CLEANUP_TIMESTAMP_METRIC = "privacy.audit.deadletters.receiver.replay_store.cleanup.last_timestamp";

    private final PrivacyAuditDeadLetterWebhookReplayStoreObservationService observationService;

    public PrivacyAuditDeadLetterWebhookReplayStoreMetricsBinder(
            PrivacyAuditDeadLetterWebhookReplayStoreObservationService observationService
    ) {
        this.observationService = observationService;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(COUNT_METRIC, this, binder -> binder.snapshot().count())
                .description("Current number of stored webhook replay-protection nonces")
                .baseUnit("entries")
                .register(registry);

        Gauge.builder(EXPIRING_SOON_METRIC, this, binder -> binder.snapshot().expiringSoon())
                .description("Number of stored replay-protection nonces expiring within the configured window")
                .baseUnit("entries")
                .register(registry);

        Gauge.builder(EXPIRY_METRIC, this, binder -> binder.earliestExpiry())
                .description("Earliest replay-store expiry timestamp as epoch seconds")
                .tag("kind", "earliest")
                .register(registry);

        Gauge.builder(EXPIRY_METRIC, this, binder -> binder.latestExpiry())
                .description("Latest replay-store expiry timestamp as epoch seconds")
                .tag("kind", "latest")
                .register(registry);

        Gauge.builder(CLEANUP_COUNT_METRIC, this, binder -> binder.cleanupSnapshot().lastCleanupCount())
                .description("Number of expired replay-store entries removed in the last cleanup pass")
                .baseUnit("entries")
                .register(registry);

        Gauge.builder(CLEANUP_DURATION_METRIC, this, binder -> binder.cleanupSnapshot().lastCleanupDurationMillis())
                .description("Duration in milliseconds of the last replay-store cleanup pass")
                .baseUnit("ms")
                .register(registry);

        Gauge.builder(CLEANUP_TIMESTAMP_METRIC, this, binder -> binder.lastCleanupTimestamp())
                .description("Last replay-store cleanup timestamp as epoch seconds")
                .register(registry);
    }

    private double earliestExpiry() {
        return snapshot().earliestExpiry() == null ? 0.0d : snapshot().earliestExpiry().getEpochSecond();
    }

    private double latestExpiry() {
        return snapshot().latestExpiry() == null ? 0.0d : snapshot().latestExpiry().getEpochSecond();
    }

    private double lastCleanupTimestamp() {
        PrivacyAuditDeadLetterWebhookReplayStoreCleanupSnapshot snapshot = cleanupSnapshot();
        return snapshot.lastCleanupAt() == null ? 0.0d : snapshot.lastCleanupAt().getEpochSecond();
    }

    private PrivacyAuditDeadLetterWebhookReplayStoreSnapshot snapshot() {
        return observationService.currentSnapshot();
    }

    private PrivacyAuditDeadLetterWebhookReplayStoreCleanupSnapshot cleanupSnapshot() {
        return observationService.currentCleanupSnapshot();
    }
}
