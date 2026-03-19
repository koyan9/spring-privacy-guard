/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.time.Instant;

@StableSpi
public record PrivacyAuditDeadLetterWebhookReplayStoreSnapshot(
        int count,
        long expiringSoon,
        Instant earliestExpiry,
        Instant latestExpiry,
        String expiringSoonWindow
) {
}
