/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.time.Duration;

public record PrivacyAuditDeadLetterWebhookVerificationSettings(
        String bearerToken,
        String signatureSecret,
        String signatureAlgorithm,
        String signatureHeader,
        String timestampHeader,
        String nonceHeader,
        Duration maxSkew
) {
}
