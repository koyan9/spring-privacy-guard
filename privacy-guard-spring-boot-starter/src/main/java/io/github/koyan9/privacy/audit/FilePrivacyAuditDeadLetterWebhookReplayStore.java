/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class FilePrivacyAuditDeadLetterWebhookReplayStore implements PrivacyAuditDeadLetterWebhookReplayStore,
        PrivacyAuditDeadLetterWebhookReplayStoreStatsProvider {

    private static final TypeReference<Map<String, String>> STORE_TYPE = new TypeReference<>() {
    };

    private final Path storeFile;
    private final ObjectMapper objectMapper;
    private final Map<String, Instant> seenNonces = new LinkedHashMap<>();

    public FilePrivacyAuditDeadLetterWebhookReplayStore(Path storeFile, ObjectMapper objectMapper) {
        this.storeFile = storeFile.toAbsolutePath();
        this.objectMapper = objectMapper;
        load();
    }

    @Override
    public synchronized boolean markIfNew(String nonce, Instant now, java.time.Duration ttl) {
        cleanup(now);
        Instant expiresAt = now.plus(ttl);
        if (seenNonces.containsKey(nonce)) {
            return false;
        }
        seenNonces.put(nonce, expiresAt);
        persist();
        return true;
    }

    @Override
    public synchronized Map<String, Instant> snapshot() {
        cleanup(Instant.now());
        return Map.copyOf(new LinkedHashMap<>(seenNonces));
    }

    @Override
    public synchronized void clear() {
        seenNonces.clear();
        persist();
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

    private void cleanup(Instant now) {
        boolean changed = false;
        for (Map.Entry<String, Instant> entry : new LinkedHashMap<>(seenNonces).entrySet()) {
            if (entry.getValue().isBefore(now)) {
                seenNonces.remove(entry.getKey());
                changed = true;
            }
        }
        if (changed) {
            persist();
        }
    }

    private void load() {
        if (!Files.exists(storeFile)) {
            return;
        }
        try {
            Map<String, String> raw = objectMapper.readValue(storeFile.toFile(), STORE_TYPE);
            seenNonces.clear();
            raw.forEach((nonce, expiry) -> seenNonces.put(nonce, Instant.parse(expiry)));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load webhook replay protection store", ex);
        }
    }

    private void persist() {
        try {
            Path parent = storeFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, String> raw = new LinkedHashMap<>();
            seenNonces.forEach((nonce, expiry) -> raw.put(nonce, expiry.toString()));
            objectMapper.writeValue(storeFile.toFile(), raw);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist webhook replay protection store", ex);
        }
    }
}
