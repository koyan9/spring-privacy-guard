/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyAuditDeadLetterMetricsBinderTest {

    @Test
    void exposesTaggedStateAndThresholdGauges() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        repository.save(entry("dead-letter-a"));
        repository.save(entry("dead-letter-b"));

        PrivacyAuditDeadLetterObservationService observationService = new PrivacyAuditDeadLetterObservationService(
                new PrivacyAuditDeadLetterStatsService(repository),
                1,
                3
        );
        PrivacyAuditDeadLetterMetricsBinder binder = new PrivacyAuditDeadLetterMetricsBinder(observationService);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binder.bindTo(registry);

        assertThat(registry.get("privacy.audit.deadletters.total").gauge().value()).isEqualTo(2.0d);
        assertThat(registry.get("privacy.audit.deadletters.state").tag("state", "warning").gauge().value()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.deadletters.state").tag("state", "clear").gauge().value()).isEqualTo(0.0d);
        assertThat(registry.get("privacy.audit.deadletters.threshold").tag("level", "warning").gauge().value()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.deadletters.threshold").tag("level", "down").gauge().value()).isEqualTo(3.0d);
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
