/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Supplier;

public class MicrometerPrivacyTenantAuditTelemetry implements PrivacyTenantAuditTelemetry {

    private final Supplier<MeterRegistry> meterRegistrySupplier;

    public MicrometerPrivacyTenantAuditTelemetry(MeterRegistry meterRegistry) {
        this(() -> meterRegistry);
    }

    public MicrometerPrivacyTenantAuditTelemetry(Supplier<MeterRegistry> meterRegistrySupplier) {
        this.meterRegistrySupplier = Objects.requireNonNull(meterRegistrySupplier, "meterRegistrySupplier must not be null");
    }

    @Override
    public void recordQueryReadPath(String domain, String pathKind) {
        MeterRegistry meterRegistry = meterRegistry();
        if (meterRegistry == null) {
            return;
        }
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
        MeterRegistry meterRegistry = meterRegistry();
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "privacy.audit.tenant.write.path",
                "domain",
                normalizeDomain(domain),
                "path",
                normalizePath(pathKind)
        ).increment();
    }

    @Override
    public void recordAlertTransition(String tenantId, String state, boolean recovery) {
        MeterRegistry meterRegistry = meterRegistry();
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "privacy.audit.deadletters.alert.tenant.transitions",
                "tenant",
                normalizeTenant(tenantId),
                "state",
                normalizeState(state),
                "recovery",
                String.valueOf(recovery)
        ).increment();
    }

    @Override
    public void recordAlertDelivery(String tenantId, String channel, String outcome) {
        MeterRegistry meterRegistry = meterRegistry();
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "privacy.audit.deadletters.alert.tenant.deliveries",
                "tenant",
                normalizeTenant(tenantId),
                "channel",
                normalizeChannel(channel),
                "outcome",
                normalizeOutcome(outcome)
        ).increment();
    }

    @Override
    public void recordReceiverRouteFailure(String route, String reason) {
        MeterRegistry meterRegistry = meterRegistry();
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "privacy.audit.deadletters.receiver.route.failures",
                "route",
                normalizeRoute(route),
                "reason",
                normalizeReason(reason)
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

    private String normalizeTenant(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }

    private String normalizeState(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }

    private String normalizeChannel(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }

    private String normalizeOutcome(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }

    private String normalizeRoute(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        return value.trim();
    }

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }

    private MeterRegistry meterRegistry() {
        return meterRegistrySupplier.get();
    }
}
