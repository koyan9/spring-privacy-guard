/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

@FunctionalInterface
@StableSpi
public interface PrivacyTenantProvider {

    String currentTenantId();

    static PrivacyTenantProvider noop() {
        return () -> null;
    }
}
