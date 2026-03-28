/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.util.List;

@StableSpi
public interface PrivacyTenantAuditWriteRepository {

    void save(PrivacyTenantAuditWriteRequest request);

    default void saveAllTenantAware(List<PrivacyTenantAuditWriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (PrivacyTenantAuditWriteRequest request : requests) {
            save(request);
        }
    }

    default boolean supportsTenantWrite() {
        return false;
    }
}
