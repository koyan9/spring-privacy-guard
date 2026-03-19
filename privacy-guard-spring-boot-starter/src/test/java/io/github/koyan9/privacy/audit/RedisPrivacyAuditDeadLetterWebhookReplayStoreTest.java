/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPrivacyAuditDeadLetterWebhookReplayStoreTest {

    @Test
    void marksNonceThroughNativeRedisExpiry() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("privacy:audit:webhook:replay:nonce-1"), any(String.class), eq(Duration.ofMinutes(5))))
                .thenReturn(Boolean.TRUE);
        RedisPrivacyAuditDeadLetterWebhookReplayStore store =
                new RedisPrivacyAuditDeadLetterWebhookReplayStore(redisTemplate, "privacy:audit:webhook:replay:", 500);

        boolean stored = store.markIfNew("nonce-1", Instant.EPOCH, Duration.ofMinutes(5));

        assertThat(stored).isTrue();
        verify(valueOperations).setIfAbsent(eq("privacy:audit:webhook:replay:nonce-1"), any(String.class), eq(Duration.ofMinutes(5)));
    }

    @Test
    void snapshotsStatsFromRedisEntries() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Instant now = Instant.now();
        Instant soon = now.plus(Duration.ofMinutes(3));
        Instant later = now.plus(Duration.ofMinutes(10));
        Instant expired = now.minusSeconds(1);
        RedisPrivacyAuditDeadLetterWebhookReplayStore store = new StubRedisReplayStore(
                redisTemplate,
                "privacy:audit:webhook:replay:",
                List.of(
                        "privacy:audit:webhook:replay:nonce-1",
                        "privacy:audit:webhook:replay:nonce-2",
                        "privacy:audit:webhook:replay:nonce-3"
                ),
                List.of(
                        soon.toString(),
                        later.toString(),
                        expired.toString()
                )
        );

        Map<String, Instant> snapshot = store.snapshot();
        PrivacyAuditDeadLetterWebhookReplayStoreSnapshot stats = store.snapshotStats(now, Duration.ofMinutes(5));

        assertThat(snapshot).containsOnlyKeys("nonce-1", "nonce-2");
        assertThat(stats.count()).isEqualTo(2);
        assertThat(stats.expiringSoon()).isEqualTo(1L);
        assertThat(stats.earliestExpiry()).isEqualTo(soon);
        assertThat(stats.latestExpiry()).isEqualTo(later);
        assertThat(stats.expiringSoonWindow()).isEqualTo("PT5M");
        assertThat(store.cleanupSnapshot()).isEqualTo(PrivacyAuditDeadLetterWebhookReplayStoreCleanupSnapshot.empty());
    }

    @Test
    void clearsAllRedisReplayKeys() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        List<String> keys = List.of(
                "privacy:audit:webhook:replay:nonce-1",
                "privacy:audit:webhook:replay:nonce-2"
        );
        RedisPrivacyAuditDeadLetterWebhookReplayStore store = new StubRedisReplayStore(
                redisTemplate,
                "privacy:audit:webhook:replay:",
                keys,
                List.of("2026-03-18T00:03:00Z", "2026-03-18T00:10:00Z")
        );

        store.clear();

        verify(redisTemplate).delete(keys);
    }

    private static final class StubRedisReplayStore extends RedisPrivacyAuditDeadLetterWebhookReplayStore {

        private final List<String> keys;
        private final List<String> values;

        private StubRedisReplayStore(
                StringRedisTemplate redisTemplate,
                String keyPrefix,
                List<String> keys,
                List<String> values
        ) {
            super(redisTemplate, keyPrefix, 500);
            this.keys = keys;
            this.values = values;
        }

        @Override
        List<String> findKeys() {
            return keys;
        }

        @Override
        List<String> readExpiryValues(java.util.Collection<String> ignored) {
            return values;
        }
    }
}
