/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.time.Instant;
import java.util.Map;

public interface PrivacyAuditDeadLetterWebhookReplayStore {

    boolean markIfNew(String nonce, Instant now, java.time.Duration ttl);

    Map<String, Instant> snapshot();

    void clear();
}
