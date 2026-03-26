/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

class PrivacyGuardDeadLetterWebhookReceiverMvcConfigurer implements WebMvcConfigurer {

    private final PrivacyAuditDeadLetterWebhookVerificationInterceptor interceptor;
    private final List<String> pathPatterns;

    PrivacyGuardDeadLetterWebhookReceiverMvcConfigurer(
            PrivacyAuditDeadLetterWebhookVerificationInterceptor interceptor,
            String pathPattern
    ) {
        this(interceptor, List.of(pathPattern));
    }

    PrivacyGuardDeadLetterWebhookReceiverMvcConfigurer(
            PrivacyAuditDeadLetterWebhookVerificationInterceptor interceptor,
            List<String> pathPatterns
    ) {
        this.interceptor = interceptor;
        this.pathPatterns = pathPatterns == null ? List.of() : List.copyOf(pathPatterns);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns(pathPatterns);
    }
}
