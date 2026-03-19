/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaskingServiceTest {

    private final MaskingService maskingService = new MaskingService();

    @Test
    void masksBuiltInSensitiveTypes() {
        assertEquals("138****8000", maskingService.mask("13800138000", SensitiveType.PHONE));
        assertEquals("a****@example.com", maskingService.mask("alice@example.com", SensitiveType.EMAIL));
        assertEquals("A***e", maskingService.mask("Alice", SensitiveType.NAME));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void returnsOriginalForNullOrBlankValues(String value) {
        assertEquals(value, maskingService.mask(value, SensitiveType.PHONE));
    }

    @Test
    void usesConfiguredFallbackMaskCharacterForTypeMasking() {
        MaskingService configuredService = new MaskingService("#");

        assertEquals("138####8000", configuredService.mask("13800138000", SensitiveType.PHONE));
    }

    @Test
    void appliesTenantFallbackMaskCharacterWhenTenantPolicyExists() {
        MaskingService configuredService = new MaskingService(
                "*",
                List.of(),
                () -> "tenant-a",
                tenantId -> new PrivacyTenantPolicy("#", null)
        );

        assertEquals("138####8000", configuredService.mask("13800138000", SensitiveType.PHONE));
    }

    @Test
    void supportsMultiCharacterMaskTokens() {
        MaskingService configuredService = new MaskingService("[x]");

        assertEquals("a[x][x]d", configuredService.mask("abcd", SensitiveType.GENERIC));
    }

    @Test
    void masksShortNamesSafely() {
        assertEquals("*", maskingService.mask("A", SensitiveType.NAME));
        assertEquals("A*", maskingService.mask("Al", SensitiveType.NAME));
    }

    @Test
    void usesCustomStrategiesBeforeBuiltInTypeRules() {
        MaskingService configuredService = new MaskingService("*", List.of(new NameWrappingStrategy()));

        assertEquals("[custom]Alice", configuredService.mask("Alice", SensitiveType.NAME));
    }

    @Test
    void keepsExplicitVisibilityAheadOfCustomStrategies() throws Exception {
        MaskingService configuredService = new MaskingService("*", List.of(new AlwaysCustomStrategy()));
        Field field = DemoRecord.class.getDeclaredField("overVisibleValue");
        SensitiveData sensitiveData = field.getAnnotation(SensitiveData.class);

        assertEquals("****", configuredService.mask("abcd", sensitiveData));
    }

    @Test
    void respectsAnnotationOverrides() throws Exception {
        Field field = DemoRecord.class.getDeclaredField("customMaskedValue");
        SensitiveData sensitiveData = field.getAnnotation(SensitiveData.class);

        assertEquals("ab###f", maskingService.mask("abcdef", sensitiveData));
    }

    @Test
    void fallsBackToDefaultMaskCharacterWhenAnnotationMaskCharIsBlank() throws Exception {
        Field field = DemoRecord.class.getDeclaredField("blankMaskCharValue");
        SensitiveData sensitiveData = field.getAnnotation(SensitiveData.class);

        assertEquals("a****f", maskingService.mask("abcdef", sensitiveData));
    }

    @Test
    void masksEntireValueWhenVisibleRangeExceedsLength() throws Exception {
        Field field = DemoRecord.class.getDeclaredField("overVisibleValue");
        SensitiveData sensitiveData = field.getAnnotation(SensitiveData.class);

        assertEquals("****", maskingService.mask("abcd", sensitiveData));
    }

    @Test
    void appliesTenantFallbackMaskCharacterToDefaultAnnotationMask() throws Exception {
        MaskingService configuredService = new MaskingService(
                "*",
                List.of(),
                () -> "tenant-a",
                tenantId -> new PrivacyTenantPolicy("#", null)
        );
        Field field = DemoRecord.class.getDeclaredField("defaultMaskedValue");
        SensitiveData sensitiveData = field.getAnnotation(SensitiveData.class);

        assertEquals("A###e", configuredService.mask("Alice", sensitiveData));
    }

    @Test
    void supportsTenantAwareMaskingStrategies() {
        MaskingService configuredService = new MaskingService(
                "*",
                List.of(new TenantPrefixStrategy()),
                () -> "tenant-a",
                PrivacyTenantPolicyResolver.noop()
        );

        assertEquals("[tenant-a]Alice", configuredService.mask("Alice", SensitiveType.NAME));
    }

    static class DemoRecord {

        @SensitiveData(type = SensitiveType.GENERIC, maskChar = "#", leftVisible = 2, rightVisible = 1)
        private String customMaskedValue;

        @SensitiveData(type = SensitiveType.GENERIC, maskChar = " ", leftVisible = 1, rightVisible = 1)
        private String blankMaskCharValue;

        @SensitiveData(type = SensitiveType.GENERIC, leftVisible = 3, rightVisible = 3)
        private String overVisibleValue;

        @SensitiveData(type = SensitiveType.NAME)
        private String defaultMaskedValue;
    }

    static class NameWrappingStrategy implements MaskingStrategy {

        @Override
        public boolean supports(MaskingContext context) {
            return context.sensitiveType() == SensitiveType.NAME;
        }

        @Override
        public String mask(String value, MaskingContext context) {
            return "[custom]" + value;
        }
    }

    static class AlwaysCustomStrategy implements MaskingStrategy {

        @Override
        public boolean supports(MaskingContext context) {
            return true;
        }

        @Override
        public String mask(String value, MaskingContext context) {
            return "CUSTOM";
        }
    }

    static class TenantPrefixStrategy implements PrivacyTenantAwareMaskingStrategy {

        @Override
        public boolean supports(String tenantId, MaskingContext context) {
            return context.sensitiveType() == SensitiveType.NAME && tenantId != null;
        }

        @Override
        public String mask(String tenantId, String value, MaskingContext context) {
            return "[" + tenantId + "]" + value;
        }
    }
}
