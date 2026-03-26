/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterAlertEvent;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterAlertCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
class DemoDeadLetterAlertCallback implements PrivacyAuditDeadLetterAlertCallback, PrivacyTenantAuditDeadLetterAlertCallback {

    private static final Logger logger = LoggerFactory.getLogger(DemoDeadLetterAlertCallback.class);

    private final AtomicReference<PrivacyAuditDeadLetterAlertEvent> lastAlert = new AtomicReference<>();
    private final AtomicReference<TenantAlertRecord> lastTenantAlert = new AtomicReference<>();

    @Override
    public void handle(PrivacyAuditDeadLetterAlertEvent event) {
        lastAlert.set(event);
        if (event.recovery()) {
            logger.info(
                    "dead-letter backlog recovered state={} total={}",
                    event.currentSnapshot().state(),
                    event.currentSnapshot().total()
            );
            return;
        }
        logger.warn(
                "dead-letter backlog alert state={} total={} warningThreshold={} downThreshold={}",
                event.currentSnapshot().state(),
                event.currentSnapshot().total(),
                event.currentSnapshot().warningThreshold(),
                event.currentSnapshot().downThreshold()
        );
    }

    @Override
    public void handle(String tenantId, PrivacyAuditDeadLetterAlertEvent event) {
        lastTenantAlert.set(new TenantAlertRecord(tenantId, event));
        if (event.recovery()) {
            logger.info(
                    "tenant dead-letter backlog recovered tenant={} state={} total={}",
                    tenantId,
                    event.currentSnapshot().state(),
                    event.currentSnapshot().total()
            );
            return;
        }
        logger.warn(
                "tenant dead-letter backlog alert tenant={} state={} total={} warningThreshold={} downThreshold={}",
                tenantId,
                event.currentSnapshot().state(),
                event.currentSnapshot().total(),
                event.currentSnapshot().warningThreshold(),
                event.currentSnapshot().downThreshold()
        );
    }

    Optional<PrivacyAuditDeadLetterAlertEvent> lastAlert() {
        return Optional.ofNullable(lastAlert.get());
    }

    Optional<TenantAlertRecord> lastTenantAlert() {
        return Optional.ofNullable(lastTenantAlert.get());
    }

    void reset() {
        lastAlert.set(null);
        lastTenantAlert.set(null);
    }

    record TenantAlertRecord(String tenantId, PrivacyAuditDeadLetterAlertEvent event) {
    }
}
