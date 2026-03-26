/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantProvider;

import java.util.List;
import java.util.function.Supplier;

public class PrivacyTenantAuditDeadLetterOperationsService {

    private final PrivacyAuditDeadLetterService privacyAuditDeadLetterService;
    private final PrivacyTenantAuditDeadLetterQueryService privacyTenantAuditDeadLetterQueryService;
    private final PrivacyTenantProvider tenantProvider;
    private final PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver;
    private final PrivacyTenantAuditDeadLetterDeleteRepository tenantDeleteRepository;
    private final PrivacyTenantAuditDeadLetterReplayRepository tenantReplayRepository;
    private final Supplier<PrivacyTenantAuditTelemetry> telemetrySupplier;

    public PrivacyTenantAuditDeadLetterOperationsService(
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyTenantAuditDeadLetterQueryService privacyTenantAuditDeadLetterQueryService,
            PrivacyTenantProvider tenantProvider
    ) {
        this(
                privacyAuditDeadLetterService,
                privacyTenantAuditDeadLetterQueryService,
                tenantProvider,
                null,
                null,
                null,
                (Supplier<PrivacyTenantAuditTelemetry>) null
        );
    }

    public PrivacyTenantAuditDeadLetterOperationsService(
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyTenantAuditDeadLetterQueryService privacyTenantAuditDeadLetterQueryService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditDeadLetterDeleteRepository tenantDeleteRepository
    ) {
        this(
                privacyAuditDeadLetterService,
                privacyTenantAuditDeadLetterQueryService,
                tenantProvider,
                tenantAuditPolicyResolver,
                tenantDeleteRepository,
                null,
                (Supplier<PrivacyTenantAuditTelemetry>) null
        );
    }

    public PrivacyTenantAuditDeadLetterOperationsService(
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyTenantAuditDeadLetterQueryService privacyTenantAuditDeadLetterQueryService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditDeadLetterDeleteRepository tenantDeleteRepository,
            PrivacyTenantAuditDeadLetterReplayRepository tenantReplayRepository,
            PrivacyTenantAuditTelemetry telemetry
    ) {
        this(
                privacyAuditDeadLetterService,
                privacyTenantAuditDeadLetterQueryService,
                tenantProvider,
                tenantAuditPolicyResolver,
                tenantDeleteRepository,
                tenantReplayRepository,
                () -> telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry
        );
    }

    public PrivacyTenantAuditDeadLetterOperationsService(
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyTenantAuditDeadLetterQueryService privacyTenantAuditDeadLetterQueryService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditDeadLetterDeleteRepository tenantDeleteRepository,
            PrivacyTenantAuditDeadLetterReplayRepository tenantReplayRepository,
            Supplier<PrivacyTenantAuditTelemetry> telemetrySupplier
    ) {
        this.privacyAuditDeadLetterService = privacyAuditDeadLetterService;
        this.privacyTenantAuditDeadLetterQueryService = privacyTenantAuditDeadLetterQueryService;
        this.tenantProvider = tenantProvider == null ? PrivacyTenantProvider.noop() : tenantProvider;
        this.tenantAuditPolicyResolver = tenantAuditPolicyResolver == null
                ? PrivacyTenantAuditPolicyResolver.noop()
                : tenantAuditPolicyResolver;
        this.tenantDeleteRepository = tenantDeleteRepository;
        this.tenantReplayRepository = tenantReplayRepository;
        this.telemetrySupplier = telemetrySupplier == null
                ? PrivacyTenantAuditTelemetry::noop
                : telemetrySupplier;
    }

    public int deleteByCriteria(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return privacyAuditDeadLetterService.deleteByCriteria(criteria);
        }
        PrivacyAuditDeadLetterQueryCriteria normalizedCriteria = criteria == null
                ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                : criteria.normalize();
        if (tenantDeleteRepository != null) {
            telemetry().recordWritePath("dead_letter_delete", "native");
            return tenantDeleteRepository.deleteByCriteria(
                    normalizedTenant,
                    tenantDetailKey(normalizedTenant),
                    normalizedCriteria
            );
        }
        telemetry().recordWritePath("dead_letter_delete", "fallback");
        List<PrivacyAuditDeadLetterEntry> selected = privacyTenantAuditDeadLetterQueryService.findByCriteria(normalizedTenant, normalizedCriteria);
        int deleted = 0;
        for (PrivacyAuditDeadLetterEntry entry : selected) {
            if (entry.id() != null && privacyAuditDeadLetterService.delete(entry.id())) {
                deleted++;
            }
        }
        return deleted;
    }

    public int deleteForCurrentTenant(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return deleteByCriteria(tenantProvider.currentTenantId(), criteria);
    }

    public PrivacyAuditDeadLetterReplayResult replayByCriteria(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return privacyAuditDeadLetterService.replayByCriteria(criteria);
        }
        PrivacyAuditDeadLetterQueryCriteria normalizedCriteria = criteria == null
                ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                : criteria.normalize();
        String detailKey = tenantDetailKey(normalizedTenant);
        if (tenantReplayRepository != null) {
            telemetry().recordWritePath("dead_letter_replay", "native");
            return tenantReplayRepository.replayByCriteria(
                    normalizedTenant,
                    detailKey,
                    normalizedCriteria,
                    privacyAuditDeadLetterService::replaySelectedEntry
            );
        }
        telemetry().recordWritePath("dead_letter_replay", "fallback");
        return privacyAuditDeadLetterService.replayEntries(
                privacyTenantAuditDeadLetterQueryService.findByCriteria(normalizedTenant, normalizedCriteria)
        );
    }

    public PrivacyAuditDeadLetterReplayResult replayForCurrentTenant(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return replayByCriteria(tenantProvider.currentTenantId(), criteria);
    }

    private String tenantDetailKey(String tenantId) {
        PrivacyTenantAuditPolicy policy = tenantAuditPolicyResolver.resolve(tenantId);
        if (policy == null) {
            return "tenantId";
        }
        return policy.tenantDetailKey();
    }

    private String normalizeTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantId.trim();
    }

    private PrivacyTenantAuditTelemetry telemetry() {
        PrivacyTenantAuditTelemetry telemetry = telemetrySupplier.get();
        return telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry;
    }
}
