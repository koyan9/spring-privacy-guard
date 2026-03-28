/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

@FunctionalInterface
@StableSpi
public interface PrivacyTenantDeadLetterAlertRoutePolicyResolver {

    PrivacyTenantDeadLetterAlertRoutePolicy resolve(String tenantId);

    static PrivacyTenantDeadLetterAlertRoutePolicyResolver noop() {
        return tenantId -> PrivacyTenantDeadLetterAlertRoutePolicy.none();
    }
}
