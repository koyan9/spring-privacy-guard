/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterAlertCallback;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterAlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
class DemoDeadLetterAlertCallback implements PrivacyAuditDeadLetterAlertCallback {

    private static final Logger logger = LoggerFactory.getLogger(DemoDeadLetterAlertCallback.class);

    private final AtomicReference<PrivacyAuditDeadLetterAlertEvent> lastAlert = new AtomicReference<>();

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

    Optional<PrivacyAuditDeadLetterAlertEvent> lastAlert() {
        return Optional.ofNullable(lastAlert.get());
    }

    void reset() {
        lastAlert.set(null);
    }
}
