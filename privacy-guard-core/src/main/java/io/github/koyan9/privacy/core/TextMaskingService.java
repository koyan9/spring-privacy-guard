/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextMaskingService {

    private final MaskingService maskingService;
    private final List<TextMaskingRule> rules;
    private final PrivacyTenantProvider tenantProvider;
    private final PrivacyTenantPolicyResolver tenantPolicyResolver;

    public TextMaskingService(MaskingService maskingService) {
        this(maskingService, null, PrivacyTenantProvider.noop(), PrivacyTenantPolicyResolver.noop());
    }

    public TextMaskingService(MaskingService maskingService, List<TextMaskingRule> rules) {
        this(maskingService, rules, PrivacyTenantProvider.noop(), PrivacyTenantPolicyResolver.noop());
    }

    public TextMaskingService(
            MaskingService maskingService,
            List<TextMaskingRule> rules,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantPolicyResolver tenantPolicyResolver
    ) {
        this.maskingService = maskingService;
        if (rules == null || rules.isEmpty()) {
            this.rules = TextMaskingRule.defaults();
        } else {
            this.rules = List.copyOf(rules);
        }
        this.tenantProvider = tenantProvider == null ? PrivacyTenantProvider.noop() : tenantProvider;
        this.tenantPolicyResolver = tenantPolicyResolver == null ? PrivacyTenantPolicyResolver.noop() : tenantPolicyResolver;
    }

    public String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String sanitized = text;
        for (TextMaskingRule rule : rulesForCurrentTenant()) {
            sanitized = replaceMatches(sanitized, rule.pattern(), rule.sensitiveType());
        }
        return sanitized;
    }

    private String replaceMatches(String text, Pattern pattern, SensitiveType sensitiveType) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String masked = maskingService.mask(matcher.group(), sensitiveType);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(masked));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private List<TextMaskingRule> rulesForCurrentTenant() {
        String tenantId = tenantProvider.currentTenantId();
        PrivacyTenantPolicy policy = tenantPolicyResolver.resolve(
                tenantId == null || tenantId.isBlank() ? null : tenantId.trim()
        );
        if (policy != null && policy.hasTextMaskingRules()) {
            return policy.textMaskingRules();
        }
        return rules;
    }
}
