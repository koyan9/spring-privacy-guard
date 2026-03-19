/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

@StableSpi
public final class PrivacyTenantContextScope implements AutoCloseable {

    private final String previousTenantId;
    private boolean closed;

    PrivacyTenantContextScope(String previousTenantId) {
        this.previousTenantId = previousTenantId;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        PrivacyTenantContextHolder.restore(previousTenantId);
    }
}
