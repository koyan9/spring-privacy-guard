/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.util.List;

@StableSpi
public interface PrivacyTenantAuditDeadLetterWriteRepository {

    void save(PrivacyTenantAuditDeadLetterWriteRequest request);

    default void saveAllTenantAware(List<PrivacyTenantAuditDeadLetterWriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (PrivacyTenantAuditDeadLetterWriteRequest request : requests) {
            save(request);
        }
    }

    default boolean supportsTenantWrite() {
        return false;
    }

    default boolean supportsTenantImport() {
        return false;
    }
}
