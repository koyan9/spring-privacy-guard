/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging.logback;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class PrivacyLogbackSanitizerSettings {

    private final boolean mdcEnabled;
    private final Set<String> mdcIncludeKeys;
    private final Set<String> mdcExcludeKeys;
    private final boolean structuredEnabled;
    private final Set<String> structuredIncludeKeys;
    private final Set<String> structuredExcludeKeys;

    public PrivacyLogbackSanitizerSettings(
            boolean mdcEnabled,
            Set<String> mdcIncludeKeys,
            Set<String> mdcExcludeKeys,
            boolean structuredEnabled,
            Set<String> structuredIncludeKeys,
            Set<String> structuredExcludeKeys
    ) {
        this.mdcEnabled = mdcEnabled;
        this.mdcIncludeKeys = normalizeSet(mdcIncludeKeys);
        this.mdcExcludeKeys = normalizeSet(mdcExcludeKeys);
        this.structuredEnabled = structuredEnabled;
        this.structuredIncludeKeys = normalizeSet(structuredIncludeKeys);
        this.structuredExcludeKeys = normalizeSet(structuredExcludeKeys);
    }

    public static PrivacyLogbackSanitizerSettings disabled() {
        return new PrivacyLogbackSanitizerSettings(false, Set.of(), Set.of(), false, Set.of(), Set.of());
    }

    public boolean isMdcEnabled() {
        return mdcEnabled;
    }

    public Set<String> getMdcIncludeKeys() {
        return mdcIncludeKeys;
    }

    public Set<String> getMdcExcludeKeys() {
        return mdcExcludeKeys;
    }

    public boolean isStructuredEnabled() {
        return structuredEnabled;
    }

    public Set<String> getStructuredIncludeKeys() {
        return structuredIncludeKeys;
    }

    public Set<String> getStructuredExcludeKeys() {
        return structuredExcludeKeys;
    }

    private static Set<String> normalizeSet(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : source) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return Set.copyOf(normalized);
    }
}
