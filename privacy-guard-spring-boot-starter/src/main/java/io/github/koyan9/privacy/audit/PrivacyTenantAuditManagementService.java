/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantProvider;

import java.util.List;

public class PrivacyTenantAuditManagementService {

    private final PrivacyAuditQueryService privacyAuditQueryService;
    private final PrivacyAuditStatsService privacyAuditStatsService;
    private final PrivacyTenantAuditQueryService privacyTenantAuditQueryService;
    private final PrivacyAuditDeadLetterService privacyAuditDeadLetterService;
    private final PrivacyAuditDeadLetterStatsService privacyAuditDeadLetterStatsService;
    private final PrivacyTenantAuditDeadLetterQueryService privacyTenantAuditDeadLetterQueryService;
    private final PrivacyTenantAuditDeadLetterOperationsService privacyTenantAuditDeadLetterOperationsService;
    private final PrivacyAuditDeadLetterExchangeService privacyAuditDeadLetterExchangeService;
    private final PrivacyTenantAuditDeadLetterExchangeService privacyTenantAuditDeadLetterExchangeService;
    private final PrivacyTenantProvider tenantProvider;

    public PrivacyTenantAuditManagementService(
            PrivacyAuditQueryService privacyAuditQueryService,
            PrivacyAuditStatsService privacyAuditStatsService,
            PrivacyTenantAuditQueryService privacyTenantAuditQueryService,
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyAuditDeadLetterStatsService privacyAuditDeadLetterStatsService,
            PrivacyTenantAuditDeadLetterQueryService privacyTenantAuditDeadLetterQueryService,
            PrivacyTenantAuditDeadLetterOperationsService privacyTenantAuditDeadLetterOperationsService,
            PrivacyAuditDeadLetterExchangeService privacyAuditDeadLetterExchangeService,
            PrivacyTenantAuditDeadLetterExchangeService privacyTenantAuditDeadLetterExchangeService,
            PrivacyTenantProvider tenantProvider
    ) {
        this.privacyAuditQueryService = privacyAuditQueryService;
        this.privacyAuditStatsService = privacyAuditStatsService;
        this.privacyTenantAuditQueryService = privacyTenantAuditQueryService;
        this.privacyAuditDeadLetterService = privacyAuditDeadLetterService;
        this.privacyAuditDeadLetterStatsService = privacyAuditDeadLetterStatsService;
        this.privacyTenantAuditDeadLetterQueryService = privacyTenantAuditDeadLetterQueryService;
        this.privacyTenantAuditDeadLetterOperationsService = privacyTenantAuditDeadLetterOperationsService;
        this.privacyAuditDeadLetterExchangeService = privacyAuditDeadLetterExchangeService;
        this.privacyTenantAuditDeadLetterExchangeService = privacyTenantAuditDeadLetterExchangeService;
        this.tenantProvider = tenantProvider == null ? PrivacyTenantProvider.noop() : tenantProvider;
    }

    public List<PrivacyAuditEvent> findAuditEvents(String tenantId, PrivacyAuditQueryCriteria criteria) {
        return hasTenant(tenantId)
                ? privacyTenantAuditQueryService.findByCriteria(tenantId, criteria)
                : privacyAuditQueryService.findByCriteria(criteria);
    }

    public List<PrivacyAuditEvent> findAuditEventsForCurrentTenant(PrivacyAuditQueryCriteria criteria) {
        return findAuditEvents(tenantProvider.currentTenantId(), criteria);
    }

    public PrivacyAuditQueryStats computeAuditStats(String tenantId, PrivacyAuditQueryCriteria criteria) {
        return hasTenant(tenantId)
                ? privacyTenantAuditQueryService.computeStats(tenantId, criteria)
                : privacyAuditStatsService.computeStats(criteria);
    }

    public PrivacyAuditQueryStats computeAuditStatsForCurrentTenant(PrivacyAuditQueryCriteria criteria) {
        return computeAuditStats(tenantProvider.currentTenantId(), criteria);
    }

