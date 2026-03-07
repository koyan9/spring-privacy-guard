/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

public class RepositoryPrivacyAuditPublisher implements PrivacyAuditPublisher {

    private final PrivacyAuditRepository privacyAuditRepository;

    public RepositoryPrivacyAuditPublisher(PrivacyAuditRepository privacyAuditRepository) {
        this.privacyAuditRepository = privacyAuditRepository;
    }

    @Override
    public void publish(PrivacyAuditEvent event) {
        privacyAuditRepository.save(event);
    }
}