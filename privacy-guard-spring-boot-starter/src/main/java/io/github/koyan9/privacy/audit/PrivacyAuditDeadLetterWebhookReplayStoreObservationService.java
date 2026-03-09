/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;

public class PrivacyAuditDeadLetterWebhookReplayStoreObservationService {

    private final PrivacyAuditDeadLetterWebhookReplayStore replayStore;
    private final Duration expiringSoonWindow;

    public PrivacyAuditDeadLetterWebhookReplayStoreObservationService(
            PrivacyAuditDeadLetterWebhookReplayStore replayStore,
            Duration expiringSoonWindow
    ) {
        this.replayStore = replayStore;
        this.expiringSoonWindow = expiringSoonWindow;
    }

    public PrivacyAuditDeadLetterWebhookReplayStoreSnapshot currentSnapshot() {
        Map<String, Instant> snapshot = replayStore.snapshot();
        Instant now = Instant.now();
        long expiringSoon = snapshot.values().stream()
                .filter(expiry -> !expiry.isBefore(now) && !expiry.isAfter(now.plus(expiringSoonWindow)))
                .count();
        Instant earliest = snapshot.values().stream().min(Comparator.naturalOrder()).orElse(null);
        Instant latest = snapshot.values().stream().max(Comparator.naturalOrder()).orElse(null);
        return new PrivacyAuditDeadLetterWebhookReplayStoreSnapshot(
                snapshot.size(),
                expiringSoon,
                earliest,
                latest,
                expiringSoonWindow.toString()
        );
    }
}
