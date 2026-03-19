/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPrivacyAuditDeadLetterWebhookReplayStore implements PrivacyAuditDeadLetterWebhookReplayStore,
        PrivacyAuditDeadLetterWebhookReplayStoreStatsProvider,
        PrivacyAuditDeadLetterWebhookReplayStoreCleanupStatsProvider {

    private final ConcurrentHashMap<String, Instant> seenNonces = new ConcurrentHashMap<>();
    private PrivacyAuditDeadLetterWebhookReplayStoreCleanupSnapshot lastCleanup =
            PrivacyAuditDeadLetterWebhookReplayStoreCleanupSnapshot.empty();

    @Override
    public synchronized boolean markIfNew(String nonce, Instant now, java.time.Duration ttl) {
        cleanup(now);
        Instant expiresAt = now.plus(ttl);
        return seenNonces.putIfAbsent(nonce, expiresAt) == null;
    }

    @Override
    public synchronized Map<String, Instant> snapshot() {
        cleanup(Instant.now());
        return Map.copyOf(new LinkedHashMap<>(seenNonces));
    }

    @Override
    public synchronized void clear() {
        seenNonces.clear();
    }

    @Override
    public synchronized PrivacyAuditDeadLetterWebhookReplayStoreSnapshot snapshotStats(Instant now, Duration expiringSoonWindow) {
        Instant evaluationTime = now == null ? Instant.now() : now;
        Duration window = expiringSoonWindow == null ? Duration.ZERO : expiringSoonWindow;
        cleanup(evaluationTime);
        long expiringSoon = 0L;
        Instant earliest = null;
        Instant latest = null;
        for (Instant expiry : seenNonces.values()) {
            if (!expiry.isBefore(evaluationTime) && !expiry.isAfter(evaluationTime.plus(window))) {
                expiringSoon++;
            }
            if (earliest == null || expiry.isBefore(earliest)) {
                earliest = expiry;
            }
            if (latest == null || expiry.isAfter(latest)) {
                latest = expiry;
            }
        }
        return new PrivacyAuditDeadLetterWebhookReplayStoreSnapshot(
                seenNonces.size(),
                expiringSoon,
                earliest,
                latest,
                window.toString()
        );
    }

    @Override
    public synchronized PrivacyAuditDeadLetterWebhookReplayStoreCleanupSnapshot cleanupSnapshot() {
        return lastCleanup;
    }

    private void cleanup(Instant now) {
        long startNanos = System.nanoTime();
        long removed = 0L;
        for (Map.Entry<String, Instant> entry : seenNonces.entrySet()) {
            if (entry.getValue().isBefore(now)) {
                seenNonces.remove(entry.getKey(), entry.getValue());
                removed++;
            }
        }
        recordCleanup(removed, startNanos, now);
    }

    private void recordCleanup(long removed, long startNanos, Instant now) {
        long durationMillis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        lastCleanup = new PrivacyAuditDeadLetterWebhookReplayStoreCleanupSnapshot(removed, durationMillis, now);
    }
}
