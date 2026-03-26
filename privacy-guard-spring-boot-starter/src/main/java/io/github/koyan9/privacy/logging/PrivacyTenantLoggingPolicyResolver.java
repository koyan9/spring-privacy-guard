/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging;

import io.github.koyan9.privacy.core.StableSpi;

@StableSpi
public interface PrivacyTenantLoggingPolicyResolver {

    PrivacyTenantLoggingPolicy resolve(String tenantId);

    static PrivacyTenantLoggingPolicyResolver noop() {
        return tenantId -> PrivacyTenantLoggingPolicy.none();
    }
}
