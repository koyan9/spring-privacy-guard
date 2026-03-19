/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.MeterRegistry;

public class MicrometerPrivacyTenantAuditTelemetry implements PrivacyTenantAuditTelemetry {

    private final MeterRegistry meterRegistry;

    public MicrometerPrivacyTenantAuditTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordQueryReadPath(String domain, String pathKind) {
        meterRegistry.counter(
                "privacy.audit.tenant.read.path",
                "domain",
                normalizeDomain(domain),
                "path",
                normalizePath(pathKind)
        ).increment();
    }

    @Override
    public void recordWritePath(String domain, String pathKind) {
        meterRegistry.counter(
                "privacy.audit.tenant.write.path",
                "domain",
                normalizeDomain(domain),
                "path",
                normalizePath(pathKind)
        ).increment();
    }

    private String normalizeDomain(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }
}
