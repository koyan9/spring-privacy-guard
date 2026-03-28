/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

@FunctionalInterface
@StableSpi
public interface PrivacyTenantDeadLetterAlertDeliveryPolicyResolver {

    PrivacyTenantDeadLetterAlertDeliveryPolicy resolve(String tenantId);

    static PrivacyTenantDeadLetterAlertDeliveryPolicyResolver noop() {
        return tenantId -> PrivacyTenantDeadLetterAlertDeliveryPolicy.none();
    }
}
