/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PrivacyTenantAuditDeadLetterHealthIndicator implements HealthIndicator {

    private final PrivacyTenantAuditDeadLetterObservationService observationService;
    private final List<String> tenantIds;

    public PrivacyTenantAuditDeadLetterHealthIndicator(
            PrivacyTenantAuditDeadLetterObservationService observationService,
            List<String> tenantIds
    ) {
        this.observationService = observationService;
        this.tenantIds = tenantIds == null ? List.of() : List.copyOf(tenantIds);
    }

    @Override
    public Health health() {
        Map<String, Object> tenantDetails = new LinkedHashMap<>();
        List<String> warningTenants = new ArrayList<>();
        List<String> downTenants = new ArrayList<>();
        PrivacyAuditDeadLetterBacklogState overallState = PrivacyAuditDeadLetterBacklogState.CLEAR;

        for (String tenantId : tenantIds) {
            PrivacyAuditDeadLetterBacklogSnapshot snapshot = observationService.currentSnapshot(tenantId);
            tenantDetails.put(tenantId, Map.of(
                    "state", snapshot.state().name(),
                    "total", snapshot.total(),
                    "warningThreshold", snapshot.warningThreshold(),
                    "downThreshold", snapshot.downThreshold()
            ));
            if (snapshot.state() == PrivacyAuditDeadLetterBacklogState.DOWN) {
                overallState = PrivacyAuditDeadLetterBacklogState.DOWN;
                downTenants.add(tenantId);
            } else if (snapshot.state() == PrivacyAuditDeadLetterBacklogState.WARNING) {
                if (overallState != PrivacyAuditDeadLetterBacklogState.DOWN) {
                    overallState = PrivacyAuditDeadLetterBacklogState.WARNING;
                }
                warningTenants.add(tenantId);
            }
        }

        Health.Builder builder = overallState == PrivacyAuditDeadLetterBacklogState.DOWN ? Health.down() : Health.up();
        return builder
                .withDetail("state", overallState.name())
                .withDetail("tenantCount", tenantIds.size())
                .withDetail("warningTenants", List.copyOf(warningTenants))
                .withDetail("downTenants", List.copyOf(downTenants))
                .withDetail("tenants", tenantDetails)
                .build();
    }
}
