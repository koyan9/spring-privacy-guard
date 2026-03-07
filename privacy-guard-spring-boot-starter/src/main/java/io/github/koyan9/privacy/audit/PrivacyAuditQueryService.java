/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.util.List;

public class PrivacyAuditQueryService {

    private final PrivacyAuditQueryRepository privacyAuditQueryRepository;

    public PrivacyAuditQueryService(PrivacyAuditQueryRepository privacyAuditQueryRepository) {
        this.privacyAuditQueryRepository = privacyAuditQueryRepository;
    }

    public List<PrivacyAuditEvent> findRecent(int limit) {
        return privacyAuditQueryRepository.findByCriteria(PrivacyAuditQueryCriteria.recent(limit));
    }

    public List<PrivacyAuditEvent> findByCriteria(PrivacyAuditQueryCriteria criteria) {
        PrivacyAuditQueryCriteria normalized = criteria == null
                ? PrivacyAuditQueryCriteria.recent(100)
                : criteria.normalize();
        return privacyAuditQueryRepository.findByCriteria(normalized);
    }
}