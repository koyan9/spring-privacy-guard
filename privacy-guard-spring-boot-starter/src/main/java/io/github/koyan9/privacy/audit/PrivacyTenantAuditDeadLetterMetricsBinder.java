/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PrivacyTenantAuditDeadLetterMetricsBinder implements MeterBinder {

    private static final String TOTAL_METRIC = "privacy.audit.deadletters.tenant.total";
    private static final String STATE_METRIC = "privacy.audit.deadletters.tenant.state";

    private final PrivacyTenantAuditDeadLetterObservationService observationService;
    private final List<String> tenantIds;

    public PrivacyTenantAuditDeadLetterMetricsBinder(
            PrivacyTenantAuditDeadLetterObservationService observationService,
            List<String> tenantIds
    ) {
        this.observationService = observationService;
        this.tenantIds = normalizeTenantIds(tenantIds);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (String tenantId : tenantIds) {
            Gauge.builder(TOTAL_METRIC, this, binder -> binder.snapshot(tenantId).total())
                    .description("Current total number of privacy audit dead-letter entries for the configured tenant")
                    .baseUnit("entries")
                    .tag("tenant", tenantId)
                    .register(registry);

            for (PrivacyAuditDeadLetterBacklogState state : PrivacyAuditDeadLetterBacklogState.values()) {
                Gauge.builder(STATE_METRIC, this, binder -> binder.stateValue(tenantId, state))
                        .description("Current tenant-scoped privacy audit dead-letter backlog state expressed as 1 for the active state and 0 otherwise")
                        .tag("tenant", tenantId)
                        .tag("state", state.metricTagValue())
                        .register(registry);
            }
        }
    }

    private double stateValue(String tenantId, PrivacyAuditDeadLetterBacklogState state) {
        return snapshot(tenantId).state() == state ? 1.0d : 0.0d;
    }

    private PrivacyAuditDeadLetterBacklogSnapshot snapshot(String tenantId) {
        return observationService.currentSnapshot(tenantId);
    }

    private List<String> normalizeTenantIds(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tenantId : source) {
            if (tenantId == null) {
                continue;
            }
            String trimmed = tenantId.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(normalized);
    }
}
