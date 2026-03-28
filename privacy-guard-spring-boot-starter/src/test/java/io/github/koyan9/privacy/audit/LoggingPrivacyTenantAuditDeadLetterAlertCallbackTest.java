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

class LoggingPrivacyTenantAuditDeadLetterAlertCallbackTest {

    @Test
    void respectsTenantDeliveryPolicyWhenLoggingDisabled() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LoggingPrivacyTenantAuditDeadLetterAlertCallback callback =
                new LoggingPrivacyTenantAuditDeadLetterAlertCallback(
                        new MicrometerPrivacyTenantAuditTelemetry(registry),
                        tenantId -> new PrivacyTenantDeadLetterAlertDeliveryPolicy(Boolean.FALSE, null, null),
                        true
                );

        assertThat(callback.supportsTenant("tenant-a")).isFalse();

        callback.handle("tenant-a", event());

        assertThat(registry.find("privacy.audit.deadletters.alert.tenant.deliveries")
                .tag("tenant", "tenant-a")
                .tag("channel", "logging")
                .counters()).isEmpty();
    }

    @Test
    void enablesTenantLoggingWhenGlobalDefaultDisabledButTenantPolicyOverrides() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LoggingPrivacyTenantAuditDeadLetterAlertCallback callback =
                new LoggingPrivacyTenantAuditDeadLetterAlertCallback(
                        new MicrometerPrivacyTenantAuditTelemetry(registry),
                        tenantId -> new PrivacyTenantDeadLetterAlertDeliveryPolicy(Boolean.TRUE, null, null),
                        false
                );

        assertThat(callback.supportsTenant("tenant-a")).isTrue();

        callback.handle("tenant-a", event());

        assertThat(registry.get("privacy.audit.deadletters.alert.tenant.deliveries")
                .tag("tenant", "tenant-a")
                .tag("channel", "logging")
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    private PrivacyAuditDeadLetterAlertEvent event() {
        return new PrivacyAuditDeadLetterAlertEvent(
                Instant.now(),
                new PrivacyAuditDeadLetterBacklogSnapshot(
                        2,
                        1,
                        5,
                        PrivacyAuditDeadLetterBacklogState.WARNING,
                        Map.of("READ", 2L),
                        Map.of(),
                        Map.of(),
                        Map.of()
                ),
                null
        );
    }
}
