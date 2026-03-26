/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditEvent;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryCriteria;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryStats;
import io.github.koyan9.privacy.audit.PrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditSortDirection;
import io.github.koyan9.privacy.audit.PrivacyAuditStatsRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditReadRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditWriteRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditWriteRequest;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CustomTenantAuditRepository implements
        PrivacyAuditRepository,
        PrivacyAuditQueryRepository,
        PrivacyAuditStatsRepository,
        PrivacyTenantAuditReadRepository,
        PrivacyTenantAuditWriteRepository {

    private final CopyOnWriteArrayList<PrivacyAuditEvent> allEvents = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<PrivacyAuditEvent>> tenantBuckets = new ConcurrentHashMap<>();

    @Override
    public void save(PrivacyAuditEvent event) {
        store(event, resolveTenantId(event.details(), null, null));
    }

    @Override
    public void saveAll(List<PrivacyAuditEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        events.forEach(this::save);
    }

    @Override
    public List<PrivacyAuditEvent> findByCriteria(PrivacyAuditQueryCriteria criteria) {
        PrivacyAuditQueryCriteria normalized = criteria.normalize();
        Stream<PrivacyAuditEvent> stream = sort(filteredStream(allEvents.stream(), normalized), normalized.sortDirection());
        if (normalized.offset() > 0) {
            stream = stream.skip(normalized.offset());
        }
        return stream.limit(normalized.limit()).toList();
    }

    @Override
    public PrivacyAuditQueryStats computeStats(PrivacyAuditQueryCriteria criteria) {
        List<PrivacyAuditEvent> filtered = filteredStream(allEvents.stream(), criteria.normalize()).toList();
        return new PrivacyAuditQueryStats(
                filtered.size(),
                aggregate(filtered, PrivacyAuditEvent::action),
                aggregate(filtered, PrivacyAuditEvent::outcome),
                aggregate(filtered, PrivacyAuditEvent::resourceType)
        );
    }

    @Override
    public List<PrivacyAuditEvent> findByCriteria(String tenantId, String tenantDetailKey, PrivacyAuditQueryCriteria criteria) {
        PrivacyAuditQueryCriteria normalized = criteria.normalize();
        Stream<PrivacyAuditEvent> stream = sort(filteredStream(bucket(tenantId).stream(), normalized), normalized.sortDirection());
        if (normalized.offset() > 0) {
            stream = stream.skip(normalized.offset());
        }
        return stream.limit(normalized.limit()).toList();
    }

    @Override
    public PrivacyAuditQueryStats computeStats(String tenantId, String tenantDetailKey, PrivacyAuditQueryCriteria criteria) {
        List<PrivacyAuditEvent> filtered = filteredStream(bucket(tenantId).stream(), criteria.normalize()).toList();
        return new PrivacyAuditQueryStats(
                filtered.size(),
                aggregate(filtered, PrivacyAuditEvent::action),
                aggregate(filtered, PrivacyAuditEvent::outcome),
                aggregate(filtered, PrivacyAuditEvent::resourceType)
        );
    }

    @Override
    public void save(PrivacyTenantAuditWriteRequest request) {
        store(request.event(), resolveTenantId(request.event().details(), request.tenantId(), request.tenantDetailKey()));
    }

    @Override
    public void saveAllTenantAware(List<PrivacyTenantAuditWriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        requests.forEach(this::save);
    }

    void clear() {
        allEvents.clear();
        tenantBuckets.clear();
    }

    private void store(PrivacyAuditEvent event, String tenantId) {
        allEvents.add(event);
        if (tenantId != null && !tenantId.isBlank()) {
            bucket(tenantId).add(event);
        }
    }

    private CopyOnWriteArrayList<PrivacyAuditEvent> bucket(String tenantId) {
        return tenantBuckets.computeIfAbsent(tenantId == null ? "__blank__" : tenantId, ignored -> new CopyOnWriteArrayList<>());
    }

    private Stream<PrivacyAuditEvent> filteredStream(Stream<PrivacyAuditEvent> stream, PrivacyAuditQueryCriteria criteria) {
        return stream
                .filter(event -> matches(criteria.action(), criteria.actionLike(), event.action()))
                .filter(event -> matches(criteria.resourceType(), criteria.resourceTypeLike(), event.resourceType()))
                .filter(event -> matches(criteria.resourceId(), criteria.resourceIdLike(), event.resourceId()))
                .filter(event -> matches(criteria.actor(), criteria.actorLike(), event.actor()))
                .filter(event -> matches(criteria.outcome(), criteria.outcomeLike(), event.outcome()))
                .filter(event -> criteria.occurredFrom() == null || !event.occurredAt().isBefore(criteria.occurredFrom()))
                .filter(event -> criteria.occurredTo() == null || !event.occurredAt().isAfter(criteria.occurredTo()));
    }

    private Stream<PrivacyAuditEvent> sort(Stream<PrivacyAuditEvent> stream, PrivacyAuditSortDirection sortDirection) {
        Comparator<PrivacyAuditEvent> comparator = Comparator.comparing(PrivacyAuditEvent::occurredAt);
        if (sortDirection == PrivacyAuditSortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return stream.sorted(comparator);
    }

    private Map<String, Long> aggregate(List<PrivacyAuditEvent> events, Function<PrivacyAuditEvent, String> classifier) {
        return events.stream()
                .collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.counting()));
    }

    private boolean matches(String expected, String fuzzy, String actual) {
        if (actual == null) {
            return expected == null && fuzzy == null;
        }
        if (expected != null && !expected.equals(actual)) {
            return false;
        }
        return fuzzy == null || actual.contains(fuzzy);
    }

    private String resolveTenantId(Map<String, String> details, String explicitTenantId, String tenantDetailKey) {
        if (explicitTenantId != null && !explicitTenantId.isBlank()) {
            return explicitTenantId;
        }
        if (details == null || details.isEmpty()) {
            return null;
        }
        if (tenantDetailKey != null && !tenantDetailKey.isBlank()) {
            String tenant = details.get(tenantDetailKey);
            if (tenant != null && !tenant.isBlank()) {
                return tenant;
            }
        }
        String tenant = details.get("tenant");
        if (tenant != null && !tenant.isBlank()) {
            return tenant;
        }
        tenant = details.get("tenantId");
        return tenant == null || tenant.isBlank() ? null : tenant;
    }
}
