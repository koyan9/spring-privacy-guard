/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

public class PrivacyAuditDeadLetterMetricsBinder implements MeterBinder {

    private static final String TOTAL_METRIC = "privacy.audit.deadletters.total";
    private static final String STATE_METRIC = "privacy.audit.deadletters.state";
    private static final String THRESHOLD_METRIC = "privacy.audit.deadletters.threshold";

    private final PrivacyAuditDeadLetterObservationService observationService;

    public PrivacyAuditDeadLetterMetricsBinder(PrivacyAuditDeadLetterObservationService observationService) {
        this.observationService = observationService;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(TOTAL_METRIC, this, binder -> binder.snapshot().total())
                .description("Current total number of privacy audit dead-letter entries")
                .baseUnit("entries")
                .register(registry);

        for (PrivacyAuditDeadLetterBacklogState state : PrivacyAuditDeadLetterBacklogState.values()) {
            Gauge.builder(STATE_METRIC, this, binder -> binder.stateValue(state))
                    .description("Current privacy audit dead-letter backlog state expressed as 1 for the active state and 0 otherwise")
                    .tag("state", state.metricTagValue())
                    .register(registry);
        }

        Gauge.builder(THRESHOLD_METRIC, this, binder -> binder.snapshot().warningThreshold())
                .description("Configured warning and down thresholds for privacy audit dead-letter backlog")
                .baseUnit("entries")
                .tag("level", "warning")
                .register(registry);

        Gauge.builder(THRESHOLD_METRIC, this, binder -> binder.snapshot().downThreshold())
                .description("Configured warning and down thresholds for privacy audit dead-letter backlog")
                .baseUnit("entries")
                .tag("level", "down")
                .register(registry);
    }

    private double stateValue(PrivacyAuditDeadLetterBacklogState state) {
        return snapshot().state() == state ? 1.0d : 0.0d;
    }

    private PrivacyAuditDeadLetterBacklogSnapshot snapshot() {
        return observationService.currentSnapshot();
    }
}
