/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

@StableSpi
public interface MaskingStrategy {

    boolean supports(MaskingContext context);

    String mask(String value, MaskingContext context);
}
