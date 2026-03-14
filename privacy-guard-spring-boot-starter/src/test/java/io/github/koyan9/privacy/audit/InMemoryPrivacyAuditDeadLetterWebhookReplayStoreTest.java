/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPrivacyAuditDeadLetterWebhookReplayStoreTest {

    @Test
    void snapshotDropsExpiredEntries() {
        InMemoryPrivacyAuditDeadLetterWebhookReplayStore store = new InMemoryPrivacyAuditDeadLetterWebhookReplayStore();
        Instant expiredNow = Instant.now().minusSeconds(10);

        assertThat(store.markIfNew("nonce-1", expiredNow, Duration.ofSeconds(1))).isTrue();

        assertThat(store.snapshot()).isEmpty();
        assertThat(store.markIfNew("nonce-1", Instant.now(), Duration.ofMinutes(1))).isTrue();
    }
}
