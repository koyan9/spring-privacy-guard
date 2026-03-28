/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.util.List;
import java.util.Optional;

@StableSpi
public interface PrivacyTenantAuditDeadLetterReadRepository {

    List<PrivacyAuditDeadLetterEntry> findByCriteria(
            String tenantId,
            String tenantDetailKey,
            PrivacyAuditDeadLetterQueryCriteria criteria
    );

    PrivacyAuditDeadLetterStats computeStats(
            String tenantId,
            String tenantDetailKey,
            PrivacyAuditDeadLetterQueryCriteria criteria
    );

    default boolean supportsTenantRead() {
        return false;
    }

    default Optional<PrivacyAuditDeadLetterEntry> findById(
            String tenantId,
            String tenantDetailKey,
            long id
    ) {
        return Optional.empty();
    }

    default boolean supportsTenantFindById() {
        return false;
    }

    default boolean supportsTenantExchangeRead() {
        return false;
    }
}
