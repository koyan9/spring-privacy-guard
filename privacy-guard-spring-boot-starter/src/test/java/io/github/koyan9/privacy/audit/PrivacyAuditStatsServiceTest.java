/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrivacyAuditStatsServiceTest {

    @Test
    void delegatesToRepository() {
        PrivacyAuditStatsRepository repository = criteria -> new PrivacyAuditQueryStats(2, Map.of("READ", 2L), Map.of("OK", 2L), Map.of("Patient", 2L));
        PrivacyAuditStatsService service = new PrivacyAuditStatsService(repository);

        PrivacyAuditQueryStats stats = service.computeStats(PrivacyAuditQueryCriteria.recent(10));

        assertEquals(2, stats.total());
        assertEquals(2L, stats.byAction().get("READ"));
    }
}