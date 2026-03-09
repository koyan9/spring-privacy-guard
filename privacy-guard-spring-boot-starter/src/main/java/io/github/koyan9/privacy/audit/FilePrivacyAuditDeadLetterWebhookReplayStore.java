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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class FilePrivacyAuditDeadLetterWebhookReplayStore implements PrivacyAuditDeadLetterWebhookReplayStore {

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
        return Map.copyOf(new LinkedHashMap<>(seenNonces));
    }

    @Override
    public synchronized void clear() {
        seenNonces.clear();
        persist();
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
