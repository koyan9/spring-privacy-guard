/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrivacyAuditQueryServiceTest {

    @Test
    void returnsRecentEventsFromRepository() {
        PrivacyAuditQueryRepository repository = criteria -> List.of(
                new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "1", "actor", "OK", Map.of())
        );
        PrivacyAuditQueryService service = new PrivacyAuditQueryService(repository);

        assertEquals(1, service.findRecent(10).size());
    }

    @Test
    void normalizesCriteria() {
        final PrivacyAuditQueryCriteria[] observed = new PrivacyAuditQueryCriteria[1];
        PrivacyAuditQueryRepository repository = criteria -> {
            observed[0] = criteria;
            return List.of();
        };
        PrivacyAuditQueryService service = new PrivacyAuditQueryService(repository);

        service.findByCriteria(new PrivacyAuditQueryCriteria(
                "  READ ", null,
                "", null,
                null, null,
                null, " actor ",
                null, null,
                Instant.parse("2026-03-06T00:00:00Z"),
                null,
                null,
                0,
                -1
        ));

        assertEquals("READ", observed[0].action());
        assertEquals(null, observed[0].resourceType());
        assertEquals("actor", observed[0].actorLike());
        assertEquals(PrivacyAuditSortDirection.DESC, observed[0].sortDirection());
        assertEquals(100, observed[0].limit());
        assertEquals(0, observed[0].offset());
    }
}