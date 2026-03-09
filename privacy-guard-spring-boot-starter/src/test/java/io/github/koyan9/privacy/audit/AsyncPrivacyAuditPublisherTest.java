/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncPrivacyAuditPublisherTest {

    @Test
    void publishesOnExecutorThread() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        PrivacyAuditEvent event = new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of());
        ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("privacy-audit-test");
            return thread;
        });

        try {
            AsyncPrivacyAuditPublisher publisher = new AsyncPrivacyAuditPublisher(
                    publishedEvent -> {
                        threadName.set(Thread.currentThread().getName());
                        latch.countDown();
                    },
                    executorService,
                    3,
                    Duration.ZERO,
                    (publishedEvent, attempts, exception) -> {
                    }
            );

            publisher.publish(event);

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals("privacy-audit-test", threadName.get());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void retriesBeforeSucceeding() {
        AtomicInteger attempts = new AtomicInteger();
        AsyncPrivacyAuditPublisher publisher = new AsyncPrivacyAuditPublisher(
                publishedEvent -> {
                    if (attempts.incrementAndGet() < 2) {
                        throw new IllegalStateException("first failure");
                    }
                },
                Runnable::run,
                3,
                Duration.ZERO,
                (publishedEvent, retryAttempts, exception) -> {
                }
        );

        publisher.publish(new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of()));

        assertEquals(2, attempts.get());
    }

    @Test
    void sendsEventToDeadLetterHandlerWhenRetriesAreExhausted() {
        AtomicReference<PrivacyAuditEvent> deadLetterEvent = new AtomicReference<>();
        AtomicReference<RuntimeException> deadLetterException = new AtomicReference<>();
        AtomicInteger deadLetterAttempts = new AtomicInteger();
        AsyncPrivacyAuditPublisher publisher = new AsyncPrivacyAuditPublisher(
                publishedEvent -> {
                    throw new IllegalStateException("always fails");
                },
                Runnable::run,
                2,
                Duration.ZERO,
                (event, attempts, exception) -> {
                    deadLetterEvent.set(event);
                    deadLetterAttempts.set(attempts);
                    deadLetterException.set(exception);
                }
        );
        PrivacyAuditEvent event = new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of());

        publisher.publish(event);

        assertEquals(event, deadLetterEvent.get());
        assertEquals(2, deadLetterAttempts.get());
        assertEquals("always fails", deadLetterException.get().getMessage());
    }

    @Test
    void fallsBackToSynchronousPublishingWhenExecutorRejectsTasks() {
        AtomicReference<String> threadName = new AtomicReference<>();
        PrivacyAuditEvent event = new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of());
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("rejected");
        };
        AsyncPrivacyAuditPublisher publisher = new AsyncPrivacyAuditPublisher(
                publishedEvent -> threadName.set(Thread.currentThread().getName()),
                rejectingExecutor,
                3,
                Duration.ZERO,
                (publishedEvent, attempts, exception) -> {
                }
        );

        publisher.publish(event);

        assertEquals(Thread.currentThread().getName(), threadName.get());
    }
}
