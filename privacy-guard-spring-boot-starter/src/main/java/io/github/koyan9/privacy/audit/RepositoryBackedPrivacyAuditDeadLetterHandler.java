/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantProvider;

import java.util.Objects;
import java.util.function.Supplier;

public class RepositoryBackedPrivacyAuditDeadLetterHandler implements PrivacyAuditDeadLetterHandler {

    private final PrivacyAuditDeadLetterRepository repository;
    private final PrivacyTenantProvider tenantProvider;
    private final PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver;
    private final Supplier<PrivacyTenantAuditTelemetry> telemetrySupplier;

    public RepositoryBackedPrivacyAuditDeadLetterHandler(PrivacyAuditDeadLetterRepository repository) {
        this(
                repository,
                PrivacyTenantProvider.noop(),
                PrivacyTenantAuditPolicyResolver.noop(),
                (Supplier<PrivacyTenantAuditTelemetry>) null
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
                (Supplier<PrivacyTenantAuditTelemetry>) null
        );
    }

    public RepositoryBackedPrivacyAuditDeadLetterHandler(
            PrivacyAuditDeadLetterRepository repository,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditTelemetry telemetry
    ) {
        this(
                repository,
                tenantProvider,
                tenantAuditPolicyResolver,
                () -> telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry
        );
    }

    public RepositoryBackedPrivacyAuditDeadLetterHandler(
            PrivacyAuditDeadLetterRepository repository,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            Supplier<PrivacyTenantAuditTelemetry> telemetrySupplier
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.tenantProvider = tenantProvider == null ? PrivacyTenantProvider.noop() : tenantProvider;
        this.tenantAuditPolicyResolver = tenantAuditPolicyResolver == null
                ? PrivacyTenantAuditPolicyResolver.noop()
                : tenantAuditPolicyResolver;
        this.telemetrySupplier = telemetrySupplier == null
                ? PrivacyTenantAuditTelemetry::noop
                : telemetrySupplier;
    }

    @Override
    public void handle(PrivacyAuditEvent event, int attempts, RuntimeException exception) {
        PrivacyAuditDeadLetterEntry entry = PrivacyAuditDeadLetterEntry.from(event, attempts, exception);
        if (repository instanceof PrivacyTenantAuditDeadLetterWriteRepository tenantAwareRepository
                && tenantAwareRepository.supportsTenantWrite()) {
            String tenantId = currentTenantId();
            telemetry().recordWritePath("dead_letter_write", "native");
            tenantAwareRepository.save(new PrivacyTenantAuditDeadLetterWriteRequest(
                    entry,
                    tenantId,
                    tenantDetailKey(tenantId)
            ));
            return;
        }
        telemetry().recordWritePath("dead_letter_write", "fallback");
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

    private PrivacyTenantAuditTelemetry telemetry() {
        PrivacyTenantAuditTelemetry telemetry = telemetrySupplier.get();
        return telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry;
    }
}
