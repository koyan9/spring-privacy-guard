/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging;

import io.github.koyan9.privacy.core.StableSpi;

import java.util.Set;

@StableSpi
public record PrivacyTenantLoggingPolicy(
        Boolean mdcEnabled,
        Set<String> mdcIncludeKeys,
        Set<String> mdcExcludeKeys,
        Boolean structuredEnabled,
        Set<String> structuredIncludeKeys,
        Set<String> structuredExcludeKeys
) {

    public PrivacyTenantLoggingPolicy {
        mdcIncludeKeys = mdcIncludeKeys == null ? null : Set.copyOf(mdcIncludeKeys);
        mdcExcludeKeys = mdcExcludeKeys == null ? null : Set.copyOf(mdcExcludeKeys);
        structuredIncludeKeys = structuredIncludeKeys == null ? null : Set.copyOf(structuredIncludeKeys);
        structuredExcludeKeys = structuredExcludeKeys == null ? null : Set.copyOf(structuredExcludeKeys);
    }

    public boolean hasOverrides() {
        return mdcEnabled != null
                || mdcIncludeKeys != null
                || mdcExcludeKeys != null
                || structuredEnabled != null
                || structuredIncludeKeys != null
                || structuredExcludeKeys != null;
    }

    public static PrivacyTenantLoggingPolicy none() {
        return new PrivacyTenantLoggingPolicy(null, null, null, null, null, null);
    }
}
