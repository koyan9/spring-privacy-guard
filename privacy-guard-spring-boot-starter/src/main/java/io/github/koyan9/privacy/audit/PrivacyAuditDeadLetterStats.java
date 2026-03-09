/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.util.Map;

public record PrivacyAuditDeadLetterStats(
        long total,
        Map<String, Long> byAction,
        Map<String, Long> byOutcome,
        Map<String, Long> byResourceType,
        Map<String, Long> byErrorType
) {

    public PrivacyAuditDeadLetterStats {
        byAction = byAction == null ? Map.of() : Map.copyOf(byAction);
        byOutcome = byOutcome == null ? Map.of() : Map.copyOf(byOutcome);
        byResourceType = byResourceType == null ? Map.of() : Map.copyOf(byResourceType);
        byErrorType = byErrorType == null ? Map.of() : Map.copyOf(byErrorType);
    }
}
