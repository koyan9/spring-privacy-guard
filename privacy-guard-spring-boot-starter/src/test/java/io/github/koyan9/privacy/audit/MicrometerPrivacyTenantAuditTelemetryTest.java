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
        telemetry.recordWritePath("audit_write", "native");
        telemetry.recordWritePath("dead_letter_write", "fallback");

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
    }
}
