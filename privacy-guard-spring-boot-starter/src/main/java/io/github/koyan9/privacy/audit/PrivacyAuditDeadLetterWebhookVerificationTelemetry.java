/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

@StableSpi
public interface PrivacyAuditDeadLetterWebhookVerificationTelemetry {

    void recordFailure(PrivacyAuditDeadLetterWebhookVerificationException.Reason reason);

    static PrivacyAuditDeadLetterWebhookVerificationTelemetry noop() {
        return reason -> {
        };
    }
}
