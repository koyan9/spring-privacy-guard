/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class PrivacyGuardDeadLetterWebhookReceiverMvcConfigurer implements WebMvcConfigurer {

    private final PrivacyAuditDeadLetterWebhookVerificationInterceptor interceptor;
    private final String pathPattern;

    PrivacyGuardDeadLetterWebhookReceiverMvcConfigurer(
            PrivacyAuditDeadLetterWebhookVerificationInterceptor interceptor,
            String pathPattern
    ) {
        this.interceptor = interceptor;
        this.pathPattern = pathPattern;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns(pathPattern);
    }
}
