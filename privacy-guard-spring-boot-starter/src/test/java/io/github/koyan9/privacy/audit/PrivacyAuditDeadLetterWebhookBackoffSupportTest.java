/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyAuditDeadLetterWebhookBackoffSupportTest {

    @Test
    void usesFixedBackoffByDefault() {
        PrivacyGuardProperties.AlertWebhook properties = new PrivacyGuardProperties.AlertWebhook();
        properties.setBackoff(Duration.ofMillis(200));

        long delay = PrivacyAuditDeadLetterWebhookBackoffSupport.computeDelayMillis(properties, 3, 0.5d);

        assertThat(delay).isEqualTo(200L);
    }

    @Test
    void usesExponentialBackoffAndCapsByMaxBackoff() {
        PrivacyGuardProperties.AlertWebhook properties = new PrivacyGuardProperties.AlertWebhook();
        properties.setBackoff(Duration.ofMillis(100));
        properties.setBackoffPolicy(PrivacyGuardProperties.AlertWebhook.BackoffPolicy.EXPONENTIAL);
        properties.setMaxBackoff(Duration.ofMillis(500));

        long delay = PrivacyAuditDeadLetterWebhookBackoffSupport.computeDelayMillis(properties, 5, 0.3d);

        assertThat(delay).isEqualTo(500L);
    }

    @Test
    void appliesJitterWithinBounds() {
        PrivacyGuardProperties.AlertWebhook properties = new PrivacyGuardProperties.AlertWebhook();
        properties.setBackoff(Duration.ofSeconds(1));
        properties.setJitter(0.5d);

        long low = PrivacyAuditDeadLetterWebhookBackoffSupport.computeDelayMillis(properties, 1, 0.0d);
        long high = PrivacyAuditDeadLetterWebhookBackoffSupport.computeDelayMillis(properties, 1, 1.0d);

        assertThat(low).isEqualTo(500L);
        assertThat(high).isEqualTo(1500L);
    }
}
