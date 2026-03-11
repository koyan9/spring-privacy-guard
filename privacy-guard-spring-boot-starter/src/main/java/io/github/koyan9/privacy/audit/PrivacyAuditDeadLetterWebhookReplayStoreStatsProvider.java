/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.time.Duration;
import java.time.Instant;

public interface PrivacyAuditDeadLetterWebhookReplayStoreStatsProvider {

    PrivacyAuditDeadLetterWebhookReplayStoreSnapshot snapshotStats(Instant now, Duration expiringSoonWindow);
}
