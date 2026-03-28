/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
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

            @Override
            public boolean supportsTenantWrite() {
                return true;
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
    void persistsTenantScopedEventInBuiltInRepositoryWhenAttachTenantIdDisabled() {
        InMemoryPrivacyAuditRepository repository = new InMemoryPrivacyAuditRepository();
        PrivacyTenantAuditPolicyResolver tenantPolicyResolver =
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), false, "tenant");
        RepositoryPrivacyAuditPublisher publisher = new RepositoryPrivacyAuditPublisher(
                repository,
                () -> "tenant-a",
                tenantPolicyResolver
        );
        PrivacyTenantAuditQueryService queryService = new PrivacyTenantAuditQueryService(
                new PrivacyAuditQueryService(repository),
                new PrivacyAuditStatsService(repository),
                () -> "tenant-a",
                tenantPolicyResolver,
                repository
        );

        publisher.publish(new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of("phone", "138****8000")));

        assertEquals(
                java.util.List.of("demo"),
                queryService.findByCriteria("tenant-a", PrivacyAuditQueryCriteria.recent(10))
                        .stream()
                        .map(PrivacyAuditEvent::resourceId)
                        .toList()
        );
        assertEquals("tenant-a", repository.findAll().get(0).details().get("tenant"));
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

    @Test
    void fallsBackWhenWriteRepositoryDoesNotDeclareNativeCapability() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AtomicReference<PrivacyTenantAuditWriteRequest> saved = new AtomicReference<>();
        RepositoryPrivacyAuditPublisher publisher = new RepositoryPrivacyAuditPublisher(
                new PrivacyAuditRepository() {
                    @Override
                    public void save(PrivacyAuditEvent event) {
                    }
                },
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                new MicrometerPrivacyTenantAuditTelemetry(meterRegistry)
        );
        publisher.publish(new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of()));
        assertEquals(1.0d, meterRegistry.get("privacy.audit.tenant.write.path").tag("domain", "audit_write").tag("path", "fallback").counter().count());
        assertEquals(null, saved.get());
    }

    @Test
    void resolvesTelemetryLazilyFromSupplier() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AtomicReference<PrivacyTenantAuditTelemetry> telemetryRef = new AtomicReference<>(new MicrometerPrivacyTenantAuditTelemetry(meterRegistry));
        RepositoryPrivacyAuditPublisher publisher = new RepositoryPrivacyAuditPublisher(
                event -> {
                },
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                telemetryRef::get
        );

        publisher.publish(new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of()));

        assertEquals(1.0d, meterRegistry.get("privacy.audit.tenant.write.path").tag("domain", "audit_write").tag("path", "fallback").counter().count());
    }

    @Test
    void builtInTenantAwareRepositoryRemainsTenantReadableWhenPolicyDoesNotAttachTenantId() {
        InMemoryPrivacyAuditRepository repository = new InMemoryPrivacyAuditRepository();
        RepositoryPrivacyAuditPublisher publisher = new RepositoryPrivacyAuditPublisher(
                repository,
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), false, "tenant")
        );

        publisher.publish(new PrivacyAuditEvent(
                Instant.parse("2026-03-27T00:00:00Z"),
                "READ",
                "Patient",
                "demo",
                "actor",
                "OK",
                Map.of("phone", "138****8000")
        ));

        List<PrivacyAuditEvent> events = repository.findByCriteria("tenant-a", "tenant", PrivacyAuditQueryCriteria.recent(10));

        assertEquals(List.of("demo"), events.stream().map(PrivacyAuditEvent::resourceId).toList());
        assertEquals("tenant-a", repository.findAll().get(0).details().get("tenant"));
    }
}
