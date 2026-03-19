/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

@StableSpi
public record MaskingContext(
        SensitiveType sensitiveType,
        String maskChar,
        int leftVisible,
        int rightVisible
) {

    public boolean hasExplicitVisibility() {
        return leftVisible >= 0 || rightVisible >= 0;
    }
}
