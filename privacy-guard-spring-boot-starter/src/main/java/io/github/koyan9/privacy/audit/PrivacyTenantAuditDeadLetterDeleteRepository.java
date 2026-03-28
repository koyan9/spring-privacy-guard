/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

@StableSpi
public interface PrivacyTenantAuditDeadLetterDeleteRepository {

    int deleteByCriteria(
            String tenantId,
            String tenantDetailKey,
            PrivacyAuditDeadLetterQueryCriteria criteria
    );

    default boolean deleteById(
            String tenantId,
            String tenantDetailKey,
            long id
    ) {
        return false;
    }

    default boolean supportsTenantDelete() {
        return false;
    }

    default boolean supportsTenantDeleteById() {
        return false;
    }
}
