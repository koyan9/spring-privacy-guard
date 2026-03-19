/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.time.Instant;

@StableSpi
public record PrivacyAuditDeadLetterWebhookReplayStoreCleanupSnapshot(
        long lastCleanupCount,
        long lastCleanupDurationMillis,
        Instant lastCleanupAt
) {
    public static PrivacyAuditDeadLetterWebhookReplayStoreCleanupSnapshot empty() {
        return new PrivacyAuditDeadLetterWebhookReplayStoreCleanupSnapshot(0L, 0L, null);
    }
}
