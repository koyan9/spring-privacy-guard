/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyAuditDeadLetterHealthIndicatorTest {

    @Test
    void reportsUpWhileBacklogIsBelowDownThreshold() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(entry("dead-letter-a"));

        PrivacyAuditDeadLetterObservationService observationService = new PrivacyAuditDeadLetterObservationService(
                new PrivacyAuditDeadLetterStatsService(repository),
                1,
                3
        );
        PrivacyAuditDeadLetterHealthIndicator indicator = new PrivacyAuditDeadLetterHealthIndicator(observationService);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails())
                .containsEntry("state", "WARNING")
                .containsEntry("total", 1L)
                .containsEntry("warningThreshold", 1L)
                .containsEntry("downThreshold", 3L);
    }

    @Test
    void reportsDownWhenBacklogReachesDownThreshold() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(entry("dead-letter-a"));
        repository.save(entry("dead-letter-b"));

        PrivacyAuditDeadLetterObservationService observationService = new PrivacyAuditDeadLetterObservationService(
                new PrivacyAuditDeadLetterStatsService(repository),
                1,
                2
        );
        PrivacyAuditDeadLetterHealthIndicator indicator = new PrivacyAuditDeadLetterHealthIndicator(observationService);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicator.health().getDetails()).containsEntry("state", "DOWN");
    }

    private PrivacyAuditDeadLetterEntry entry(String resourceId) {
        return new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                3,
                "java.lang.IllegalStateException",
                "failure",
                Instant.parse("2026-03-08T00:00:00Z"),
                "READ",
                "Patient",
                resourceId,
                "actor",
                "OK",
                Map.of("phone", "138****8000")
        );
    }
}
