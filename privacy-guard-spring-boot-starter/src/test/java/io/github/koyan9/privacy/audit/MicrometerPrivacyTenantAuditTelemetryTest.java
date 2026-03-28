/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerPrivacyTenantAuditTelemetryTest {

    @Test
    void recordsTenantReadPathCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerPrivacyTenantAuditTelemetry telemetry = new MicrometerPrivacyTenantAuditTelemetry(registry);

        telemetry.recordQueryReadPath("audit", "native");
        telemetry.recordQueryReadPath("audit", "fallback");
        telemetry.recordQueryReadPath("dead_letter", "native");
        telemetry.recordQueryReadPath("dead_letter_find_by_id", "fallback");
        telemetry.recordWritePath("audit_write", "native");
        telemetry.recordWritePath("dead_letter_write", "fallback");
        telemetry.recordWritePath("dead_letter_import", "native");
        telemetry.recordWritePath("dead_letter_delete", "native");
        telemetry.recordWritePath("dead_letter_delete_by_id", "fallback");
        telemetry.recordWritePath("dead_letter_replay", "fallback");
        telemetry.recordWritePath("dead_letter_replay_by_id", "native");
        telemetry.recordAlertTransition("tenant-a", "WARNING", false);
        telemetry.recordAlertTransition("tenant-a", "CLEAR", true);
        telemetry.recordAlertDelivery("tenant-a", "webhook", "success");
        telemetry.recordAlertDelivery("tenant-a", "email", "failure");
        telemetry.recordReceiverRouteFailure("/receiver/tenant-a-alerts", "invalid_signature");

        assertThat(registry.get("privacy.audit.tenant.read.path")
                .tag("domain", "audit")
                .tag("path", "native")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.tenant.read.path")
                .tag("domain", "audit")
                .tag("path", "fallback")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.tenant.read.path")
                .tag("domain", "dead_letter")
                .tag("path", "native")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.tenant.read.path")
                .tag("domain", "dead_letter_find_by_id")
                .tag("path", "fallback")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.tenant.write.path")
                .tag("domain", "audit_write")
                .tag("path", "native")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.tenant.write.path")
                .tag("domain", "dead_letter_write")
                .tag("path", "fallback")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.tenant.write.path")
                .tag("domain", "dead_letter_import")
                .tag("path", "native")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.tenant.write.path")
                .tag("domain", "dead_letter_delete")
                .tag("path", "native")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.tenant.write.path")
                .tag("domain", "dead_letter_delete_by_id")
                .tag("path", "fallback")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.tenant.write.path")
                .tag("domain", "dead_letter_replay")
                .tag("path", "fallback")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.tenant.write.path")
                .tag("domain", "dead_letter_replay_by_id")
                .tag("path", "native")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.deadletters.alert.tenant.transitions")
                .tag("tenant", "tenant-a")
                .tag("state", "warning")
                .tag("recovery", "false")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.deadletters.alert.tenant.transitions")
                .tag("tenant", "tenant-a")
                .tag("state", "clear")
                .tag("recovery", "true")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.deadletters.alert.tenant.deliveries")
                .tag("tenant", "tenant-a")
                .tag("channel", "webhook")
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.deadletters.alert.tenant.deliveries")
                .tag("tenant", "tenant-a")
                .tag("channel", "email")
                .tag("outcome", "failure")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.deadletters.receiver.route.failures")
                .tag("route", "/receiver/tenant-a-alerts")
                .tag("reason", "invalid_signature")
                .counter()
                .count()).isEqualTo(1.0d);
    }
}
