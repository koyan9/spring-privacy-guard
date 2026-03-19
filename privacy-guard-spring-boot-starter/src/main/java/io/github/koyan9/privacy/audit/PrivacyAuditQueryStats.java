/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.util.Map;

@StableSpi
public record PrivacyAuditQueryStats(
        long total,
        Map<String, Long> byAction,
        Map<String, Long> byOutcome,
        Map<String, Long> byResourceType
) {

    public PrivacyAuditQueryStats {
        byAction = byAction == null ? Map.of() : Map.copyOf(byAction);
        byOutcome = byOutcome == null ? Map.of() : Map.copyOf(byOutcome);
        byResourceType = byResourceType == null ? Map.of() : Map.copyOf(byResourceType);
    }
}
