/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.PrivacyTenantProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PrivacyTenantAuditQueryService {

    private static final int DEFAULT_PAGE_SIZE = 200;

    private final PrivacyAuditQueryService privacyAuditQueryService;
    private final PrivacyAuditStatsService privacyAuditStatsService;
    private final PrivacyTenantProvider tenantProvider;
    private final PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver;
    private final PrivacyTenantAuditReadRepository tenantAuditReadRepository;
    private final Supplier<PrivacyTenantAuditTelemetry> telemetrySupplier;

    public PrivacyTenantAuditQueryService(
            PrivacyAuditQueryService privacyAuditQueryService,
            PrivacyAuditStatsService privacyAuditStatsService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver
    ) {
        this(
                privacyAuditQueryService,
                privacyAuditStatsService,
                tenantProvider,
                tenantAuditPolicyResolver,
                null,
                (Supplier<PrivacyTenantAuditTelemetry>) null
        );
    }

    public PrivacyTenantAuditQueryService(
            PrivacyAuditQueryService privacyAuditQueryService,
            PrivacyAuditStatsService privacyAuditStatsService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditReadRepository tenantAuditReadRepository
    ) {
        this(
                privacyAuditQueryService,
                privacyAuditStatsService,
                tenantProvider,
                tenantAuditPolicyResolver,
                tenantAuditReadRepository,
                (Supplier<PrivacyTenantAuditTelemetry>) null
        );
    }

    public PrivacyTenantAuditQueryService(
            PrivacyAuditQueryService privacyAuditQueryService,
            PrivacyAuditStatsService privacyAuditStatsService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditReadRepository tenantAuditReadRepository,
            PrivacyTenantAuditTelemetry telemetry
    ) {
        this(
                privacyAuditQueryService,
                privacyAuditStatsService,
                tenantProvider,
                tenantAuditPolicyResolver,
                tenantAuditReadRepository,
                () -> telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry
        );
    }

    public PrivacyTenantAuditQueryService(
            PrivacyAuditQueryService privacyAuditQueryService,
            PrivacyAuditStatsService privacyAuditStatsService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyTenantAuditReadRepository tenantAuditReadRepository,
            Supplier<PrivacyTenantAuditTelemetry> telemetrySupplier
    ) {
        this.privacyAuditQueryService = privacyAuditQueryService;
        this.privacyAuditStatsService = privacyAuditStatsService;
        this.tenantProvider = tenantProvider == null ? PrivacyTenantProvider.noop() : tenantProvider;
        this.tenantAuditPolicyResolver = tenantAuditPolicyResolver == null
                ? PrivacyTenantAuditPolicyResolver.noop()
                : tenantAuditPolicyResolver;
        this.tenantAuditReadRepository = tenantAuditReadRepository;
        this.telemetrySupplier = telemetrySupplier == null
                ? PrivacyTenantAuditTelemetry::noop
                : telemetrySupplier;
    }

    public List<PrivacyAuditEvent> findByCriteria(String tenantId, PrivacyAuditQueryCriteria criteria) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return privacyAuditQueryService.findByCriteria(criteria);
        }
        PrivacyAuditQueryCriteria normalized = criteria == null
                ? PrivacyAuditQueryCriteria.recent(100)
                : criteria.normalize();
        String detailKey = tenantDetailKey(normalizedTenant);
        if (tenantAuditReadRepository != null && tenantAuditReadRepository.supportsTenantRead()) {
            telemetry().recordQueryReadPath("audit", "native");
            return tenantAuditReadRepository.findByCriteria(normalizedTenant, detailKey, normalized);
        }
        telemetry().recordQueryReadPath("audit", "fallback");
        List<PrivacyAuditEvent> filtered = collectFilteredEvents(normalized, normalizedTenant);
        return filtered.stream()
                .skip(normalized.offset())
                .limit(normalized.limit())
                .toList();
    }

    public List<PrivacyAuditEvent> findForCurrentTenant(PrivacyAuditQueryCriteria criteria) {
        return findByCriteria(tenantProvider.currentTenantId(), criteria);
    }

    public PrivacyAuditQueryStats computeStats(String tenantId, PrivacyAuditQueryCriteria criteria) {
        String normalizedTenant = normalizeTenant(tenantId);
        if (normalizedTenant == null) {
            return privacyAuditStatsService.computeStats(criteria);
        }
        PrivacyAuditQueryCriteria normalized = criteria == null
                ? PrivacyAuditQueryCriteria.recent(100)
                : criteria.normalize();
        String detailKey = tenantDetailKey(normalizedTenant);
        if (tenantAuditReadRepository != null && tenantAuditReadRepository.supportsTenantRead()) {
            telemetry().recordQueryReadPath("audit_stats", "native");
            return tenantAuditReadRepository.computeStats(normalizedTenant, detailKey, normalized);
        }
        telemetry().recordQueryReadPath("audit_stats", "fallback");
        List<PrivacyAuditEvent> filtered = collectFilteredEvents(normalized, normalizedTenant);
        return new PrivacyAuditQueryStats(
                filtered.size(),
                aggregate(filtered, PrivacyAuditEvent::action),
                aggregate(filtered, PrivacyAuditEvent::outcome),
                aggregate(filtered, PrivacyAuditEvent::resourceType)
        );
    }

    public PrivacyAuditQueryStats computeStatsForCurrentTenant(PrivacyAuditQueryCriteria criteria) {
        return computeStats(tenantProvider.currentTenantId(), criteria);
    }

    private List<PrivacyAuditEvent> collectFilteredEvents(PrivacyAuditQueryCriteria criteria, String tenantId) {
        String detailKey = tenantDetailKey(tenantId);
        int pageSize = Math.max(DEFAULT_PAGE_SIZE, criteria.limit());
        int offset = 0;
        java.util.ArrayList<PrivacyAuditEvent> filtered = new java.util.ArrayList<>();
        while (true) {
            PrivacyAuditQueryCriteria pageCriteria = new PrivacyAuditQueryCriteria(
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
                    criteria.occurredFrom(),
                    criteria.occurredTo(),
                    criteria.sortDirection(),
                    pageSize,
                    offset
            );
            List<PrivacyAuditEvent> page = privacyAuditQueryService.findByCriteria(pageCriteria);
            if (page.isEmpty()) {
                break;
            }
            page.stream()
                    .filter(event -> tenantId.equals(event.details().get(detailKey)))
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

    private Map<String, Long> aggregate(List<PrivacyAuditEvent> events, Function<PrivacyAuditEvent, String> classifier) {
        return events.stream()
                .map(classifier)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
    }

    private PrivacyTenantAuditTelemetry telemetry() {
        PrivacyTenantAuditTelemetry telemetry = telemetrySupplier.get();
        return telemetry == null ? PrivacyTenantAuditTelemetry.noop() : telemetry;
    }
}
