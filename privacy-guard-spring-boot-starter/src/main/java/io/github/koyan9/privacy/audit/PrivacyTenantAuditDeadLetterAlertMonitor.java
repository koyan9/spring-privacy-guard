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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PrivacyTenantAuditDeadLetterAlertMonitor implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(PrivacyTenantAuditDeadLetterAlertMonitor.class);

    private final PrivacyTenantAuditDeadLetterObservationService observationService;
    private final List<PrivacyTenantAuditDeadLetterAlertCallback> callbacks;
    private final List<String> tenantIds;
    private final PrivacyTenantDeadLetterObservabilityPolicyResolver observabilityPolicyResolver;
    private final boolean defaultNotifyOnRecovery;
    private final PrivacyTenantAuditTelemetry telemetry;
    private final Map<String, PrivacyAuditDeadLetterBacklogSnapshot> previousSnapshots = new LinkedHashMap<>();
    private final ScheduledFuture<?> future;

    public PrivacyTenantAuditDeadLetterAlertMonitor(
            PrivacyTenantAuditDeadLetterObservationService observationService,
            List<PrivacyTenantAuditDeadLetterAlertCallback> callbacks,
            List<String> tenantIds,
            ScheduledExecutorService executor,
            Duration checkInterval,
            boolean notifyOnRecovery
    ) {
        this(
                observationService,
                callbacks,
                tenantIds,
                executor,
                checkInterval,
                PrivacyTenantDeadLetterObservabilityPolicyResolver.noop(),
                notifyOnRecovery,
                PrivacyTenantAuditTelemetry.noop()
        );
    }

    public PrivacyTenantAuditDeadLetterAlertMonitor(
            PrivacyTenantAuditDeadLetterObservationService observationService,
            List<PrivacyTenantAuditDeadLetterAlertCallback> callbacks,
            List<String> tenantIds,
            ScheduledExecutorService executor,
            Duration checkInterval,
            PrivacyTenantDeadLetterObservabilityPolicyResolver observabilityPolicyResolver,
            boolean notifyOnRecovery,
            PrivacyTenantAuditTelemetry telemetry
    ) {
        this.observationService = observationService;
        this.callbacks = List.copyOf(callbacks);
        this.tenantIds = tenantIds == null ? List.of() : List.copyOf(tenantIds);
        this.observabilityPolicyResolver = observabilityPolicyResolver == null
                ? PrivacyTenantDeadLetterObservabilityPolicyResolver.noop()
                : observabilityPolicyResolver;
        this.defaultNotifyOnRecovery = notifyOnRecovery;
        this.telemetry = telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry;
        long delayMillis = Math.max(1L, checkInterval.toMillis());
        this.future = executor.scheduleWithFixedDelay(this::checkAndNotify, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
    }

    void checkAndNotify() {
        try {
            for (String tenantId : tenantIds) {
                if (tenantId == null || tenantId.isBlank()) {
                    continue;
                }
                PrivacyAuditDeadLetterBacklogSnapshot currentSnapshot = observationService.currentSnapshot(tenantId);
                PrivacyAuditDeadLetterBacklogSnapshot previousSnapshot = previousSnapshots.put(tenantId, currentSnapshot);
                if (previousSnapshot == null) {
                    if (currentSnapshot.state() != PrivacyAuditDeadLetterBacklogState.CLEAR) {
                        telemetry.recordAlertTransition(tenantId, currentSnapshot.state().name(), false);
                        publish(tenantId, currentSnapshot, null);
                    }
                    continue;
                }
                if (currentSnapshot.state() == previousSnapshot.state()) {
                    continue;
                }
                boolean recovery = previousSnapshot.state() != PrivacyAuditDeadLetterBacklogState.CLEAR
                        && currentSnapshot.state() == PrivacyAuditDeadLetterBacklogState.CLEAR;
                telemetry.recordAlertTransition(tenantId, currentSnapshot.state().name(), recovery);
                if (recovery && !notifyOnRecovery(tenantId)) {
                    continue;
                }
                publish(tenantId, currentSnapshot, previousSnapshot);
            }
        } catch (Exception ex) {
            logger.warn("Failed to evaluate privacy audit tenant dead-letter backlog alert", ex);
        }
    }

    private void publish(
            String tenantId,
            PrivacyAuditDeadLetterBacklogSnapshot currentSnapshot,
            PrivacyAuditDeadLetterBacklogSnapshot previousSnapshot
    ) {
        PrivacyAuditDeadLetterAlertEvent event = new PrivacyAuditDeadLetterAlertEvent(
                Instant.now(),
                currentSnapshot,
                previousSnapshot
        );
        for (PrivacyTenantAuditDeadLetterAlertCallback callback : callbacks) {
            if (!callback.supportsTenant(tenantId)) {
                continue;
            }
            try {
                callback.handle(tenantId, event);
            } catch (Exception ex) {
                logger.warn("Privacy audit tenant dead-letter alert callback failed tenant={}", tenantId, ex);
            }
        }
    }

    private boolean notifyOnRecovery(String tenantId) {
        PrivacyTenantDeadLetterObservabilityPolicy policy = observabilityPolicyResolver.resolve(tenantId);
        if (policy == null) {
            policy = PrivacyTenantDeadLetterObservabilityPolicy.none();
        }
        return policy.resolveNotifyOnRecovery(defaultNotifyOnRecovery);
    }

    @Override
    public void destroy() {
        future.cancel(false);
    }
}
