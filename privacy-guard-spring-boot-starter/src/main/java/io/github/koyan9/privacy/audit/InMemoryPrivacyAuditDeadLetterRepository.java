/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InMemoryPrivacyAuditDeadLetterRepository implements
        PrivacyAuditDeadLetterRepository,
        PrivacyAuditDeadLetterStatsRepository,
        PrivacyTenantAuditDeadLetterReadRepository,
        PrivacyTenantAuditDeadLetterWriteRepository,
        PrivacyTenantAuditDeadLetterDeleteRepository,
        PrivacyTenantAuditDeadLetterReplayRepository {

    private final CopyOnWriteArrayList<PrivacyAuditDeadLetterEntry> entries = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public void save(PrivacyAuditDeadLetterEntry entry) {
        entries.add(assignId(entry));
    }

    @Override
    public void saveAll(List<PrivacyAuditDeadLetterEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        this.entries.addAll(entries.stream().map(this::assignId).toList());
    }

    @Override
    public List<PrivacyAuditDeadLetterEntry> findAll() {
        return sort(entries.stream(), PrivacyAuditSortDirection.DESC).toList();
    }

    @Override
    public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
        PrivacyAuditDeadLetterQueryCriteria normalized = criteria == null
                ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                : criteria.normalize();
        Stream<PrivacyAuditDeadLetterEntry> stream = filteredStream(normalized);
        if (normalized.offset() > 0) {
            stream = stream.skip(normalized.offset());
        }
        return stream.limit(normalized.limit()).toList();
    }

    @Override
    public PrivacyAuditDeadLetterStats computeStats(PrivacyAuditDeadLetterQueryCriteria criteria) {
        List<PrivacyAuditDeadLetterEntry> filtered = filteredStream(criteria == null ? PrivacyAuditDeadLetterQueryCriteria.recent(100) : criteria.normalize()).toList();
        return new PrivacyAuditDeadLetterStats(
                filtered.size(),
                aggregate(filtered, PrivacyAuditDeadLetterEntry::action),
                aggregate(filtered, PrivacyAuditDeadLetterEntry::outcome),
                aggregate(filtered, PrivacyAuditDeadLetterEntry::resourceType),
                aggregate(filtered, PrivacyAuditDeadLetterEntry::errorType)
        );
    }

    @Override
    public List<PrivacyAuditDeadLetterEntry> findByCriteria(
            String tenantId,
            String tenantDetailKey,
            PrivacyAuditDeadLetterQueryCriteria criteria
    ) {
        PrivacyAuditDeadLetterQueryCriteria normalized = criteria == null
                ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                : criteria.normalize();
        Stream<PrivacyAuditDeadLetterEntry> stream = filteredStream(normalized, tenantId, tenantDetailKey);
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
        List<PrivacyAuditDeadLetterEntry> filtered = filteredStream(
                criteria == null ? PrivacyAuditDeadLetterQueryCriteria.recent(100) : criteria.normalize(),
                tenantId,
                tenantDetailKey
        ).toList();
        return new PrivacyAuditDeadLetterStats(
                filtered.size(),
                aggregate(filtered, PrivacyAuditDeadLetterEntry::action),
                aggregate(filtered, PrivacyAuditDeadLetterEntry::outcome),
                aggregate(filtered, PrivacyAuditDeadLetterEntry::resourceType),
                aggregate(filtered, PrivacyAuditDeadLetterEntry::errorType)
        );
    }

    @Override
    public Optional<PrivacyAuditDeadLetterEntry> findById(
            String tenantId,
            String tenantDetailKey,
            long id
    ) {
        if (tenantId == null || tenantId.isBlank() || tenantDetailKey == null || tenantDetailKey.isBlank()) {
            return findById(id);
        }
        return entries.stream()
                .filter(entry -> entry.id() != null && entry.id() == id)
                .filter(entry -> tenantId.equals(entry.details().get(tenantDetailKey)))
                .findFirst();
    }

    @Override
    public boolean supportsTenantRead() {
        return true;
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
        save(materializeTenantDetails(request));
    }

    @Override
    public void saveAllTenantAware(List<PrivacyTenantAuditDeadLetterWriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        this.entries.addAll(requests.stream().map(this::materializeTenantDetails).map(this::assignId).toList());
    }

    @Override
    public Optional<PrivacyAuditDeadLetterEntry> findById(long id) {
        return entries.stream().filter(entry -> entry.id() != null && entry.id() == id).findFirst();
    }

    @Override
    public boolean deleteById(long id) {
        return entries.removeIf(entry -> entry.id() != null && entry.id() == id);
    }

    @Override
    public boolean deleteById(String tenantId, String tenantDetailKey, long id) {
        if (tenantId == null || tenantId.isBlank() || tenantDetailKey == null || tenantDetailKey.isBlank()) {
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
        if (selectedIds.isEmpty()) {
            return 0;
        }
        java.util.Set<Long> selected = java.util.Set.copyOf(selectedIds);
        return (int) entries.stream()
                .filter(entry -> entry.id() != null && selected.contains(entry.id()))
                .map(PrivacyAuditDeadLetterEntry::id)
                .distinct()
                .filter(this::deleteById)
                .count();
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
        List<Long> replayedIds = new java.util.ArrayList<>();
        List<Long> failedIds = new java.util.ArrayList<>();
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

    public void clear() {
        entries.clear();
    }

    private Stream<PrivacyAuditDeadLetterEntry> filteredStream(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return sort(entries.stream()
                .filter(entry -> matches(criteria.action(), criteria.actionLike(), entry.action()))
                .filter(entry -> matches(criteria.resourceType(), criteria.resourceTypeLike(), entry.resourceType()))
                .filter(entry -> matches(criteria.resourceId(), criteria.resourceIdLike(), entry.resourceId()))
                .filter(entry -> matches(criteria.actor(), criteria.actorLike(), entry.actor()))
                .filter(entry -> matches(criteria.outcome(), criteria.outcomeLike(), entry.outcome()))
                .filter(entry -> matches(criteria.errorType(), null, entry.errorType()))
                .filter(entry -> matchesLike(criteria.errorMessageLike(), entry.errorMessage()))
                .filter(entry -> matchesFrom(criteria.failedFrom(), entry.failedAt()))
                .filter(entry -> matchesTo(criteria.failedTo(), entry.failedAt()))
                .filter(entry -> matchesFrom(criteria.occurredFrom(), entry.occurredAt()))
                .filter(entry -> matchesTo(criteria.occurredTo(), entry.occurredAt())), criteria.sortDirection());
    }

    private Stream<PrivacyAuditDeadLetterEntry> filteredStream(
            PrivacyAuditDeadLetterQueryCriteria criteria,
            String tenantId,
            String tenantDetailKey
    ) {
        return filteredStream(criteria)
                .filter(entry -> matchesTenant(entry, tenantId, tenantDetailKey));
    }

    private Stream<PrivacyAuditDeadLetterEntry> sort(Stream<PrivacyAuditDeadLetterEntry> stream, PrivacyAuditSortDirection sortDirection) {
        Comparator<PrivacyAuditDeadLetterEntry> comparator = Comparator.comparing(PrivacyAuditDeadLetterEntry::failedAt)
                .thenComparing(PrivacyAuditDeadLetterEntry::id, Comparator.nullsLast(Long::compareTo));
        if (sortDirection == PrivacyAuditSortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return stream.sorted(comparator);
    }

    private Map<String, Long> aggregate(List<PrivacyAuditDeadLetterEntry> entries, Function<PrivacyAuditDeadLetterEntry, String> classifier) {
        return entries.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()));
    }

    private boolean matches(String expected, String fuzzy, String actual) {
        if (actual == null) {
            return expected == null && fuzzy == null;
        }
        if (expected != null && !expected.equals(actual)) {
            return false;
        }
        if (fuzzy != null && !actual.contains(fuzzy)) {
            return false;
        }
        return true;
    }

    private boolean matchesLike(String fuzzy, String actual) {
        return fuzzy == null || (actual != null && actual.contains(fuzzy));
    }

    private boolean matchesFrom(java.time.Instant from, java.time.Instant actual) {
        return from == null || (actual != null && !actual.isBefore(from));
    }

    private boolean matchesTo(java.time.Instant to, java.time.Instant actual) {
        return to == null || (actual != null && !actual.isAfter(to));
    }

    private boolean matchesTenant(PrivacyAuditDeadLetterEntry entry, String tenantId, String tenantDetailKey) {
        if (tenantId == null || tenantId.isBlank() || tenantDetailKey == null || tenantDetailKey.isBlank()) {
            return true;
        }
        return tenantId.equals(entry.details().get(tenantDetailKey));
    }

    private PrivacyAuditDeadLetterEntry materializeTenantDetails(PrivacyTenantAuditDeadLetterWriteRequest request) {
        PrivacyAuditDeadLetterEntry entry = request.entry();
        String tenantId = request.tenantId();
        String tenantDetailKey = request.tenantDetailKey();
        if (tenantId == null || tenantId.isBlank() || tenantDetailKey == null || tenantDetailKey.isBlank()) {
            return entry;
        }
        if (tenantId.equals(entry.details().get(tenantDetailKey))) {
            return entry;
        }
        Map<String, String> details = new LinkedHashMap<>(entry.details());
        details.putIfAbsent(tenantDetailKey, tenantId);
        return new PrivacyAuditDeadLetterEntry(
                entry.id(),
                entry.failedAt(),
                entry.attempts(),
                entry.errorType(),
                entry.errorMessage(),
                entry.occurredAt(),
                entry.action(),
                entry.resourceType(),
                entry.resourceId(),
                entry.actor(),
                entry.outcome(),
                details
        );
    }

    private PrivacyAuditDeadLetterEntry assignId(PrivacyAuditDeadLetterEntry entry) {
        if (entry.id() != null) {
            return entry;
        }
        return new PrivacyAuditDeadLetterEntry(
                sequence.incrementAndGet(),
                entry.failedAt(),
                entry.attempts(),
                entry.errorType(),
                entry.errorMessage(),
                entry.occurredAt(),
                entry.action(),
                entry.resourceType(),
                entry.resourceId(),
                entry.actor(),
                entry.outcome(),
                entry.details()
        );
    }
}
