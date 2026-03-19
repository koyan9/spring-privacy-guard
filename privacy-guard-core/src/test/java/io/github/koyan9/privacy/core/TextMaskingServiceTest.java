/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextMaskingServiceTest {

    private final TextMaskingService textMaskingService = new TextMaskingService(new MaskingService());

    @Test
    void sanitizesFreeTextLogPayloads() {
        String sanitized = textMaskingService.sanitize("patient phone=13800138000 email=alice@example.com id=110101199001011234");

        assertEquals("patient phone=138****8000 email=a****@example.com id=1101**********1234", sanitized);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void returnsOriginalForNullOrBlankText(String text) {
        assertEquals(text, textMaskingService.sanitize(text));
    }

    @Test
    void sanitizesMultipleMatchesInOneMessage() {
        String sanitized = textMaskingService.sanitize(
                "phones=13800138000,13900139000 emails=alice@example.com,bob@example.com"
        );

        assertEquals(
                "phones=138****8000,139****9000 emails=a****@example.com,b**@example.com",
                sanitized
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"11010119900101123X", "11010119900101123x"})
    void sanitizesIdCardsEndingWithLetterX(String idCard) {
        char suffix = idCard.charAt(idCard.length() - 1);

        assertEquals("1101**********123" + suffix, textMaskingService.sanitize(idCard));
    }

    @Test
    void leavesNonSensitiveTextUntouched() {
        String raw = "order=12345 ref=ABC-001 note=normal-text";

        assertEquals(raw, textMaskingService.sanitize(raw));
    }

    @Test
    void usesCustomStrategiesDuringPatternSanitization() {
        TextMaskingService customTextMaskingService = new TextMaskingService(
                new MaskingService(List.of(new EmailTokenStrategy()))
        );

        assertEquals("email=<EMAIL>", customTextMaskingService.sanitize("email=alice@example.com"));
    }

    @Test
    void supportsCustomRuleSets() {
        TextMaskingService customTextMaskingService = new TextMaskingService(
                new MaskingService(),
                List.of(new TextMaskingRule(SensitiveType.GENERIC, Pattern.compile("EMP\\d{4}")))
        );

        String sanitized = customTextMaskingService.sanitize("id=EMP1234 phone=13800138000");

        assertTrue(sanitized.contains("id=E"));
        assertTrue(sanitized.contains("phone=13800138000"));
        assertTrue(!sanitized.contains("EMP1234"));
    }

    @Test
    void usesTenantSpecificRuleSetsWhenProvided() {
        TextMaskingService tenantAwareService = new TextMaskingService(
                new MaskingService(
                        "*",
                        List.of(),
                        () -> "tenant-a",
                        tenantId -> new PrivacyTenantPolicy(null, List.of(new TextMaskingRule(SensitiveType.GENERIC, Pattern.compile("EMP\\d{4}"))))
                ),
                TextMaskingRule.defaults(),
                () -> "tenant-a",
                tenantId -> new PrivacyTenantPolicy(null, List.of(new TextMaskingRule(SensitiveType.GENERIC, Pattern.compile("EMP\\d{4}"))))
        );

        String sanitized = tenantAwareService.sanitize("id=EMP1234 phone=13800138000");

        assertTrue(sanitized.contains("id=E"));
        assertTrue(sanitized.contains("phone=13800138000"));
        assertTrue(!sanitized.contains("EMP1234"));
    }

    static class EmailTokenStrategy implements MaskingStrategy {

        @Override
        public boolean supports(MaskingContext context) {
            return context.sensitiveType() == SensitiveType.EMAIL;
        }

        @Override
        public String mask(String value, MaskingContext context) {
            return "<EMAIL>";
        }
    }
}