    public List<PrivacyAuditDeadLetterEntry> findDeadLetters(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        return hasTenant(tenantId)
                ? privacyTenantAuditDeadLetterQueryService.findByCriteria(tenantId, criteria)
                : privacyAuditDeadLetterService.findByCriteria(criteria);
    }

    public List<PrivacyAuditDeadLetterEntry> findDeadLettersForCurrentTenant(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return findDeadLetters(tenantProvider.currentTenantId(), criteria);
    }

    public PrivacyAuditDeadLetterStats computeDeadLetterStats(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        return hasTenant(tenantId)
                ? privacyTenantAuditDeadLetterQueryService.computeStats(tenantId, criteria)
                : privacyAuditDeadLetterStatsService.computeStats(criteria);
    }

    public PrivacyAuditDeadLetterStats computeDeadLetterStatsForCurrentTenant(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return computeDeadLetterStats(tenantProvider.currentTenantId(), criteria);
    }

    public boolean deleteDeadLetter(long id) {
        return privacyAuditDeadLetterService.delete(id);
    }

    public int deleteDeadLetters(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        return hasTenant(tenantId)
                ? privacyTenantAuditDeadLetterOperationsService.deleteByCriteria(tenantId, criteria)
                : privacyAuditDeadLetterService.deleteByCriteria(criteria);
    }

    public int deleteDeadLettersForCurrentTenant(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return deleteDeadLetters(tenantProvider.currentTenantId(), criteria);
    }

    public boolean replayDeadLetter(long id) {
        return privacyAuditDeadLetterService.replay(id);
    }

    public PrivacyAuditDeadLetterReplayResult replayDeadLetters(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        return hasTenant(tenantId)
                ? privacyTenantAuditDeadLetterOperationsService.replayByCriteria(tenantId, criteria)
                : privacyAuditDeadLetterService.replayByCriteria(criteria);
    }

    public PrivacyAuditDeadLetterReplayResult replayDeadLettersForCurrentTenant(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return replayDeadLetters(tenantProvider.currentTenantId(), criteria);
    }

    public PrivacyAuditDeadLetterExportManifest exportDeadLettersManifest(
            String tenantId,
            PrivacyAuditDeadLetterQueryCriteria criteria,
            String format
    ) {
        return hasTenant(tenantId)
                ? privacyTenantAuditDeadLetterExchangeService.exportManifest(tenantId, criteria, format)
                : privacyAuditDeadLetterExchangeService.exportManifest(criteria, format);
    }

    public String exportDeadLettersJson(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        return hasTenant(tenantId)
                ? privacyTenantAuditDeadLetterExchangeService.exportJson(tenantId, criteria)
                : privacyAuditDeadLetterExchangeService.exportJson(criteria);
    }

    public String exportDeadLettersCsv(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        return hasTenant(tenantId)
                ? privacyTenantAuditDeadLetterExchangeService.exportCsv(tenantId, criteria)
                : privacyAuditDeadLetterExchangeService.exportCsv(criteria);
    }

    public PrivacyAuditDeadLetterImportResult importDeadLettersJson(
            String tenantId,
            String content,
            boolean deduplicate,
            String expectedChecksum
    ) {
        return hasTenant(tenantId)
                ? privacyTenantAuditDeadLetterExchangeService.importJson(tenantId, content, deduplicate, expectedChecksum)
                : privacyAuditDeadLetterExchangeService.importJson(content, deduplicate, expectedChecksum);
    }

    public PrivacyAuditDeadLetterImportResult importDeadLettersCsv(
            String tenantId,
            String content,
            boolean deduplicate,
            String expectedChecksum
    ) {
        return hasTenant(tenantId)
                ? privacyTenantAuditDeadLetterExchangeService.importCsv(tenantId, content, deduplicate, expectedChecksum)
                : privacyAuditDeadLetterExchangeService.importCsv(content, deduplicate, expectedChecksum);
    }

    private boolean hasTenant(String tenantId) {
        return tenantId != null && !tenantId.isBlank();
    }
}
