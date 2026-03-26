/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging.logback;

import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.PrivacyTenantContextHolder;
import io.github.koyan9.privacy.core.PrivacyTenantContextScope;
import io.github.koyan9.privacy.core.TextMaskingService;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyLogbackRuntimeTest {

    @AfterEach
    void clearRuntime() {
        PrivacyLogbackRuntime.clear();
        PrivacyTenantContextHolder.clear();
    }

    @Test
    void respectsIncludeAndExcludeKeys() {
        PrivacyLogbackRuntime.set(
                new PrivacyLogSanitizer(new TextMaskingService(new MaskingService())),
                new PrivacyLogbackSanitizerSettings(
                        true,
                        Set.of("email"),
                        Set.of("skip"),
                        true,
                        Set.of("phone"),
                        Set.of("skip")
                )
        );

        Map<String, String> sanitizedMdc = PrivacyLogbackRuntime.sanitizeMdc(Map.of(
                "email", "alice@example.com",
                "phone", "13800138000",
                "skip", "alice@example.com"
        ));
        assertThat(sanitizedMdc.get("email")).contains("a****@example.com");
        assertThat(sanitizedMdc.get("phone")).isEqualTo("13800138000");
        assertThat(sanitizedMdc.get("skip")).isEqualTo("alice@example.com");

        List<KeyValuePair> sanitizedPairs = PrivacyLogbackRuntime.sanitizeKeyValuePairs(List.of(
                new KeyValuePair("phone", "13800138000"),
                new KeyValuePair("skip", "alice@example.com")
        ));
        assertThat(sanitizedPairs.get(0).value.toString()).contains("138****8000");
        assertThat(sanitizedPairs.get(1).value.toString()).isEqualTo("alice@example.com");
    }

    @Test
    void resolvesSettingsDynamicallyPerTenant() {
        PrivacyLogbackRuntime.set(
                new PrivacyLogSanitizer(new TextMaskingService(new MaskingService())),
                () -> {
                    String tenantId = PrivacyTenantContextHolder.getTenantId();
                    if ("tenant-a".equals(tenantId)) {
                        return new PrivacyLogbackSanitizerSettings(
                                true,
                                Set.of("phone"),
                                Set.of(),
                                false,
                                Set.of(),
                                Set.of()
                        );
                    }
                    return new PrivacyLogbackSanitizerSettings(
                            true,
                            Set.of("email"),
                            Set.of(),
                            true,
                            Set.of("phone"),
                            Set.of()
                    );
                }
        );

        try (PrivacyTenantContextScope ignored = PrivacyTenantContextHolder.openScope("tenant-a")) {
            Map<String, String> tenantMdc = PrivacyLogbackRuntime.sanitizeMdc(Map.of(
                    "email", "alice@example.com",
                    "phone", "13800138000"
            ));
            assertThat(tenantMdc.get("email")).isEqualTo("alice@example.com");
            assertThat(tenantMdc.get("phone")).contains("138****8000");

            List<KeyValuePair> tenantPairs = PrivacyLogbackRuntime.sanitizeKeyValuePairs(List.of(
                    new KeyValuePair("phone", "13800138000")
            ));
            assertThat(tenantPairs.get(0).value.toString()).isEqualTo("13800138000");
        }

        Map<String, String> defaultMdc = PrivacyLogbackRuntime.sanitizeMdc(Map.of(
                "email", "alice@example.com",
                "phone", "13800138000"
        ));
        assertThat(defaultMdc.get("email")).contains("a****@example.com");
        assertThat(defaultMdc.get("phone")).isEqualTo("13800138000");
    }
}
