/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

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

public class BufferedPrivacyAuditPublisher implements PrivacyAuditPublisher, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(BufferedPrivacyAuditPublisher.class);

    private final PrivacyAuditRepository repository;
    private final ScheduledExecutorService executor;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final PrivacyAuditDeadLetterHandler deadLetterHandler;
    private final Queue<PrivacyAuditEvent> queue = new ConcurrentLinkedQueue<>();
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
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoff = retryBackoff == null ? Duration.ZERO : retryBackoff;
        this.deadLetterHandler = Objects.requireNonNull(deadLetterHandler, "deadLetterHandler must not be null");
        long intervalMillis = Math.max(1L, flushInterval == null ? 500L : flushInterval.toMillis());
        this.executor.scheduleWithFixedDelay(this::flushSafely, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void publish(PrivacyAuditEvent event) {
        if (event == null) {
            return;
        }
        queue.offer(event);
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
            List<PrivacyAuditEvent> batch = drainBatch();
            if (batch.isEmpty()) {
                return;
            }
            saveBatchWithRetry(batch);
        }
    }

    private List<PrivacyAuditEvent> drainBatch() {
        List<PrivacyAuditEvent> batch = new ArrayList<>(batchSize);
        while (batch.size() < batchSize) {
            PrivacyAuditEvent event = queue.poll();
            if (event == null) {
                break;
            }
            queuedCount.decrementAndGet();
            batch.add(event);
        }
        return batch;
    }

    private void saveBatchWithRetry(List<PrivacyAuditEvent> batch) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                repository.saveAll(batch);
                return;
            } catch (RuntimeException exception) {
                lastException = exception;
                if (attempt < maxAttempts) {
                    sleepBackoff();
                }
            }
        }

        RuntimeException failure = lastException == null ? new IllegalStateException("Unknown buffered audit persistence failure") : lastException;
        for (PrivacyAuditEvent event : batch) {
            deadLetterHandler.handle(event, maxAttempts, failure);
        }
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
}
