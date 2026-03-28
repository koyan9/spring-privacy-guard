/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterEntry;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterQueryCriteria;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterReplayResult;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterStats;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterStatsRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditSortDirection;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterDeleteRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterReadRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterReplayRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterWriteRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterWriteRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CustomTenantDeadLetterRepository implements
        PrivacyAuditDeadLetterRepository,
        PrivacyAuditDeadLetterStatsRepository,
        PrivacyTenantAuditDeadLetterReadRepository,
        PrivacyTenantAuditDeadLetterWriteRepository,
        PrivacyTenantAuditDeadLetterDeleteRepository,
        PrivacyTenantAuditDeadLetterReplayRepository {

    private final AtomicLong sequence = new AtomicLong();
    private final CopyOnWriteArrayList<PrivacyAuditDeadLetterEntry> allEntries = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<PrivacyAuditDeadLetterEntry>> tenantBuckets = new ConcurrentHashMap<>();

    @Override
    public void save(PrivacyAuditDeadLetterEntry entry) {
        store(entry, resolveTenantId(entry.details(), null, null));
    }

    @Override
    public void saveAll(List<PrivacyAuditDeadLetterEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        entries.forEach(this::save);
    }

    @Override
    public List<PrivacyAuditDeadLetterEntry> findAll() {
        return sort(allEntries.stream(), PrivacyAuditSortDirection.DESC).toList();
    }

    @Override
    public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
        PrivacyAuditDeadLetterQueryCriteria normalized = normalize(criteria);
        Stream<PrivacyAuditDeadLetterEntry> stream = sort(filteredStream(allEntries.stream(), normalized), normalized.sortDirection());
        if (normalized.offset() > 0) {
            stream = stream.skip(normalized.offset());
        }
        return stream.limit(normalized.limit()).toList();
    }

    @Override
    public PrivacyAuditDeadLetterStats computeStats(PrivacyAuditDeadLetterQueryCriteria criteria) {
        List<PrivacyAuditDeadLetterEntry> filtered = filteredStream(allEntries.stream(), normalize(criteria)).toList();
        return stats(filtered);
    }

    @Override
    public List<PrivacyAuditDeadLetterEntry> findByCriteria(
            String tenantId,
            String tenantDetailKey,
            PrivacyAuditDeadLetterQueryCriteria criteria
    ) {
        PrivacyAuditDeadLetterQueryCriteria normalized = normalize(criteria);
        Stream<PrivacyAuditDeadLetterEntry> stream = sort(filteredStream(bucket(tenantId).stream(), normalized), normalized.sortDirection());
        if (normalized.offset() > 0) {
            stream = stream.skip(normalized.offset());
        }
        return stream.limit(normalized.limit()).toList();
    }

    @Override
    public PrivacyAuditDeadLetterStats computeStats(
            String tenantId,
            String tenantDetailKey,
            PrivacyAuditDeadLetterQueryCriteria criteria
    ) {
        return stats(filteredStream(bucket(tenantId).stream(), normalize(criteria)).toList());
    }

    @Override
    public boolean supportsTenantRead() {
        return true;
    }

    @Override
    public Optional<PrivacyAuditDeadLetterEntry> findById(
            String tenantId,
            String tenantDetailKey,
            long id
    ) {
        if (tenantId == null || tenantId.isBlank()) {
            return findById(id);
        }
        return bucket(tenantId).stream()
                .filter(entry -> entry.id() != null && entry.id() == id)
                .findFirst();
    }

    @Override
    public boolean supportsTenantFindById() {
        return true;
    }

    @Override
    public boolean supportsTenantExchangeRead() {
        return true;
    }

    @Override
    public boolean supportsTenantImport() {
        return true;
    }

    @Override
    public boolean supportsTenantWrite() {
        return true;
    }

    @Override
    public void save(PrivacyTenantAuditDeadLetterWriteRequest request) {
        store(request.entry(), resolveTenantId(request.entry().details(), request.tenantId(), request.tenantDetailKey()));
    }

    @Override
    public void saveAllTenantAware(List<PrivacyTenantAuditDeadLetterWriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        requests.forEach(this::save);
    }

    @Override
    public Optional<PrivacyAuditDeadLetterEntry> findById(long id) {
        return allEntries.stream().filter(entry -> entry.id() != null && entry.id() == id).findFirst();
    }

    @Override
    public boolean deleteById(long id) {
        Optional<PrivacyAuditDeadLetterEntry> entry = findById(id);
        if (entry.isEmpty()) {
            return false;
        }
        PrivacyAuditDeadLetterEntry value = entry.get();
        allEntries.removeIf(candidate -> candidate.id() != null && candidate.id() == id);
        String tenantId = resolveTenantId(value.details(), null, null);
        if (tenantId != null) {
            bucket(tenantId).removeIf(candidate -> candidate.id() != null && candidate.id() == id);
        }
        return true;
    }

    @Override
    public boolean deleteById(String tenantId, String tenantDetailKey, long id) {
        if (tenantId == null || tenantId.isBlank()) {
            return deleteById(id);
        }
        return findById(tenantId, tenantDetailKey, id)
                .map(PrivacyAuditDeadLetterEntry::id)
                .filter(java.util.Objects::nonNull)
                .map(this::deleteById)
                .orElse(false);
    }

    @Override
    public int deleteByCriteria(String tenantId, String tenantDetailKey, PrivacyAuditDeadLetterQueryCriteria criteria) {
        List<Long> selectedIds = findByCriteria(tenantId, tenantDetailKey, criteria).stream()
                .map(PrivacyAuditDeadLetterEntry::id)
                .filter(java.util.Objects::nonNull)
                .toList();
        int deleted = 0;
        for (Long id : selectedIds) {
            if (deleteById(id)) {
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public boolean supportsTenantDelete() {
        return true;
    }

    @Override
    public boolean supportsTenantDeleteById() {
        return true;
    }

    @Override
    public PrivacyAuditDeadLetterReplayResult replayByCriteria(
            String tenantId,
            String tenantDetailKey,
            PrivacyAuditDeadLetterQueryCriteria criteria,
            java.util.function.Predicate<PrivacyAuditDeadLetterEntry> replayAction
    ) {
        List<PrivacyAuditDeadLetterEntry> selected = findByCriteria(tenantId, tenantDetailKey, criteria);
        List<Long> replayedIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();
        for (PrivacyAuditDeadLetterEntry entry : selected) {
            if (entry.id() == null) {
                continue;
            }
            if (!replayAction.test(entry)) {
                failedIds.add(entry.id());
                continue;
            }
            if (deleteById(tenantId, tenantDetailKey, entry.id())) {
                replayedIds.add(entry.id());
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

    @Override
    public boolean supportsTenantReplay() {
        return true;
    }

    @Override
    public boolean replayById(
            String tenantId,
            String tenantDetailKey,
            long id,
            java.util.function.Predicate<PrivacyAuditDeadLetterEntry> replayAction
    ) {
        return findById(tenantId, tenantDetailKey, id)
                .filter(entry -> entry.id() != null)
                .map(entry -> replayAction.test(entry)
                        && deleteById(tenantId, tenantDetailKey, entry.id()))
                .orElse(false);
    }

    @Override
    public boolean supportsTenantReplayById() {
        return true;
    }

    void clear() {
        allEntries.clear();
        tenantBuckets.clear();
    }

    private void store(PrivacyAuditDeadLetterEntry entry, String tenantId) {
        PrivacyAuditDeadLetterEntry assigned = assignId(entry);
        allEntries.add(assigned);
        if (tenantId != null && !tenantId.isBlank()) {
            bucket(tenantId).add(assigned);
        }
    }

    private CopyOnWriteArrayList<PrivacyAuditDeadLetterEntry> bucket(String tenantId) {
        return tenantBuckets.computeIfAbsent(tenantId == null ? "__blank__" : tenantId, ignored -> new CopyOnWriteArrayList<>());
    }

    private Stream<PrivacyAuditDeadLetterEntry> filteredStream(
            Stream<PrivacyAuditDeadLetterEntry> stream,
            PrivacyAuditDeadLetterQueryCriteria criteria
    ) {
        return stream
                .filter(entry -> matches(criteria.action(), criteria.actionLike(), entry.action()))
                .filter(entry -> matches(criteria.resourceType(), criteria.resourceTypeLike(), entry.resourceType()))
                .filter(entry -> matches(criteria.resourceId(), criteria.resourceIdLike(), entry.resourceId()))
                .filter(entry -> matches(criteria.actor(), criteria.actorLike(), entry.actor()))
                .filter(entry -> matches(criteria.outcome(), criteria.outcomeLike(), entry.outcome()))
                .filter(entry -> matches(criteria.errorType(), null, entry.errorType()))
                .filter(entry -> criteria.errorMessageLike() == null || (entry.errorMessage() != null && entry.errorMessage().contains(criteria.errorMessageLike())))
                .filter(entry -> criteria.failedFrom() == null || !entry.failedAt().isBefore(criteria.failedFrom()))
                .filter(entry -> criteria.failedTo() == null || !entry.failedAt().isAfter(criteria.failedTo()))
                .filter(entry -> criteria.occurredFrom() == null || !entry.occurredAt().isBefore(criteria.occurredFrom()))
                .filter(entry -> criteria.occurredTo() == null || !entry.occurredAt().isAfter(criteria.occurredTo()));
    }

    private Stream<PrivacyAuditDeadLetterEntry> sort(
            Stream<PrivacyAuditDeadLetterEntry> stream,
            PrivacyAuditSortDirection sortDirection
    ) {
        Comparator<PrivacyAuditDeadLetterEntry> comparator = Comparator.comparing(PrivacyAuditDeadLetterEntry::failedAt)
                .thenComparing(PrivacyAuditDeadLetterEntry::id, Comparator.nullsLast(Long::compareTo));
        if (sortDirection == PrivacyAuditSortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return stream.sorted(comparator);
    }

    private PrivacyAuditDeadLetterStats stats(List<PrivacyAuditDeadLetterEntry> entries) {
        return new PrivacyAuditDeadLetterStats(
                entries.size(),
                aggregate(entries, PrivacyAuditDeadLetterEntry::action),
                aggregate(entries, PrivacyAuditDeadLetterEntry::outcome),
                aggregate(entries, PrivacyAuditDeadLetterEntry::resourceType),
                aggregate(entries, PrivacyAuditDeadLetterEntry::errorType)
        );
    }

    private Map<String, Long> aggregate(List<PrivacyAuditDeadLetterEntry> entries, Function<PrivacyAuditDeadLetterEntry, String> classifier) {
        return entries.stream()
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

    private PrivacyAuditDeadLetterQueryCriteria normalize(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return criteria == null ? PrivacyAuditDeadLetterQueryCriteria.recent(100) : criteria.normalize();
    }

    private PrivacyAuditDeadLetterEntry assignId(PrivacyAuditDeadLetterEntry entry) {
        if (entry.id() != null) {
            return entry;
        }
        return new PrivacyAuditDeadLetterEntry(
                sequence.incrementAndGet(),
                entry.failedAt() == null ? Instant.EPOCH : entry.failedAt(),
                entry.attempts(),
                entry.errorType(),
                entry.errorMessage(),
                entry.occurredAt() == null ? Instant.EPOCH : entry.occurredAt(),
                entry.action(),
                entry.resourceType(),
                entry.resourceId(),
                entry.actor(),
                entry.outcome(),
                entry.details()
        );
    }
}
