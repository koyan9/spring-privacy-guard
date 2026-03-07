/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

public class PrivacyAuditStatsService {

    private final PrivacyAuditStatsRepository privacyAuditStatsRepository;

    public PrivacyAuditStatsService(PrivacyAuditStatsRepository privacyAuditStatsRepository) {
        this.privacyAuditStatsRepository = privacyAuditStatsRepository;
    }

    public PrivacyAuditQueryStats computeStats(PrivacyAuditQueryCriteria criteria) {
        PrivacyAuditQueryCriteria normalized = criteria == null
                ? PrivacyAuditQueryCriteria.recent(100)
                : criteria.normalize();
        return privacyAuditStatsRepository.computeStats(normalized);
    }
}