/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantProvider;

import java.util.Objects;

public class RepositoryBackedPrivacyAuditDeadLetterHandler implements PrivacyAuditDeadLetterHandler {

    private final PrivacyAuditDeadLetterRepository repository;
    private final PrivacyTenantProvider tenantProvider;
    private final PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver;

    public RepositoryBackedPrivacyAuditDeadLetterHandler(PrivacyAuditDeadLetterRepository repository) {
        this(repository, PrivacyTenantProvider.noop(), PrivacyTenantAuditPolicyResolver.noop());
    }

    public RepositoryBackedPrivacyAuditDeadLetterHandler(
            PrivacyAuditDeadLetterRepository repository,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.tenantProvider = tenantProvider == null ? PrivacyTenantProvider.noop() : tenantProvider;
        this.tenantAuditPolicyResolver = tenantAuditPolicyResolver == null
                ? PrivacyTenantAuditPolicyResolver.noop()
                : tenantAuditPolicyResolver;
    }

    @Override
    public void handle(PrivacyAuditEvent event, int attempts, RuntimeException exception) {
        PrivacyAuditDeadLetterEntry entry = PrivacyAuditDeadLetterEntry.from(event, attempts, exception);
        if (repository instanceof PrivacyTenantAuditDeadLetterWriteRepository tenantAwareRepository) {
            String tenantId = currentTenantId();
            tenantAwareRepository.save(new PrivacyTenantAuditDeadLetterWriteRequest(
                    entry,
                    tenantId,
                    tenantDetailKey(tenantId)
            ));
            return;
        }
        repository.save(entry);
    }

    private String currentTenantId() {
        String tenantId = tenantProvider.currentTenantId();
        return tenantId == null || tenantId.isBlank() ? null : tenantId.trim();
    }

    private String tenantDetailKey(String tenantId) {
        PrivacyTenantAuditPolicy policy = tenantAuditPolicyResolver.resolve(tenantId);
        return policy == null ? "tenantId" : policy.tenantDetailKey();
    }
}
