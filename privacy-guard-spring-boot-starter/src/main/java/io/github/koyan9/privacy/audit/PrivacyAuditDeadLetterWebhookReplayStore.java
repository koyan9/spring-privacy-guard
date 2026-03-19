/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.time.Instant;
import java.util.Map;

@StableSpi
public interface PrivacyAuditDeadLetterWebhookReplayStore {

    boolean markIfNew(String nonce, Instant now, java.time.Duration ttl);

    Map<String, Instant> snapshot();

    void clear();
}
