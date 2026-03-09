/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrivacyAuditDeadLetterStatsServiceTest {

    @Test
    void computesStatsThroughRepository() {
        PrivacyAuditDeadLetterStatsRepository repository = criteria -> new PrivacyAuditDeadLetterStats(
                2,
                Map.of("READ", 2L),
                Map.of("OK", 1L),
                Map.of("Patient", 2L),
                Map.of("java.lang.IllegalStateException", 2L)
        );
        PrivacyAuditDeadLetterStatsService service = new PrivacyAuditDeadLetterStatsService(repository);

        PrivacyAuditDeadLetterStats stats = service.computeStats(PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertEquals(2, stats.total());
        assertEquals(2L, stats.byAction().get("READ"));
        assertEquals(2L, stats.byErrorType().get("java.lang.IllegalStateException"));
    }
}
