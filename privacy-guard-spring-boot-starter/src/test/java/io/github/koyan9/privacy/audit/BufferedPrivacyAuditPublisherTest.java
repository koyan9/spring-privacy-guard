/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantContextHolder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferedPrivacyAuditPublisherTest {

    @Test
    void flushesWhenBatchSizeIsReached() throws Exception {
        RecordingRepository repository = new RecordingRepository(1);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        BufferedPrivacyAuditPublisher publisher = new BufferedPrivacyAuditPublisher(
                repository,
                executor,
                2,
                Duration.ofSeconds(30),
                2,
                Duration.ZERO,
                (event, retryAttempts, exception) -> {
                }
        );

        try {
            publisher.publish(event("first"));
            publisher.publish(event("second"));

            assertTrue(repository.await());
            assertEquals(List.of(List.of("first", "second")), repository.resourceIdBatches());
        } finally {
            publisher.destroy();
            executor.shutdownNow();
        }
    }

    @Test
    void flushesPendingEventsOnSchedule() throws Exception {
        RecordingRepository repository = new RecordingRepository(1);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        BufferedPrivacyAuditPublisher publisher = new BufferedPrivacyAuditPublisher(
                repository,
                executor,
                10,
                Duration.ofMillis(50),
                2,
                Duration.ZERO,
                (event, retryAttempts, exception) -> {
                }
        );

        try {
            publisher.publish(event("scheduled"));

            assertTrue(repository.await());
            assertEquals(List.of(List.of("scheduled")), repository.resourceIdBatches());
        } finally {
            publisher.destroy();
            executor.shutdownNow();
        }
    }

    @Test
    void retriesBatchBeforeSucceeding() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        RecordingRepository repository = new RecordingRepository(1) {
            @Override
            public synchronized void saveAll(List<PrivacyAuditEvent> events) {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("first batch failure");
                }
                super.saveAll(events);
            }
        };
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        BufferedPrivacyAuditPublisher publisher = new BufferedPrivacyAuditPublisher(
                repository,
                executor,
                2,
                Duration.ofSeconds(30),
                2,
                Duration.ZERO,
                (event, retryAttempts, exception) -> {
                }
        );

        try {
            publisher.publish(event("first"));
            publisher.publish(event("second"));

            assertTrue(repository.await());
            assertEquals(2, attempts.get());
            assertEquals(List.of(List.of("first", "second")), repository.resourceIdBatches());
        } finally {
            publisher.destroy();
            executor.shutdownNow();
        }
    }

    @Test
    void sendsBatchEventsToDeadLetterHandlerWhenRetriesAreExhausted() throws Exception {
        RecordingRepository repository = new RecordingRepository(0) {
            @Override
            public synchronized void saveAll(List<PrivacyAuditEvent> events) {
                throw new IllegalStateException("batch failure");
            }
        };
        List<String> deadLetterResourceIds = new ArrayList<>();
        CountDownLatch deadLetterLatch = new CountDownLatch(2);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        BufferedPrivacyAuditPublisher publisher = new BufferedPrivacyAuditPublisher(
                repository,
                executor,
                2,
                Duration.ofSeconds(30),
                2,
                Duration.ZERO,
                (event, attempts, exception) -> {
                    deadLetterResourceIds.add(event.resourceId());
                    deadLetterLatch.countDown();
                }
        );

        try {
            publisher.publish(event("first"));
            publisher.publish(event("second"));
            publisher.destroy();

            assertTrue(deadLetterLatch.await(5, TimeUnit.SECONDS));
            assertEquals(2, deadLetterResourceIds.size());
            assertTrue(deadLetterResourceIds.containsAll(List.of("first", "second")));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void flushesRemainingEventsOnDestroy() throws Exception {
        RecordingRepository repository = new RecordingRepository(1);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        BufferedPrivacyAuditPublisher publisher = new BufferedPrivacyAuditPublisher(
                repository,
                executor,
                10,
                Duration.ofSeconds(30),
                2,
                Duration.ZERO,
                (event, retryAttempts, exception) -> {
                }
        );

        try {
            publisher.publish(event("destroyed"));
            publisher.destroy();

            assertTrue(repository.await());
            assertEquals(List.of(List.of("destroyed")), repository.resourceIdBatches());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void capturesTenantMetadataPerBufferedEventWhenTenantAwareWriteRepositoryIsAvailable() throws Exception {
        TenantAwareRecordingRepository repository = new TenantAwareRecordingRepository(1);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BufferedPrivacyAuditPublisher publisher = new BufferedPrivacyAuditPublisher(
                repository,
                executor,
                2,
                Duration.ofSeconds(30),
                2,
                Duration.ZERO,
                (event, retryAttempts, exception) -> {
                },
                PrivacyTenantContextHolder::getTenantId,
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                new MicrometerPrivacyTenantAuditTelemetry(meterRegistry)
        );

        try {
            PrivacyTenantContextHolder.setTenantId("tenant-a");
            publisher.publish(event("first"));
            PrivacyTenantContextHolder.setTenantId("tenant-b");
            publisher.publish(event("second"));

            assertTrue(repository.await());
            assertThat(repository.tenantIds()).containsExactly("tenant-a", "tenant-b");
            assertEquals(1.0d, meterRegistry.get("privacy.audit.tenant.write.path").tag("domain", "audit_batch_write").tag("path", "native").counter().count());
        } finally {
            PrivacyTenantContextHolder.clear();
            publisher.destroy();
            executor.shutdownNow();
        }
    }

    @Test
    void recordsFallbackBatchWritePathWhenTenantAwareRepositoryUnavailable() throws Exception {
        RecordingRepository repository = new RecordingRepository(1);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BufferedPrivacyAuditPublisher publisher = new BufferedPrivacyAuditPublisher(
                repository,
                executor,
                2,
                Duration.ofSeconds(30),
                2,
                Duration.ZERO,
                (event, retryAttempts, exception) -> {
                },
                PrivacyTenantContextHolder::getTenantId,
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                new MicrometerPrivacyTenantAuditTelemetry(meterRegistry)
        );

        try {
            publisher.publish(event("first"));
            publisher.publish(event("second"));

            assertTrue(repository.await());
            assertEquals(1.0d, meterRegistry.get("privacy.audit.tenant.write.path").tag("domain", "audit_batch_write").tag("path", "fallback").counter().count());
        } finally {
            PrivacyTenantContextHolder.clear();
            publisher.destroy();
            executor.shutdownNow();
        }
    }

    @Test
    void builtInTenantAwareBatchRepositoryRemainsTenantReadableWhenPolicyDoesNotAttachTenantId() throws Exception {
        TenantAwareInMemoryRepository repository = new TenantAwareInMemoryRepository(1);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        BufferedPrivacyAuditPublisher publisher = new BufferedPrivacyAuditPublisher(
                repository,
                executor,
                2,
                Duration.ofSeconds(30),
                2,
                Duration.ZERO,
                (event, retryAttempts, exception) -> {
                },
                PrivacyTenantContextHolder::getTenantId,
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), false, "tenant")
        );

        try {
            PrivacyTenantContextHolder.setTenantId("tenant-a");
            publisher.publish(event("first"));
            PrivacyTenantContextHolder.setTenantId("tenant-b");
            publisher.publish(event("second"));

            assertTrue(repository.await());
            assertThat(repository.findByCriteria("tenant-a", "tenant", PrivacyAuditQueryCriteria.recent(10)))
                    .extracting(PrivacyAuditEvent::resourceId)
                    .containsExactly("first");
            assertThat(repository.findByCriteria("tenant-b", "tenant", PrivacyAuditQueryCriteria.recent(10)))
                    .extracting(PrivacyAuditEvent::resourceId)
                    .containsExactly("second");
            assertThat(repository.findAll())
                    .extracting(event -> event.details().get("tenant"))
                    .containsExactlyInAnyOrder("tenant-a", "tenant-b");
        } finally {
            PrivacyTenantContextHolder.clear();
            publisher.destroy();
            executor.shutdownNow();
        }
    }

    private PrivacyAuditEvent event(String resourceId) {
        return new PrivacyAuditEvent(Instant.now(), "READ", "Patient", resourceId, "actor", "OK", Map.of());
    }

    static class RecordingRepository implements PrivacyAuditRepository {

        private final CountDownLatch latch;
        private final List<List<String>> resourceIdBatches = new ArrayList<>();

        RecordingRepository(int expectedFlushes) {
            this.latch = new CountDownLatch(expectedFlushes);
        }

        @Override
        public void save(PrivacyAuditEvent event) {
            throw new UnsupportedOperationException("save should not be used in this test");
        }

        @Override
        public synchronized void saveAll(List<PrivacyAuditEvent> events) {
            resourceIdBatches.add(events.stream().map(PrivacyAuditEvent::resourceId).toList());
            latch.countDown();
        }

        synchronized List<List<String>> resourceIdBatches() {
            return List.copyOf(resourceIdBatches);
        }

        boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }
    }

    static class TenantAwareRecordingRepository implements PrivacyAuditRepository, PrivacyTenantAuditWriteRepository {

        private final CountDownLatch latch;
        private final List<String> tenantIds = new ArrayList<>();

        TenantAwareRecordingRepository(int expectedFlushes) {
            this.latch = new CountDownLatch(expectedFlushes);
        }

        @Override
        public void save(PrivacyAuditEvent event) {
            throw new UnsupportedOperationException("save should not be used in this test");
        }

        @Override
        public void save(PrivacyTenantAuditWriteRequest request) {
            throw new UnsupportedOperationException("save should not be used in this test");
        }

        @Override
        public synchronized void saveAllTenantAware(List<PrivacyTenantAuditWriteRequest> requests) {
            tenantIds.addAll(requests.stream().map(PrivacyTenantAuditWriteRequest::tenantId).toList());
            latch.countDown();
        }

        @Override
        public boolean supportsTenantWrite() {
            return true;
        }

        List<String> tenantIds() {
            return List.copyOf(tenantIds);
        }

        boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }
    }

    static class TenantAwareInMemoryRepository extends InMemoryPrivacyAuditRepository {

        private final CountDownLatch latch;

        TenantAwareInMemoryRepository(int expectedFlushes) {
            this.latch = new CountDownLatch(expectedFlushes);
        }

        @Override
        public synchronized void saveAllTenantAware(List<PrivacyTenantAuditWriteRequest> requests) {
            super.saveAllTenantAware(requests);
            latch.countDown();
        }

        boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void recordsFallbackBatchWritePathWhenRepositoryDoesNotDeclareNativeCapability() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CountDownLatch latch = new CountDownLatch(1);
        PrivacyAuditRepository repository = new PrivacyAuditRepository() {
            @Override
            public void save(PrivacyAuditEvent event) {
            }

            @Override
            public void saveAll(List<PrivacyAuditEvent> events) {
                latch.countDown();
            }
        };
        BufferedPrivacyAuditPublisher publisher = new BufferedPrivacyAuditPublisher(
                repository,
                executor,
                2,
                Duration.ofSeconds(30),
                2,
                Duration.ZERO,
                (event, retryAttempts, exception) -> {
                },
                PrivacyTenantContextHolder::getTenantId,
                tenantId -> new PrivacyTenantAuditPolicy(java.util.Set.of(), java.util.Set.of(), true, "tenant"),
                new MicrometerPrivacyTenantAuditTelemetry(meterRegistry)
        );

        try {
            publisher.publish(event("first"));
            publisher.publish(event("second"));

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1.0d, meterRegistry.get("privacy.audit.tenant.write.path").tag("domain", "audit_batch_write").tag("path", "fallback").counter().count());
        } finally {
            PrivacyTenantContextHolder.clear();
            publisher.destroy();
            executor.shutdownNow();
        }
    }

}
