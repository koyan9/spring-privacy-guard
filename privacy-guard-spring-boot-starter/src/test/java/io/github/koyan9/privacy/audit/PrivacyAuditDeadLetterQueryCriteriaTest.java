/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PrivacyAuditDeadLetterQueryCriteriaTest {

    @Test
    void normalizesCriteria() {
        PrivacyAuditDeadLetterQueryCriteria criteria = new PrivacyAuditDeadLetterQueryCriteria(
                "  READ ", null,
                "", null,
                null, " demo ",
                null, " actor ",
                null, null,
                " java.lang.IllegalStateException ", " failure ",
                Instant.parse("2026-03-06T00:00:00Z"),
                null,
                null,
                null,
                null,
                0,
                -1
        ).normalize();

        assertEquals("READ", criteria.action());
        assertNull(criteria.resourceType());
        assertEquals("demo", criteria.resourceIdLike());
        assertEquals("actor", criteria.actorLike());
        assertEquals("java.lang.IllegalStateException", criteria.errorType());
        assertEquals("failure", criteria.errorMessageLike());
        assertEquals(PrivacyAuditSortDirection.DESC, criteria.sortDirection());
        assertEquals(100, criteria.limit());
        assertEquals(0, criteria.offset());
    }

    @Test
    void createsRecentCriteria() {
        PrivacyAuditDeadLetterQueryCriteria criteria = PrivacyAuditDeadLetterQueryCriteria.recent(10);

        assertEquals(PrivacyAuditSortDirection.DESC, criteria.sortDirection());
        assertEquals(10, criteria.limit());
        assertEquals(0, criteria.offset());
    }
}
