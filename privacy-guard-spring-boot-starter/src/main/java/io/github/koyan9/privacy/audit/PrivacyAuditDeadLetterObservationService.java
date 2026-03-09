/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

public class PrivacyAuditDeadLetterObservationService {

    private final PrivacyAuditDeadLetterStatsService statsService;
    private final long warningThreshold;
    private final long downThreshold;

    public PrivacyAuditDeadLetterObservationService(
            PrivacyAuditDeadLetterStatsService statsService,
            long warningThreshold,
            long downThreshold
    ) {
        this.statsService = statsService;
        this.warningThreshold = Math.max(0, warningThreshold);
        this.downThreshold = Math.max(this.warningThreshold, downThreshold);
    }

    public PrivacyAuditDeadLetterBacklogSnapshot currentSnapshot() {
        PrivacyAuditDeadLetterStats stats = statsService.computeStats(PrivacyAuditDeadLetterQueryCriteria.recent(1));
        long total = stats.total();
        return new PrivacyAuditDeadLetterBacklogSnapshot(
                total,
                warningThreshold,
                downThreshold,
                determineState(total),
                stats.byAction(),
                stats.byOutcome(),
                stats.byResourceType(),
                stats.byErrorType()
        );
    }

    private PrivacyAuditDeadLetterBacklogState determineState(long total) {
        if (total >= downThreshold) {
            return PrivacyAuditDeadLetterBacklogState.DOWN;
        }
        if (total >= warningThreshold) {
            return PrivacyAuditDeadLetterBacklogState.WARNING;
        }
        return PrivacyAuditDeadLetterBacklogState.CLEAR;
    }
}
