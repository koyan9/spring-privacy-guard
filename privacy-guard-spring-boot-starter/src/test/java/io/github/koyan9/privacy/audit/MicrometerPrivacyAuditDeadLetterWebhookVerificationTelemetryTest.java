/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerPrivacyAuditDeadLetterWebhookVerificationTelemetryTest {

    @Test
    void recordsFailuresByReason() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerPrivacyAuditDeadLetterWebhookVerificationTelemetry telemetry =
                new MicrometerPrivacyAuditDeadLetterWebhookVerificationTelemetry(registry);

        telemetry.recordFailure(PrivacyAuditDeadLetterWebhookVerificationException.Reason.INVALID_SIGNATURE);
        telemetry.recordFailure(PrivacyAuditDeadLetterWebhookVerificationException.Reason.REPLAY_DETECTED);
        telemetry.recordFailure(null);

        assertThat(registry.get("privacy.audit.deadletters.receiver.verification.failures")
                .tag("reason", "invalid_signature")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.deadletters.receiver.verification.failures")
                .tag("reason", "replay_detected")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.deadletters.receiver.verification.failures")
                .tag("reason", "unknown")
                .counter()
                .count()).isEqualTo(1.0d);
    }
}
