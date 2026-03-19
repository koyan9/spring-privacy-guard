/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

@FunctionalInterface
@StableSpi
public interface PrivacyTenantAuditPolicyResolver {

    PrivacyTenantAuditPolicy resolve(String tenantId);

    static PrivacyTenantAuditPolicyResolver noop() {
        return tenantId -> PrivacyTenantAuditPolicy.none();
    }
}
