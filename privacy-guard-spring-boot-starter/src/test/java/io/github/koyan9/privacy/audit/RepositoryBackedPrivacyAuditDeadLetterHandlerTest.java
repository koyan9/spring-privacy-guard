/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantContextHolder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepositoryBackedPrivacyAuditDeadLetterHandlerTest {

    @Test
    void storesDeadLetterEntryInRepository() {
        AtomicReference<PrivacyAuditDeadLetterEntry> saved = new AtomicReference<>();
        RepositoryBackedPrivacyAuditDeadLetterHandler handler = new RepositoryBackedPrivacyAuditDeadLetterHandler(saved::set);
        PrivacyAuditEvent event = new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of());

        handler.handle(event, 3, new IllegalStateException("failed"));

        assertEquals("demo", saved.get().resourceId());
        assertEquals(3, saved.get().attempts());
        assertEquals("failed", saved.get().errorMessage());
    }

    @Test
    void routesThroughTenantAwareDeadLetterWriteRepositoryWhenAvailable() {
        AtomicReference<PrivacyTenantAuditDeadLetterWriteRequest> saved = new AtomicReference<>();
        class TenantAwareDeadLetterRepository implements PrivacyAuditDeadLetterRepository, PrivacyTenantAuditDeadLetterWriteRepository {
            @Override
            public void save(PrivacyAuditDeadLetterEntry entry) {
            }

            @Override
            public void save(PrivacyTenantAuditDeadLetterWriteRequest request) {
                saved.set(request);
            }
        }
        PrivacyTenantContextHolder.setTenantId("tenant-a");
        try {
            SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
            RepositoryBackedPrivacyAuditDeadLetterHandler handler = new RepositoryBackedPrivacyAuditDeadLetterHandler(
                    new TenantAwareDeadLetterRepository(),
                    PrivacyTenantContextHolder::getTenantId,
                    tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                    new MicrometerPrivacyTenantAuditTelemetry(meterRegistry)
            );
            PrivacyAuditEvent event = new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of());

            handler.handle(event, 3, new IllegalStateException("failed"));

            assertEquals("tenant-a", saved.get().tenantId());
            assertEquals("tenant", saved.get().tenantDetailKey());
            assertEquals("demo", saved.get().entry().resourceId());
            assertEquals(1.0d, meterRegistry.get("privacy.audit.tenant.write.path").tag("domain", "dead_letter_write").tag("path", "native").counter().count());
        } finally {
            PrivacyTenantContextHolder.clear();
        }
    }

    @Test
    void recordsFallbackDeadLetterWritePathWhenTenantAwareRepositoryUnavailable() {
        AtomicReference<PrivacyAuditDeadLetterEntry> saved = new AtomicReference<>();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RepositoryBackedPrivacyAuditDeadLetterHandler handler = new RepositoryBackedPrivacyAuditDeadLetterHandler(
                saved::set,
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                new MicrometerPrivacyTenantAuditTelemetry(meterRegistry)
        );

        handler.handle(new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of()), 3, new IllegalStateException("failed"));

        assertEquals("demo", saved.get().resourceId());
        assertEquals(1.0d, meterRegistry.get("privacy.audit.tenant.write.path").tag("domain", "dead_letter_write").tag("path", "fallback").counter().count());
    }

    @Test
    void resolvesTelemetryLazilyFromSupplier() {
        AtomicReference<PrivacyAuditDeadLetterEntry> saved = new AtomicReference<>();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AtomicReference<PrivacyTenantAuditTelemetry> telemetryRef = new AtomicReference<>(new MicrometerPrivacyTenantAuditTelemetry(meterRegistry));
        RepositoryBackedPrivacyAuditDeadLetterHandler handler = new RepositoryBackedPrivacyAuditDeadLetterHandler(
                saved::set,
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                telemetryRef::get
        );

        handler.handle(new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of()), 3, new IllegalStateException("failed"));

        assertEquals("demo", saved.get().resourceId());
        assertEquals(1.0d, meterRegistry.get("privacy.audit.tenant.write.path").tag("domain", "dead_letter_write").tag("path", "fallback").counter().count());
    }
}
