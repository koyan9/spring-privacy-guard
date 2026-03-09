/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.util.List;

public record PrivacyAuditDeadLetterReplayResult(
        int requested,
        int replayed,
        int failed,
        List<Long> replayedIds,
        List<Long> failedIds
) {
}
