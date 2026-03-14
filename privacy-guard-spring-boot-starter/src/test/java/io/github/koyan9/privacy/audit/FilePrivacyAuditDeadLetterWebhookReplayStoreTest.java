/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FilePrivacyAuditDeadLetterWebhookReplayStoreTest {

    @Test
    void persistsReplayNonceStoreAcrossInstances() throws Exception {
        Path storeFile = Files.createTempFile("privacy-guard-replay-store", ".json");
        Files.deleteIfExists(storeFile);
        ObjectMapper objectMapper = new ObjectMapper();

        FilePrivacyAuditDeadLetterWebhookReplayStore first = new FilePrivacyAuditDeadLetterWebhookReplayStore(storeFile, objectMapper);
        assertThat(first.markIfNew("nonce-1", Instant.parse("2026-03-09T00:00:00Z"), Duration.ofMinutes(5))).isTrue();
        assertThat(Files.exists(storeFile)).isTrue();

        FilePrivacyAuditDeadLetterWebhookReplayStore second = new FilePrivacyAuditDeadLetterWebhookReplayStore(storeFile, objectMapper);
        assertThat(second.markIfNew("nonce-1", Instant.parse("2026-03-09T00:01:00Z"), Duration.ofMinutes(5))).isFalse();

        second.clear();
        FilePrivacyAuditDeadLetterWebhookReplayStore third = new FilePrivacyAuditDeadLetterWebhookReplayStore(storeFile, objectMapper);
        assertThat(third.markIfNew("nonce-1", Instant.parse("2026-03-09T00:02:00Z"), Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void snapshotRemovesExpiredEntries() throws Exception {
        Path storeFile = Files.createTempFile("privacy-guard-replay-store", ".json");
        Files.deleteIfExists(storeFile);
        ObjectMapper objectMapper = new ObjectMapper();
        FilePrivacyAuditDeadLetterWebhookReplayStore store = new FilePrivacyAuditDeadLetterWebhookReplayStore(storeFile, objectMapper);

        Instant expiredNow = Instant.now().minusSeconds(10);
        assertThat(store.markIfNew("nonce-expired", expiredNow, Duration.ofSeconds(1))).isTrue();

        assertThat(store.snapshot()).isEmpty();

        FilePrivacyAuditDeadLetterWebhookReplayStore reloaded = new FilePrivacyAuditDeadLetterWebhookReplayStore(storeFile, objectMapper);
        assertThat(reloaded.markIfNew("nonce-expired", Instant.now(), Duration.ofMinutes(1))).isTrue();
    }
}
