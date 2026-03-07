/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.springframework.context.ApplicationEventPublisher;

public class ApplicationEventPrivacyAuditPublisher implements PrivacyAuditPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public ApplicationEventPrivacyAuditPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(PrivacyAuditEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}