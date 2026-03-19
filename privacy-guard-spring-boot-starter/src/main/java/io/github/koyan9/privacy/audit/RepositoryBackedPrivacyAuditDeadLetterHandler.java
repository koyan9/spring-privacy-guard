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
    private final PrivacyTenantAuditTelemetry telemetry;

    public RepositoryBackedPrivacyAuditDeadLetterHandler(PrivacyAuditDeadLetterRepository repository) {
        this(
                repository,
                PrivacyTenantProvider.noop(),
                PrivacyTenantAuditPolicyResolver.noop(),
                PrivacyTenantAuditTelemetry.noop()
        );
    }

    public RepositoryBackedPrivacyAuditDeadLetterHandler(
            PrivacyAuditDeadLetterRepository repository,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver
    ) {
        this(
                repository,
                tenantProvider,
                tenantAuditPolicyResolver,
                PrivacyTenantAuditTelemetry.noop()
        );
    }

    public RepositoryBackedPrivacyAuditDeadLetterHandler(
            PrivacyAuditDeadLetterRepository repository,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditTelemetry telemetry
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.tenantProvider = tenantProvider == null ? PrivacyTenantProvider.noop() : tenantProvider;
        this.tenantAuditPolicyResolver = tenantAuditPolicyResolver == null
                ? PrivacyTenantAuditPolicyResolver.noop()
                : tenantAuditPolicyResolver;
        this.telemetry = telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry;
    }

    @Override
    public void handle(PrivacyAuditEvent event, int attempts, RuntimeException exception) {
        PrivacyAuditDeadLetterEntry entry = PrivacyAuditDeadLetterEntry.from(event, attempts, exception);
        if (repository instanceof PrivacyTenantAuditDeadLetterWriteRepository tenantAwareRepository) {
            String tenantId = currentTenantId();
            telemetry.recordWritePath("dead_letter_write", "native");
            tenantAwareRepository.save(new PrivacyTenantAuditDeadLetterWriteRequest(
                    entry,
                    tenantId,
                    tenantDetailKey(tenantId)
            ));
            return;
        }
        telemetry.recordWritePath("dead_letter_write", "fallback");
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
