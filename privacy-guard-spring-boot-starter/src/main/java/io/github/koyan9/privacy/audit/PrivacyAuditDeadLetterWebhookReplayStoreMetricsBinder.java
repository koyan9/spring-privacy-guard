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
    }

    private double earliestExpiry() {
        return snapshot().earliestExpiry() == null ? 0.0d : snapshot().earliestExpiry().getEpochSecond();
    }

    private double latestExpiry() {
        return snapshot().latestExpiry() == null ? 0.0d : snapshot().latestExpiry().getEpochSecond();
    }

    private PrivacyAuditDeadLetterWebhookReplayStoreSnapshot snapshot() {
        return observationService.currentSnapshot();
    }
}
