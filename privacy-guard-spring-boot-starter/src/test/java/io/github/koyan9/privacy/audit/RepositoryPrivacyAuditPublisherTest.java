/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepositoryPrivacyAuditPublisherTest {

    @Test
    void routesThroughTenantAwareWriteRepositoryWhenAvailable() {
        AtomicReference<PrivacyTenantAuditWriteRequest> saved = new AtomicReference<>();
        class TenantAwareRepository implements PrivacyAuditRepository, PrivacyTenantAuditWriteRepository {
            @Override
            public void save(PrivacyAuditEvent event) {
            }

            @Override
            public void save(PrivacyTenantAuditWriteRequest request) {
                saved.set(request);
            }
        }
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RepositoryPrivacyAuditPublisher publisher = new RepositoryPrivacyAuditPublisher(
                new TenantAwareRepository(),
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                new MicrometerPrivacyTenantAuditTelemetry(meterRegistry)
        );
        PrivacyAuditEvent event = new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of());

        publisher.publish(event);

        assertEquals("tenant-a", saved.get().tenantId());
        assertEquals("tenant", saved.get().tenantDetailKey());
        assertEquals("demo", saved.get().event().resourceId());
        assertEquals(1.0d, meterRegistry.get("privacy.audit.tenant.write.path").tag("domain", "audit_write").tag("path", "native").counter().count());
    }

    @Test
    void recordsFallbackWritePathWhenTenantAwareRepositoryUnavailable() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RepositoryPrivacyAuditPublisher publisher = new RepositoryPrivacyAuditPublisher(
                event -> {
                },
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                new MicrometerPrivacyTenantAuditTelemetry(meterRegistry)
        );

        publisher.publish(new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of()));

        assertEquals(1.0d, meterRegistry.get("privacy.audit.tenant.write.path").tag("domain", "audit_write").tag("path", "fallback").counter().count());
    }
}
