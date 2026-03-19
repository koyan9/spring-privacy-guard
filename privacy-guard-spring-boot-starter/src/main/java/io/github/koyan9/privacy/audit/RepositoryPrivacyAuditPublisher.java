/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantProvider;

public class RepositoryPrivacyAuditPublisher implements PrivacyAuditPublisher {

    private final PrivacyAuditRepository privacyAuditRepository;
    private final PrivacyTenantProvider tenantProvider;
    private final PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver;
    private final PrivacyTenantAuditTelemetry telemetry;

    public RepositoryPrivacyAuditPublisher(PrivacyAuditRepository privacyAuditRepository) {
        this(
                privacyAuditRepository,
                PrivacyTenantProvider.noop(),
                PrivacyTenantAuditPolicyResolver.noop(),
                PrivacyTenantAuditTelemetry.noop()
        );
    }

    public RepositoryPrivacyAuditPublisher(
            PrivacyAuditRepository privacyAuditRepository,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver
    ) {
        this(
                privacyAuditRepository,
                tenantProvider,
                tenantAuditPolicyResolver,
                PrivacyTenantAuditTelemetry.noop()
        );
    }

    public RepositoryPrivacyAuditPublisher(
            PrivacyAuditRepository privacyAuditRepository,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditTelemetry telemetry
    ) {
        this.privacyAuditRepository = privacyAuditRepository;
        this.tenantProvider = tenantProvider == null ? PrivacyTenantProvider.noop() : tenantProvider;
        this.tenantAuditPolicyResolver = tenantAuditPolicyResolver == null
                ? PrivacyTenantAuditPolicyResolver.noop()
                : tenantAuditPolicyResolver;
        this.telemetry = telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry;
    }

    @Override
    public void publish(PrivacyAuditEvent event) {
        if (privacyAuditRepository instanceof PrivacyTenantAuditWriteRepository tenantAwareRepository) {
            String tenantId = currentTenantId();
            telemetry.recordWritePath("audit_write", "native");
            tenantAwareRepository.save(new PrivacyTenantAuditWriteRequest(
                    event,
                    tenantId,
                    tenantDetailKey(tenantId)
            ));
            return;
        }
        telemetry.recordWritePath("audit_write", "fallback");
        privacyAuditRepository.save(event);
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
