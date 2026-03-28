/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PrivacyTenantAuditDeadLetterQueryService {

    private static final int DEFAULT_PAGE_SIZE = 200;

    private final PrivacyAuditDeadLetterService privacyAuditDeadLetterService;
    private final PrivacyAuditDeadLetterStatsService privacyAuditDeadLetterStatsService;
    private final PrivacyTenantProvider tenantProvider;
    private final PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver;
    private final PrivacyTenantAuditDeadLetterReadRepository tenantAuditDeadLetterReadRepository;
    private final Supplier<PrivacyTenantAuditTelemetry> telemetrySupplier;

    public PrivacyTenantAuditDeadLetterQueryService(
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyAuditDeadLetterStatsService privacyAuditDeadLetterStatsService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver
    ) {
        this(
                privacyAuditDeadLetterService,
                privacyAuditDeadLetterStatsService,
                tenantProvider,
                tenantAuditPolicyResolver,
                null,
                (Supplier<PrivacyTenantAuditTelemetry>) null
        );
    }

    public PrivacyTenantAuditDeadLetterQueryService(
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyAuditDeadLetterStatsService privacyAuditDeadLetterStatsService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditDeadLetterReadRepository tenantAuditDeadLetterReadRepository
    ) {
        this(
                privacyAuditDeadLetterService,
                privacyAuditDeadLetterStatsService,
                tenantProvider,
                tenantAuditPolicyResolver,
                tenantAuditDeadLetterReadRepository,
                (Supplier<PrivacyTenantAuditTelemetry>) null
        );
    }

    public PrivacyTenantAuditDeadLetterQueryService(
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyAuditDeadLetterStatsService privacyAuditDeadLetterStatsService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditDeadLetterReadRepository tenantAuditDeadLetterReadRepository,
            PrivacyTenantAuditTelemetry telemetry
    ) {
        this(
                privacyAuditDeadLetterService,
                privacyAuditDeadLetterStatsService,
                tenantProvider,
                tenantAuditPolicyResolver,
                tenantAuditDeadLetterReadRepository,
                () -> telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry
        );
    }

    public PrivacyTenantAuditDeadLetterQueryService(
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyAuditDeadLetterStatsService privacyAuditDeadLetterStatsService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditDeadLetterReadRepository tenantAuditDeadLetterReadRepository,
            Supplier<PrivacyTenantAuditTelemetry> telemetrySupplier
    ) {
        this.privacyAuditDeadLetterService = privacyAuditDeadLetterService;
        this.privacyAuditDeadLetterStatsService = privacyAuditDeadLetterStatsService;
        this.tenantProvider = tenantProvider == null ? PrivacyTenantProvider.noop() : tenantProvider;
        this.tenantAuditPolicyResolver = tenantAuditPolicyResolver == null
                ? PrivacyTenantAuditPolicyResolver.noop()
                : tenantAuditPolicyResolver;
        this.tenantAuditDeadLetterReadRepository = tenantAuditDeadLetterReadRepository;
        this.telemetrySupplier = telemetrySupplier == null
                ? PrivacyTenantAuditTelemetry::noop
                : telemetrySupplier;
    }

    public List<PrivacyAuditDeadLetterEntry> findByCriteria(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return privacyAuditDeadLetterService.findByCriteria(criteria);
        }
        PrivacyAuditDeadLetterQueryCriteria normalized = criteria == null
                ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                : criteria.normalize();
        String detailKey = tenantDetailKey(normalizedTenant);
        if (tenantAuditDeadLetterReadRepository != null && tenantAuditDeadLetterReadRepository.supportsTenantRead()) {
            telemetry().recordQueryReadPath("dead_letter", "native");
            return tenantAuditDeadLetterReadRepository.findByCriteria(normalizedTenant, detailKey, normalized);
        }
        telemetry().recordQueryReadPath("dead_letter", "fallback");
        List<PrivacyAuditDeadLetterEntry> filtered = collectFilteredEntries(normalized, normalizedTenant);
        return filtered.stream()
                .skip(normalized.offset())
                .limit(normalized.limit())
                .toList();
    }

    public List<PrivacyAuditDeadLetterEntry> findForCurrentTenant(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return findByCriteria(tenantProvider.currentTenantId(), criteria);
    }

    public Optional<PrivacyAuditDeadLetterEntry> findById(String tenantId, long id) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return privacyAuditDeadLetterService.findById(id);
        }
        String detailKey = tenantDetailKey(normalizedTenant);
        if (tenantAuditDeadLetterReadRepository != null && tenantAuditDeadLetterReadRepository.supportsTenantFindById()) {
            telemetry().recordQueryReadPath("dead_letter_find_by_id", "native");
            return tenantAuditDeadLetterReadRepository.findById(normalizedTenant, detailKey, id);
        }
        telemetry().recordQueryReadPath("dead_letter_find_by_id", "fallback");
        return privacyAuditDeadLetterService.findById(id)
                .filter(entry -> normalizedTenant.equals(entry.details().get(detailKey)));
    }

    public Optional<PrivacyAuditDeadLetterEntry> findByIdForCurrentTenant(long id) {
        return findById(tenantProvider.currentTenantId(), id);
    }

    public PrivacyAuditDeadLetterStats computeStats(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return privacyAuditDeadLetterStatsService.computeStats(criteria);
        }
        PrivacyAuditDeadLetterQueryCriteria normalized = criteria == null
                ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                : criteria.normalize();
        String detailKey = tenantDetailKey(normalizedTenant);
        if (tenantAuditDeadLetterReadRepository != null && tenantAuditDeadLetterReadRepository.supportsTenantRead()) {
            telemetry().recordQueryReadPath("dead_letter_stats", "native");
            return tenantAuditDeadLetterReadRepository.computeStats(normalizedTenant, detailKey, normalized);
        }
        telemetry().recordQueryReadPath("dead_letter_stats", "fallback");
        List<PrivacyAuditDeadLetterEntry> filtered = collectFilteredEntries(normalized, normalizedTenant);
        return new PrivacyAuditDeadLetterStats(
                filtered.size(),
                aggregate(filtered, PrivacyAuditDeadLetterEntry::action),
                aggregate(filtered, PrivacyAuditDeadLetterEntry::outcome),
                aggregate(filtered, PrivacyAuditDeadLetterEntry::resourceType),
                aggregate(filtered, PrivacyAuditDeadLetterEntry::errorType)
        );
    }

    public PrivacyAuditDeadLetterStats computeStatsForCurrentTenant(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return computeStats(tenantProvider.currentTenantId(), criteria);
    }

    private List<PrivacyAuditDeadLetterEntry> collectFilteredEntries(PrivacyAuditDeadLetterQueryCriteria criteria, String tenantId) {
        String detailKey = tenantDetailKey(tenantId);
        int pageSize = Math.max(DEFAULT_PAGE_SIZE, criteria.limit());
        int offset = 0;
        java.util.ArrayList<PrivacyAuditDeadLetterEntry> filtered = new java.util.ArrayList<>();
        while (true) {
            PrivacyAuditDeadLetterQueryCriteria pageCriteria = new PrivacyAuditDeadLetterQueryCriteria(
                    criteria.action(),
                    criteria.actionLike(),
                    criteria.resourceType(),
                    criteria.resourceTypeLike(),
                    criteria.resourceId(),
                    criteria.resourceIdLike(),
                    criteria.actor(),
                    criteria.actorLike(),
                    criteria.outcome(),
                    criteria.outcomeLike(),
                    criteria.errorType(),
                    criteria.errorMessageLike(),
                    criteria.failedFrom(),
                    criteria.failedTo(),
                    criteria.occurredFrom(),
                    criteria.occurredTo(),
                    criteria.sortDirection(),
                    pageSize,
                    offset
            );
            List<PrivacyAuditDeadLetterEntry> page = privacyAuditDeadLetterService.findByCriteria(pageCriteria);
            if (page.isEmpty()) {
                break;
            }
            page.stream()
                    .filter(entry -> tenantId.equals(entry.details().get(detailKey)))
                    .forEach(filtered::add);
            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
        return List.copyOf(filtered);
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

    private Map<String, Long> aggregate(List<PrivacyAuditDeadLetterEntry> entries, Function<PrivacyAuditDeadLetterEntry, String> classifier) {
        return entries.stream()
                .map(classifier)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
    }

    private PrivacyTenantAuditTelemetry telemetry() {
        PrivacyTenantAuditTelemetry telemetry = telemetrySupplier.get();
        return telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry;
    }
}
