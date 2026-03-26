/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging.logback;

import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.TextMaskingService;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyLogbackConfigurerTest {

    @AfterEach
    void clearRuntime() {
        PrivacyLogbackRuntime.clear();
    }

    @Test
    void appliesSupplierBackedSettingsAndClearsRuntimeOnDestroy() {
        PrivacyLogbackConfigurer configurer = new PrivacyLogbackConfigurer(
                new PrivacyLogSanitizer(new TextMaskingService(new MaskingService())),
                false,
                true,
                () -> new PrivacyLogbackSanitizerSettings(
                        true,
                        Set.of("email"),
                        Set.of(),
                        false,
                        Set.of(),
                        Set.of()
                )
        );

        Map<String, String> sanitized = PrivacyLogbackRuntime.sanitizeMdc(Map.of(
                "email", "alice@example.com",
                "phone", "13800138000"
        ));
        assertThat(sanitized.get("email")).contains("a****@example.com");
        assertThat(sanitized.get("phone")).isEqualTo("13800138000");

        configurer.destroy();

        Map<String, String> afterDestroy = PrivacyLogbackRuntime.sanitizeMdc(Map.of(
                "email", "alice@example.com"
        ));
        assertThat(afterDestroy.get("email")).isEqualTo("alice@example.com");
    }
}
