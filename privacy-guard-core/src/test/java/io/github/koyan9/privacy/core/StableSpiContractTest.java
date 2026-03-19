/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class StableSpiContractTest {

    @Test
    void marksCoreStableSpiTypes() {
        assertStable(MaskingStrategy.class);
        assertStable(MaskingContext.class);
        assertStable(TextMaskingRule.class);
        assertStable(PrivacyTenantProvider.class);
        assertStable(PrivacyTenantContextScope.class);
        assertStable(PrivacyTenantContextSnapshot.class);
        assertStable(PrivacyTenantPolicy.class);
        assertStable(PrivacyTenantPolicyResolver.class);
        assertStable(PrivacyTenantAwareMaskingStrategy.class);
    }

    private static void assertStable(Class<?> type) {
        assertNotNull(type.getAnnotation(StableSpi.class), () -> type.getName() + " should declare @StableSpi");
    }
}
