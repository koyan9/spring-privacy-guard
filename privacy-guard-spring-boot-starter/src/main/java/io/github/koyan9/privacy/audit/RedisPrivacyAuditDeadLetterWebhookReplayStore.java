/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RedisPrivacyAuditDeadLetterWebhookReplayStore implements PrivacyAuditDeadLetterWebhookReplayStore,
        PrivacyAuditDeadLetterWebhookReplayStoreStatsProvider,
        PrivacyAuditDeadLetterWebhookReplayStoreCleanupStatsProvider {

    private static final String DEFAULT_KEY_PREFIX = "privacy:audit:webhook:replay:";

    private final StringRedisTemplate redisTemplate;
    private final ValueOperations<String, String> valueOperations;
    private final String keyPrefix;
    private final int scanBatchSize;

    public RedisPrivacyAuditDeadLetterWebhookReplayStore(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_KEY_PREFIX, 500);
    }

    public RedisPrivacyAuditDeadLetterWebhookReplayStore(
            StringRedisTemplate redisTemplate,
            String keyPrefix,
            int scanBatchSize
    ) {
        this.redisTemplate = redisTemplate;
        this.valueOperations = redisTemplate.opsForValue();
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : DEFAULT_KEY_PREFIX;
        this.scanBatchSize = Math.max(1, scanBatchSize);
    }

    @Override
    public boolean markIfNew(String nonce, Instant now, java.time.Duration ttl) {
        String key = keyFor(nonce);
        String value = now.plus(ttl).toString();
        Boolean stored = valueOperations.setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(stored);
    }

    @Override
    public Map<String, Instant> snapshot() {
        return Map.copyOf(loadEntries(Instant.now()));
    }

    @Override
    public void clear() {
        List<String> keys = findKeys();
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Override
    public PrivacyAuditDeadLetterWebhookReplayStoreSnapshot snapshotStats(Instant now, Duration expiringSoonWindow) {
        Instant evaluationTime = now == null ? Instant.now() : now;
        Duration window = expiringSoonWindow == null ? Duration.ZERO : expiringSoonWindow;
        Map<String, Instant> entries = loadEntries(evaluationTime);
        long expiringSoon = 0L;
        Instant earliest = null;
        Instant latest = null;
        for (Instant expiry : entries.values()) {
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
                entries.size(),
                expiringSoon,
                earliest,
                latest,
                window.toString()
        );
    }

    @Override
    public PrivacyAuditDeadLetterWebhookReplayStoreCleanupSnapshot cleanupSnapshot() {
        return PrivacyAuditDeadLetterWebhookReplayStoreCleanupSnapshot.empty();
    }

    List<String> findKeys() {
        List<String> keys = redisTemplate.execute((RedisCallback<List<String>>) connection -> scanKeys(connection));
        return keys == null ? List.of() : keys;
    }

    List<String> readExpiryValues(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<String> values = valueOperations.multiGet(new ArrayList<>(keys));
        return values == null ? List.of() : values;
    }

    private Map<String, Instant> loadEntries(Instant referenceTime) {
        List<String> keys = findKeys();
        if (keys.isEmpty()) {
            return Map.of();
        }
        List<String> values = readExpiryValues(keys);
        Map<String, Instant> entries = new LinkedHashMap<>();
        for (int index = 0; index < keys.size(); index++) {
            String value = index < values.size() ? values.get(index) : null;
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                Instant expiry = Instant.parse(value);
                if (expiry.isBefore(referenceTime)) {
                    continue;
                }
                entries.put(toNonce(keys.get(index)), expiry);
            } catch (RuntimeException ignored) {
                // Ignore malformed or concurrently expired keys.
            }
        }
        return entries;
    }

    private List<String> scanKeys(RedisConnection connection) {
        if (connection == null) {
            return List.of();
        }
        RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
        if (serializer == null) {
            serializer = new StringRedisSerializer();
        }
        ScanOptions options = ScanOptions.scanOptions()
                .match(keyPrefix + "*")
                .count(scanBatchSize)
                .build();
        List<String> keys = new ArrayList<>();
        try (Cursor<byte[]> cursor = connection.scan(options)) {
            while (cursor.hasNext()) {
                String key = serializer.deserialize(cursor.next());
                if (key != null) {
                    keys.add(key);
                }
            }
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to scan Redis replay-store keys", ex);
        }
        return keys;
    }

    private String keyFor(String nonce) {
        return keyPrefix + nonce;
    }

    private String toNonce(String key) {
        return key.startsWith(keyPrefix) ? key.substring(keyPrefix.length()) : key;
    }
}
