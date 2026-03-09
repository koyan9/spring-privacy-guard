/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PrivacyAuditDeadLetterAlertMonitor implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(PrivacyAuditDeadLetterAlertMonitor.class);

    private final PrivacyAuditDeadLetterObservationService observationService;
    private final List<PrivacyAuditDeadLetterAlertCallback> callbacks;
    private final boolean notifyOnRecovery;
    private final AtomicReference<PrivacyAuditDeadLetterBacklogSnapshot> previousSnapshot = new AtomicReference<>();
    private final ScheduledFuture<?> future;

    public PrivacyAuditDeadLetterAlertMonitor(
            PrivacyAuditDeadLetterObservationService observationService,
            List<PrivacyAuditDeadLetterAlertCallback> callbacks,
            ScheduledExecutorService executor,
            Duration checkInterval,
            boolean notifyOnRecovery
    ) {
        this.observationService = observationService;
        this.callbacks = List.copyOf(callbacks);
        this.notifyOnRecovery = notifyOnRecovery;
        long delayMillis = Math.max(1L, checkInterval.toMillis());
        this.future = executor.scheduleWithFixedDelay(this::checkAndNotify, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
    }

    void checkAndNotify() {
        try {
            PrivacyAuditDeadLetterBacklogSnapshot currentSnapshot = observationService.currentSnapshot();
            PrivacyAuditDeadLetterBacklogSnapshot previous = previousSnapshot.getAndSet(currentSnapshot);
            if (previous == null) {
                if (currentSnapshot.state() != PrivacyAuditDeadLetterBacklogState.CLEAR) {
                    publish(currentSnapshot, null);
                }
                return;
            }
            if (currentSnapshot.state() == previous.state()) {
                return;
            }
            if (currentSnapshot.state() == PrivacyAuditDeadLetterBacklogState.CLEAR && !notifyOnRecovery) {
                return;
            }
            publish(currentSnapshot, previous);
        } catch (Exception ex) {
            logger.warn("Failed to evaluate privacy audit dead-letter backlog alert", ex);
        }
    }

    private void publish(
            PrivacyAuditDeadLetterBacklogSnapshot currentSnapshot,
            PrivacyAuditDeadLetterBacklogSnapshot previousSnapshot
    ) {
        PrivacyAuditDeadLetterAlertEvent event = new PrivacyAuditDeadLetterAlertEvent(
                Instant.now(),
                currentSnapshot,
                previousSnapshot
        );
        for (PrivacyAuditDeadLetterAlertCallback callback : callbacks) {
            try {
                callback.handle(event);
            } catch (Exception ex) {
                logger.warn("Privacy audit dead-letter alert callback failed", ex);
            }
        }
    }

    @Override
    public void destroy() {
        future.cancel(false);
    }
}
