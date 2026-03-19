/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.time.Instant;

@StableSpi
public record PrivacyAuditQueryCriteria(
        String action,
        String actionLike,
        String resourceType,
        String resourceTypeLike,
        String resourceId,
        String resourceIdLike,
        String actor,
        String actorLike,
        String outcome,
        String outcomeLike,
        Instant occurredFrom,
        Instant occurredTo,
        PrivacyAuditSortDirection sortDirection,
        int limit,
        int offset
) {

    public PrivacyAuditQueryCriteria normalize() {
        return new PrivacyAuditQueryCriteria(
                normalizeText(action),
                normalizeText(actionLike),
                normalizeText(resourceType),
                normalizeText(resourceTypeLike),
                normalizeText(resourceId),
                normalizeText(resourceIdLike),
                normalizeText(actor),
                normalizeText(actorLike),
                normalizeText(outcome),
                normalizeText(outcomeLike),
                occurredFrom,
                occurredTo,
                sortDirection == null ? PrivacyAuditSortDirection.DESC : sortDirection,
                limit <= 0 ? 100 : limit,
                Math.max(0, offset)
        );
    }

    public static PrivacyAuditQueryCriteria recent(int limit) {
        return new PrivacyAuditQueryCriteria(null, null, null, null, null, null, null, null, null, null, null, null, PrivacyAuditSortDirection.DESC, limit, 0).normalize();
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
