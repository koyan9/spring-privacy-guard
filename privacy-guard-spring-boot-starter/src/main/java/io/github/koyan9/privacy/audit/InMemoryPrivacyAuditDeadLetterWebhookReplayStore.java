/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPrivacyAuditDeadLetterWebhookReplayStore implements PrivacyAuditDeadLetterWebhookReplayStore {

    private final ConcurrentHashMap<String, Instant> seenNonces = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean markIfNew(String nonce, Instant now, java.time.Duration ttl) {
        cleanup(now);
        Instant expiresAt = now.plus(ttl);
        return seenNonces.putIfAbsent(nonce, expiresAt) == null;
    }

    @Override
    public synchronized Map<String, Instant> snapshot() {
        return Map.copyOf(new LinkedHashMap<>(seenNonces));
    }

    @Override
    public synchronized void clear() {
        seenNonces.clear();
    }

    private void cleanup(Instant now) {
        for (Map.Entry<String, Instant> entry : seenNonces.entrySet()) {
            if (entry.getValue().isBefore(now)) {
                seenNonces.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
