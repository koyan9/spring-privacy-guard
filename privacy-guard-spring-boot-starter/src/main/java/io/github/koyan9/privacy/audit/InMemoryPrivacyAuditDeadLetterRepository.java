/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InMemoryPrivacyAuditDeadLetterRepository implements PrivacyAuditDeadLetterRepository, PrivacyAuditDeadLetterStatsRepository {

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
    public Optional<PrivacyAuditDeadLetterEntry> findById(long id) {
        return entries.stream().filter(entry -> entry.id() != null && entry.id() == id).findFirst();
    }

    @Override
    public boolean deleteById(long id) {
        return entries.removeIf(entry -> entry.id() != null && entry.id() == id);
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
