/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

public class PrivacyAuditDeadLetterStatsService {

    private final PrivacyAuditDeadLetterStatsRepository repository;

    public PrivacyAuditDeadLetterStatsService(PrivacyAuditDeadLetterStatsRepository repository) {
        this.repository = repository;
    }

    public PrivacyAuditDeadLetterStats computeStats(PrivacyAuditDeadLetterQueryCriteria criteria) {
        PrivacyAuditDeadLetterQueryCriteria normalized = criteria == null
                ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                : criteria.normalize();
        return repository.computeStats(normalized);
    }
}
