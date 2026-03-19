/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import java.util.List;

@StableSpi
public record PrivacyTenantPolicy(
        String fallbackMaskChar,
        List<TextMaskingRule> textMaskingRules
) {

    public PrivacyTenantPolicy {
        textMaskingRules = textMaskingRules == null ? null : List.copyOf(textMaskingRules);
    }

    public boolean hasFallbackMaskChar() {
        return fallbackMaskChar != null && !fallbackMaskChar.isBlank();
    }

    public boolean hasTextMaskingRules() {
        return textMaskingRules != null;
    }

    public static PrivacyTenantPolicy none() {
        return new PrivacyTenantPolicy(null, null);
    }
}
