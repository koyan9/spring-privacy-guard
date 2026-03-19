/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantContextHolder;
import io.github.koyan9.privacy.core.PrivacyTenantContextSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;

public class AsyncPrivacyAuditPublisher implements PrivacyAuditPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncPrivacyAuditPublisher.class);

    private final PrivacyAuditPublisher delegate;
    private final Executor executor;
    private final int maxAttempts;
    private final Duration backoff;
    private final PrivacyAuditDeadLetterHandler deadLetterHandler;

    public AsyncPrivacyAuditPublisher(
            PrivacyAuditPublisher delegate,
            Executor executor,
            int maxAttempts,
            Duration backoff,
            PrivacyAuditDeadLetterHandler deadLetterHandler
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoff = backoff == null ? Duration.ZERO : backoff;
        this.deadLetterHandler = Objects.requireNonNull(deadLetterHandler, "deadLetterHandler must not be null");
    }

    @Override
    public void publish(PrivacyAuditEvent event) {
        PrivacyTenantContextSnapshot tenantContextSnapshot = PrivacyTenantContextHolder.snapshot();
        Runnable task = tenantContextSnapshot.wrap(() -> publishWithRetry(event));

        try {
            executor.execute(task);
        } catch (RuntimeException exception) {
            LOGGER.warn("Async privacy audit executor rejected task, falling back to synchronous publishing: {}", exception.toString());
            task.run();
        }
    }

    private void publishWithRetry(PrivacyAuditEvent event) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                delegate.publish(event);
                return;
            } catch (RuntimeException exception) {
                lastException = exception;
                if (attempt < maxAttempts) {
                    sleepBackoff();
                }
            }
        }

        deadLetterHandler.handle(
                event,
                maxAttempts,
                lastException == null ? new IllegalStateException("Unknown async audit publishing failure") : lastException
        );
    }

    private void sleepBackoff() {
        long backoffMillis = Math.max(0L, backoff.toMillis());
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
