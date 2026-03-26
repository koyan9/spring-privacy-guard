/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantContextHolder;
import io.github.koyan9.privacy.core.PrivacyTenantContextScope;
import io.github.koyan9.privacy.core.PrivacyTenantProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class BufferedPrivacyAuditPublisher implements PrivacyAuditPublisher, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(BufferedPrivacyAuditPublisher.class);

    private final PrivacyAuditRepository repository;
    private final ScheduledExecutorService executor;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final PrivacyAuditDeadLetterHandler deadLetterHandler;
    private final PrivacyTenantProvider tenantProvider;
    private final PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver;
    private final Supplier<PrivacyTenantAuditTelemetry> telemetrySupplier;
    private final Queue<BufferedEvent> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedCount = new AtomicInteger();
    private final AtomicBoolean flushing = new AtomicBoolean();

    public BufferedPrivacyAuditPublisher(
            PrivacyAuditRepository repository,
            ScheduledExecutorService executor,
            int batchSize,
            Duration flushInterval,
            int maxAttempts,
            Duration retryBackoff,
            PrivacyAuditDeadLetterHandler deadLetterHandler
    ) {
        this(
                repository,
                executor,
                batchSize,
                flushInterval,
                maxAttempts,
                retryBackoff,
                deadLetterHandler,
                PrivacyTenantProvider.noop(),
                PrivacyTenantAuditPolicyResolver.noop(),
                (Supplier<PrivacyTenantAuditTelemetry>) null
        );
    }

    public BufferedPrivacyAuditPublisher(
            PrivacyAuditRepository repository,
            ScheduledExecutorService executor,
            int batchSize,
            Duration flushInterval,
            int maxAttempts,
            Duration retryBackoff,
            PrivacyAuditDeadLetterHandler deadLetterHandler,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver
    ) {
        this(
                repository,
                executor,
                batchSize,
                flushInterval,
                maxAttempts,
                retryBackoff,
                deadLetterHandler,
                tenantProvider,
                tenantAuditPolicyResolver,
                (Supplier<PrivacyTenantAuditTelemetry>) null
        );
    }

    public BufferedPrivacyAuditPublisher(
            PrivacyAuditRepository repository,
            ScheduledExecutorService executor,
            int batchSize,
            Duration flushInterval,
            int maxAttempts,
            Duration retryBackoff,
            PrivacyAuditDeadLetterHandler deadLetterHandler,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditTelemetry telemetry
    ) {
        this(
                repository,
                executor,
                batchSize,
                flushInterval,
                maxAttempts,
                retryBackoff,
                deadLetterHandler,
                tenantProvider,
                tenantAuditPolicyResolver,
                () -> telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry
        );
    }

    public BufferedPrivacyAuditPublisher(
            PrivacyAuditRepository repository,
            ScheduledExecutorService executor,
            int batchSize,
            Duration flushInterval,
            int maxAttempts,
            Duration retryBackoff,
            PrivacyAuditDeadLetterHandler deadLetterHandler,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            Supplier<PrivacyTenantAuditTelemetry> telemetrySupplier
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoff = retryBackoff == null ? Duration.ZERO : retryBackoff;
        this.deadLetterHandler = Objects.requireNonNull(deadLetterHandler, "deadLetterHandler must not be null");
        this.tenantProvider = tenantProvider == null ? PrivacyTenantProvider.noop() : tenantProvider;
        this.tenantAuditPolicyResolver = tenantAuditPolicyResolver == null
                ? PrivacyTenantAuditPolicyResolver.noop()
                : tenantAuditPolicyResolver;
        this.telemetrySupplier = telemetrySupplier == null
                ? PrivacyTenantAuditTelemetry::noop
                : telemetrySupplier;
        long intervalMillis = Math.max(1L, flushInterval == null ? 500L : flushInterval.toMillis());
        this.executor.scheduleWithFixedDelay(this::flushSafely, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void publish(PrivacyAuditEvent event) {
        if (event == null) {
            return;
        }
        String tenantId = currentTenantId();
        queue.offer(new BufferedEvent(event, tenantId, tenantDetailKey(tenantId)));
        int currentSize = queuedCount.incrementAndGet();
        if (currentSize >= batchSize) {
            requestFlush();
        }
    }

    @Override
    public void destroy() {
        flushNow();
    }

    private void flushSafely() {
        try {
            flushNow();
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to flush buffered privacy audit events", exception);
        }
    }

    private void requestFlush() {
        if (flushing.compareAndSet(false, true)) {
            try {
                executor.execute(this::flushLoop);
            } catch (RuntimeException exception) {
                flushing.set(false);
                LOGGER.warn("Buffered privacy audit executor rejected flush task, flushing synchronously: {}", exception.toString());
                flushNow();
            }
        }
    }

    private void flushLoop() {
        try {
            flushBatches();
        } finally {
            flushing.set(false);
            if (!queue.isEmpty()) {
                requestFlush();
            }
        }
    }

    private void flushNow() {
        if (flushing.compareAndSet(false, true)) {
            try {
                flushBatches();
            } finally {
                flushing.set(false);
            }
        }
    }

    private void flushBatches() {
        while (true) {
            List<BufferedEvent> batch = drainBatch();
            if (batch.isEmpty()) {
                return;
            }
            saveBatchWithRetry(batch);
        }
    }

    private List<BufferedEvent> drainBatch() {
        List<BufferedEvent> batch = new ArrayList<>(batchSize);
        while (batch.size() < batchSize) {
            BufferedEvent event = queue.poll();
            if (event == null) {
                break;
            }
            queuedCount.decrementAndGet();
            batch.add(event);
        }
        return batch;
    }

    private void saveBatchWithRetry(List<BufferedEvent> batch) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                saveBatch(batch);
                return;
            } catch (RuntimeException exception) {
                lastException = exception;
                if (attempt < maxAttempts) {
                    sleepBackoff();
                }
            }
        }

        RuntimeException failure = lastException == null ? new IllegalStateException("Unknown buffered audit persistence failure") : lastException;
        for (BufferedEvent event : batch) {
            try (PrivacyTenantContextScope ignored = PrivacyTenantContextHolder.openScope(event.tenantId())) {
                deadLetterHandler.handle(event.event(), maxAttempts, failure);
            }
        }
    }

    private void saveBatch(List<BufferedEvent> batch) {
        if (repository instanceof PrivacyTenantAuditWriteRepository tenantAwareRepository) {
            telemetry().recordWritePath("audit_batch_write", "native");
            tenantAwareRepository.saveAllTenantAware(batch.stream()
                    .map(BufferedEvent::toWriteRequest)
                    .toList());
            return;
        }
        telemetry().recordWritePath("audit_batch_write", "fallback");
        repository.saveAll(batch.stream().map(BufferedEvent::event).toList());
    }

    private void sleepBackoff() {
        long backoffMillis = Math.max(0L, retryBackoff.toMillis());
        if (backoffMillis == 0L) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String currentTenantId() {
        String tenantId = tenantProvider.currentTenantId();
        return tenantId == null || tenantId.isBlank() ? null : tenantId.trim();
    }

    private String tenantDetailKey(String tenantId) {
        PrivacyTenantAuditPolicy policy = tenantAuditPolicyResolver.resolve(tenantId);
        return policy == null ? "tenantId" : policy.tenantDetailKey();
    }

    private PrivacyTenantAuditTelemetry telemetry() {
        PrivacyTenantAuditTelemetry telemetry = telemetrySupplier.get();
        return telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry;
    }

    private record BufferedEvent(PrivacyAuditEvent event, String tenantId, String tenantDetailKey) {
        private PrivacyTenantAuditWriteRequest toWriteRequest() {
            return new PrivacyTenantAuditWriteRequest(event, tenantId, tenantDetailKey);
        }
    }
}
