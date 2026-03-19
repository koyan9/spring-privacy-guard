/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantProvider;

import java.util.ArrayList;
import java.util.List;

public class PrivacyTenantAuditDeadLetterOperationsService {

    private final PrivacyAuditDeadLetterService privacyAuditDeadLetterService;
    private final PrivacyTenantAuditDeadLetterQueryService privacyTenantAuditDeadLetterQueryService;
    private final PrivacyTenantProvider tenantProvider;

    public PrivacyTenantAuditDeadLetterOperationsService(
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyTenantAuditDeadLetterQueryService privacyTenantAuditDeadLetterQueryService,
            PrivacyTenantProvider tenantProvider
    ) {
        this.privacyAuditDeadLetterService = privacyAuditDeadLetterService;
        this.privacyTenantAuditDeadLetterQueryService = privacyTenantAuditDeadLetterQueryService;
        this.tenantProvider = tenantProvider == null ? PrivacyTenantProvider.noop() : tenantProvider;
    }

    public int deleteByCriteria(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        if (tenantId == null || tenantId.isBlank()) {
            return privacyAuditDeadLetterService.deleteByCriteria(criteria);
        }
        List<PrivacyAuditDeadLetterEntry> selected = privacyTenantAuditDeadLetterQueryService.findByCriteria(tenantId, criteria);
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
        if (tenantId == null || tenantId.isBlank()) {
            return privacyAuditDeadLetterService.replayByCriteria(criteria);
        }
        List<PrivacyAuditDeadLetterEntry> selected = privacyTenantAuditDeadLetterQueryService.findByCriteria(tenantId, criteria);
        List<Long> replayedIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();

        for (PrivacyAuditDeadLetterEntry entry : selected) {
            if (entry.id() == null) {
                continue;
            }
            if (privacyAuditDeadLetterService.replay(entry.id())) {
                replayedIds.add(entry.id());
            } else {
                failedIds.add(entry.id());
            }
        }

        return new PrivacyAuditDeadLetterReplayResult(
                selected.size(),
                replayedIds.size(),
                failedIds.size(),
                List.copyOf(replayedIds),
                List.copyOf(failedIds)
        );
    }

    public PrivacyAuditDeadLetterReplayResult replayForCurrentTenant(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return replayByCriteria(tenantProvider.currentTenantId(), criteria);
    }
}
