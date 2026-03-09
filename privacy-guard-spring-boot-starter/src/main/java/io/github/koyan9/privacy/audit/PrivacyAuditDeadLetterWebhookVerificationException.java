/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

public class PrivacyAuditDeadLetterWebhookVerificationException extends RuntimeException {

    private final Reason reason;

    public PrivacyAuditDeadLetterWebhookVerificationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public PrivacyAuditDeadLetterWebhookVerificationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_AUTHORIZATION,
        MISSING_SIGNATURE_HEADERS,
        INVALID_TIMESTAMP,
        EXPIRED_TIMESTAMP,
        INVALID_SIGNATURE,
        REPLAY_DETECTED
    }
}
