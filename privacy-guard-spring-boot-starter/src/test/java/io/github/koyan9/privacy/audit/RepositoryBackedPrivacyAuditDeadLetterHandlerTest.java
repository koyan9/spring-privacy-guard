/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantContextHolder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
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

            @Override
            public boolean supportsTenantWrite() {
                return true;
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
    void persistsTenantScopedDeadLetterInBuiltInRepositoryWhenAttachTenantIdDisabled() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        PrivacyTenantAuditPolicyResolver tenantPolicyResolver =
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), false, "tenant");
        RepositoryBackedPrivacyAuditDeadLetterHandler handler = new RepositoryBackedPrivacyAuditDeadLetterHandler(
                repository,
                () -> "tenant-a",
                tenantPolicyResolver
        );
        PrivacyAuditDeadLetterService deadLetterService = new PrivacyAuditDeadLetterService(repository, event -> {
        });
        PrivacyTenantAuditDeadLetterQueryService queryService = new PrivacyTenantAuditDeadLetterQueryService(
                deadLetterService,
                new PrivacyAuditDeadLetterStatsService(repository),
                () -> "tenant-a",
                tenantPolicyResolver,
                repository
        );

        handler.handle(new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of("phone", "138****8000")), 3, new IllegalStateException("failed"));

        assertEquals(
                java.util.List.of("demo"),
                queryService.findByCriteria("tenant-a", PrivacyAuditDeadLetterQueryCriteria.recent(10))
                        .stream()
                        .map(PrivacyAuditDeadLetterEntry::resourceId)
                        .toList()
        );
        assertEquals("tenant-a", repository.findAll().get(0).details().get("tenant"));
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
    void fallsBackWhenDeadLetterWriteRepositoryDoesNotDeclareNativeCapability() {
        AtomicReference<PrivacyAuditDeadLetterEntry> saved = new AtomicReference<>();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RepositoryBackedPrivacyAuditDeadLetterHandler handler = new RepositoryBackedPrivacyAuditDeadLetterHandler(
                new PrivacyAuditDeadLetterRepository() {
                    @Override
                    public void save(PrivacyAuditDeadLetterEntry entry) {
                        saved.set(entry);
                    }
                },
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

    @Test
    void builtInTenantAwareDeadLetterRepositoryRemainsTenantReadableWhenPolicyDoesNotAttachTenantId() {
        InMemoryPrivacyAuditDeadLetterRepository repository = new InMemoryPrivacyAuditDeadLetterRepository();
        RepositoryBackedPrivacyAuditDeadLetterHandler handler = new RepositoryBackedPrivacyAuditDeadLetterHandler(
                repository,
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), false, "tenant")
        );

        handler.handle(
                new PrivacyAuditEvent(
                        Instant.parse("2026-03-27T00:00:00Z"),
                        "READ",
                        "Patient",
                        "demo",
                        "actor",
                        "OK",
                        Map.of("phone", "138****8000")
                ),
                3,
                new IllegalStateException("failed")
        );

        List<PrivacyAuditDeadLetterEntry> entries = repository.findByCriteria("tenant-a", "tenant", PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertEquals(List.of("demo"), entries.stream().map(PrivacyAuditDeadLetterEntry::resourceId).toList());
        assertEquals("tenant-a", repository.findAll().get(0).details().get("tenant"));
    }
}
