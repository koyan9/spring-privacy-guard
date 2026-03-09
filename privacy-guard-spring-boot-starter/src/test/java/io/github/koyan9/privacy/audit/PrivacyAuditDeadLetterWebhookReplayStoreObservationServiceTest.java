/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyAuditDeadLetterWebhookReplayStoreObservationServiceTest {

    @Test
    void computesReplayStoreSnapshot() {
        InMemoryPrivacyAuditDeadLetterWebhookReplayStore replayStore = new InMemoryPrivacyAuditDeadLetterWebhookReplayStore();
        Instant now = Instant.now();
        replayStore.markIfNew("nonce-a", now, Duration.ofMinutes(1));
        replayStore.markIfNew("nonce-b", now, Duration.ofMinutes(10));

        PrivacyAuditDeadLetterWebhookReplayStoreObservationService service = new PrivacyAuditDeadLetterWebhookReplayStoreObservationService(
                replayStore,
                Duration.ofMinutes(5)
        );

        PrivacyAuditDeadLetterWebhookReplayStoreSnapshot snapshot = service.currentSnapshot();
        assertThat(snapshot.count()).isEqualTo(2);
        assertThat(snapshot.expiringSoon()).isEqualTo(1);
        assertThat(snapshot.earliestExpiry()).isNotNull();
        assertThat(snapshot.latestExpiry()).isNotNull();
        assertThat(snapshot.expiringSoonWindow()).isEqualTo("PT5M");
    }
}
