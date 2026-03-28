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
    private final PrivacyTenantDeadLetterAlertDeliveryPolicyResolver deliveryPolicyResolver;
    private final boolean defaultEnabled;

    public LoggingPrivacyTenantAuditDeadLetterAlertCallback() {
        this(PrivacyTenantAuditTelemetry.noop(), PrivacyTenantDeadLetterAlertDeliveryPolicyResolver.noop(), true);
    }

    public LoggingPrivacyTenantAuditDeadLetterAlertCallback(PrivacyTenantAuditTelemetry telemetry) {
        this(telemetry, PrivacyTenantDeadLetterAlertDeliveryPolicyResolver.noop(), true);
    }

    public LoggingPrivacyTenantAuditDeadLetterAlertCallback(
            PrivacyTenantAuditTelemetry telemetry,
            PrivacyTenantDeadLetterAlertDeliveryPolicyResolver deliveryPolicyResolver,
            boolean defaultEnabled
    ) {
        this.telemetry = telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry;
        this.deliveryPolicyResolver = deliveryPolicyResolver == null
                ? PrivacyTenantDeadLetterAlertDeliveryPolicyResolver.noop()
                : deliveryPolicyResolver;
        this.defaultEnabled = defaultEnabled;
    }

    @Override
    public boolean supportsTenant(String tenantId) {
        return tenantId != null && !tenantId.isBlank() && resolveEnabled(tenantId);
    }

    @Override
    public void handle(String tenantId, PrivacyAuditDeadLetterAlertEvent event) {
        if (!supportsTenant(tenantId)) {
            return;
        }
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

    private boolean resolveEnabled(String tenantId) {
        PrivacyTenantDeadLetterAlertDeliveryPolicy policy = deliveryPolicyResolver.resolve(tenantId);
        if (policy == null) {
            policy = PrivacyTenantDeadLetterAlertDeliveryPolicy.none();
        }
        return policy.resolveLoggingEnabled(defaultEnabled);
    }
}
