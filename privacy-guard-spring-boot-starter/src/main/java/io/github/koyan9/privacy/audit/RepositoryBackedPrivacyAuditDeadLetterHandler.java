/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.util.Objects;

public class RepositoryBackedPrivacyAuditDeadLetterHandler implements PrivacyAuditDeadLetterHandler {

    private final PrivacyAuditDeadLetterRepository repository;

    public RepositoryBackedPrivacyAuditDeadLetterHandler(PrivacyAuditDeadLetterRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public void handle(PrivacyAuditEvent event, int attempts, RuntimeException exception) {
        repository.save(PrivacyAuditDeadLetterEntry.from(event, attempts, exception));
    }
}
