/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantProvider;

public class PrivacyTenantAuditDeadLetterObservationService {

    private final PrivacyTenantAuditDeadLetterQueryService tenantQueryService;
    private final PrivacyTenantProvider tenantProvider;
    private final PrivacyTenantDeadLetterObservabilityPolicyResolver observabilityPolicyResolver;
    private final long defaultWarningThreshold;
    private final long defaultDownThreshold;

    public PrivacyTenantAuditDeadLetterObservationService(
            PrivacyTenantAuditDeadLetterQueryService tenantQueryService,
            PrivacyTenantProvider tenantProvider,
            long warningThreshold,
            long downThreshold
    ) {
        this(
                tenantQueryService,
                tenantProvider,
                PrivacyTenantDeadLetterObservabilityPolicyResolver.noop(),
                warningThreshold,
                downThreshold
        );
    }

    public PrivacyTenantAuditDeadLetterObservationService(
            PrivacyTenantAuditDeadLetterQueryService tenantQueryService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantDeadLetterObservabilityPolicyResolver observabilityPolicyResolver,
            long warningThreshold,
            long downThreshold
    ) {
        this.tenantQueryService = tenantQueryService;
        this.tenantProvider = tenantProvider == null ? PrivacyTenantProvider.noop() : tenantProvider;
        this.observabilityPolicyResolver = observabilityPolicyResolver == null
                ? PrivacyTenantDeadLetterObservabilityPolicyResolver.noop()
                : observabilityPolicyResolver;
        this.defaultWarningThreshold = Math.max(0, warningThreshold);
        this.defaultDownThreshold = Math.max(this.defaultWarningThreshold, downThreshold);
    }

    public PrivacyAuditDeadLetterBacklogSnapshot currentSnapshot(String tenantId) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return emptySnapshot();
        }
        ResolvedThresholds thresholds = resolveThresholds(normalizedTenant);
        PrivacyAuditDeadLetterStats stats = tenantQueryService.computeStats(
                normalizedTenant,
                PrivacyAuditDeadLetterQueryCriteria.recent(1)
        );
        long total = stats.total();
        return new PrivacyAuditDeadLetterBacklogSnapshot(
                total,
                thresholds.warningThreshold(),
                thresholds.downThreshold(),
                determineState(total, thresholds),
                stats.byAction(),
                stats.byOutcome(),
                stats.byResourceType(),
                stats.byErrorType()
        );
    }

    public PrivacyAuditDeadLetterBacklogSnapshot currentSnapshotForCurrentTenant() {
        return currentSnapshot(tenantProvider.currentTenantId());
    }

    private PrivacyAuditDeadLetterBacklogSnapshot emptySnapshot() {
        ResolvedThresholds thresholds = new ResolvedThresholds(defaultWarningThreshold, defaultDownThreshold);
        return new PrivacyAuditDeadLetterBacklogSnapshot(
                0L,
                thresholds.warningThreshold(),
                thresholds.downThreshold(),
                PrivacyAuditDeadLetterBacklogState.CLEAR,
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of()
        );
    }

    private PrivacyAuditDeadLetterBacklogState determineState(long total, ResolvedThresholds thresholds) {
        if (total >= thresholds.downThreshold()) {
            return PrivacyAuditDeadLetterBacklogState.DOWN;
        }
        if (total >= thresholds.warningThreshold()) {
            return PrivacyAuditDeadLetterBacklogState.WARNING;
        }
        return PrivacyAuditDeadLetterBacklogState.CLEAR;
    }

    private ResolvedThresholds resolveThresholds(String tenantId) {
        PrivacyTenantDeadLetterObservabilityPolicy policy = observabilityPolicyResolver.resolve(tenantId);
        if (policy == null) {
            policy = PrivacyTenantDeadLetterObservabilityPolicy.none();
        }
        return new ResolvedThresholds(
                policy.resolveWarningThreshold(defaultWarningThreshold),
                policy.resolveDownThreshold(defaultWarningThreshold, defaultDownThreshold)
        );
    }

    private String normalizeTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantId.trim();
    }

    private record ResolvedThresholds(long warningThreshold, long downThreshold) {
    }
}
