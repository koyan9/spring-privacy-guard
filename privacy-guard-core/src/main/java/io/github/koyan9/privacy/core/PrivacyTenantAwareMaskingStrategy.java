/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

@StableSpi
public interface PrivacyTenantAwareMaskingStrategy extends MaskingStrategy {

    boolean supports(String tenantId, MaskingContext context);

    String mask(String tenantId, String value, MaskingContext context);

    @Override
    default boolean supports(MaskingContext context) {
        return supports(null, context);
    }

    @Override
    default String mask(String value, MaskingContext context) {
        return mask(null, value, context);
    }
}
