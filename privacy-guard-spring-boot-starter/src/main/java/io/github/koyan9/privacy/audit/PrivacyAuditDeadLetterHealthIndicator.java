/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

public class PrivacyAuditDeadLetterHealthIndicator implements HealthIndicator {

    private final PrivacyAuditDeadLetterObservationService observationService;

    public PrivacyAuditDeadLetterHealthIndicator(PrivacyAuditDeadLetterObservationService observationService) {
        this.observationService = observationService;
    }

    @Override
    public Health health() {
        PrivacyAuditDeadLetterBacklogSnapshot snapshot = observationService.currentSnapshot();
        Health.Builder builder = snapshot.state() == PrivacyAuditDeadLetterBacklogState.DOWN ? Health.down() : Health.up();
        return builder
                .withDetail("state", snapshot.state().name())
                .withDetail("total", snapshot.total())
                .withDetail("warningThreshold", snapshot.warningThreshold())
                .withDetail("downThreshold", snapshot.downThreshold())
                .build();
    }
}
