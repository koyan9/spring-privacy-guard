/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingPrivacyAuditDeadLetterAlertCallback implements PrivacyAuditDeadLetterAlertCallback {

    private static final Logger logger = LoggerFactory.getLogger(LoggingPrivacyAuditDeadLetterAlertCallback.class);

    @Override
    public void handle(PrivacyAuditDeadLetterAlertEvent event) {
        PrivacyAuditDeadLetterBacklogSnapshot snapshot = event.currentSnapshot();
        PrivacyAuditDeadLetterBacklogState previousState = event.previousSnapshot() == null
                ? null
                : event.previousSnapshot().state();
        if (event.recovery()) {
            logger.info(
                    "Privacy audit dead-letter backlog recovered previousState={} total={} warningThreshold={} downThreshold={}",
                    previousState,
                    snapshot.total(),
                    snapshot.warningThreshold(),
                    snapshot.downThreshold()
            );
            return;
        }
        if (snapshot.state() == PrivacyAuditDeadLetterBacklogState.DOWN) {
            logger.error(
                    "Privacy audit dead-letter backlog alert state={} previousState={} total={} warningThreshold={} downThreshold={}",
                    snapshot.state(),
                    previousState,
                    snapshot.total(),
                    snapshot.warningThreshold(),
                    snapshot.downThreshold()
            );
            return;
        }
        logger.warn(
                "Privacy audit dead-letter backlog alert state={} previousState={} total={} warningThreshold={} downThreshold={}",
                snapshot.state(),
                previousState,
                snapshot.total(),
                snapshot.warningThreshold(),
                snapshot.downThreshold()
        );
    }
}
