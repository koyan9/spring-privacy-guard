/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

public interface PrivacyAuditDeadLetterWebhookAlertTelemetry {

    void recordAttempt();

    void recordSuccess(int attempts);

    void recordFailure(int attempts);

    void recordRetryScheduled(int nextAttempt);

    default void recordFailureDetail(WebhookAlertFailureDetail detail) {
    }

    static PrivacyAuditDeadLetterWebhookAlertTelemetry noop() {
        return new PrivacyAuditDeadLetterWebhookAlertTelemetry() {
            @Override
            public void recordAttempt() {
            }

            @Override
            public void recordSuccess(int attempts) {
            }

            @Override
            public void recordFailure(int attempts) {
            }

            @Override
            public void recordRetryScheduled(int nextAttempt) {
            }

            @Override
            public void recordFailureDetail(WebhookAlertFailureDetail detail) {
            }
        };
    }
}
