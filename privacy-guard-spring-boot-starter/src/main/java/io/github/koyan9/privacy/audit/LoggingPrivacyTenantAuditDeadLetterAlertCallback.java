/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingPrivacyTenantAuditDeadLetterAlertCallback implements PrivacyTenantAuditDeadLetterAlertCallback {

    private static final Logger logger = LoggerFactory.getLogger(LoggingPrivacyTenantAuditDeadLetterAlertCallback.class);
    private final PrivacyTenantAuditTelemetry telemetry;

    public LoggingPrivacyTenantAuditDeadLetterAlertCallback() {
        this(PrivacyTenantAuditTelemetry.noop());
    }

    public LoggingPrivacyTenantAuditDeadLetterAlertCallback(PrivacyTenantAuditTelemetry telemetry) {
        this.telemetry = telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry;
    }

    @Override
    public void handle(String tenantId, PrivacyAuditDeadLetterAlertEvent event) {
        try {
            PrivacyAuditDeadLetterBacklogSnapshot snapshot = event.currentSnapshot();
            PrivacyAuditDeadLetterBacklogState previousState = event.previousSnapshot() == null
                    ? null
                    : event.previousSnapshot().state();
            if (event.recovery()) {
                logger.info(
                        "Privacy audit tenant dead-letter backlog recovered tenant={} previousState={} total={} warningThreshold={} downThreshold={}",
                        tenantId,
                        previousState,
                        snapshot.total(),
                        snapshot.warningThreshold(),
                        snapshot.downThreshold()
                );
                telemetry.recordAlertDelivery(tenantId, "logging", "success");
                return;
            }
            if (snapshot.state() == PrivacyAuditDeadLetterBacklogState.DOWN) {
                logger.error(
                        "Privacy audit tenant dead-letter backlog alert tenant={} state={} previousState={} total={} warningThreshold={} downThreshold={}",
                        tenantId,
                        snapshot.state(),
                        previousState,
                        snapshot.total(),
                        snapshot.warningThreshold(),
                        snapshot.downThreshold()
                );
                telemetry.recordAlertDelivery(tenantId, "logging", "success");
                return;
            }
            logger.warn(
                    "Privacy audit tenant dead-letter backlog alert tenant={} state={} previousState={} total={} warningThreshold={} downThreshold={}",
                    tenantId,
                    snapshot.state(),
                    previousState,
                    snapshot.total(),
                    snapshot.warningThreshold(),
                    snapshot.downThreshold()
            );
            telemetry.recordAlertDelivery(tenantId, "logging", "success");
        } catch (RuntimeException ex) {
            telemetry.recordAlertDelivery(tenantId, "logging", "failure");
            throw ex;
        }
    }
}
