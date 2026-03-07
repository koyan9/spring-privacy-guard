/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InMemoryPrivacyAuditRepository implements PrivacyAuditRepository, PrivacyAuditQueryRepository, PrivacyAuditStatsRepository {

    private final CopyOnWriteArrayList<PrivacyAuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void save(PrivacyAuditEvent event) {
        events.add(event);
    }

    public List<PrivacyAuditEvent> findAll() {
        return List.copyOf(events);
    }

    @Override
    public List<PrivacyAuditEvent> findByCriteria(PrivacyAuditQueryCriteria criteria) {
        PrivacyAuditQueryCriteria normalized = criteria.normalize();
        Comparator<PrivacyAuditEvent> comparator = Comparator.comparing(PrivacyAuditEvent::occurredAt);
        if (normalized.sortDirection() == PrivacyAuditSortDirection.DESC) {
            comparator = comparator.reversed();
        }

        Stream<PrivacyAuditEvent> stream = filteredStream(normalized).sorted(comparator);

        if (normalized.offset() > 0) {
            stream = stream.skip(normalized.offset());
        }
        return stream.limit(normalized.limit()).toList();
    }

    @Override
    public PrivacyAuditQueryStats computeStats(PrivacyAuditQueryCriteria criteria) {
        List<PrivacyAuditEvent> filtered = filteredStream(criteria.normalize()).toList();
        return new PrivacyAuditQueryStats(
                filtered.size(),
                aggregate(filtered, PrivacyAuditEvent::action),
                aggregate(filtered, PrivacyAuditEvent::outcome),
                aggregate(filtered, PrivacyAuditEvent::resourceType)
        );
    }

    public void clear() {
        events.clear();
    }

    private Stream<PrivacyAuditEvent> filteredStream(PrivacyAuditQueryCriteria criteria) {
        return events.stream()
                .filter(event -> matches(criteria.action(), criteria.actionLike(), event.action()))
                .filter(event -> matches(criteria.resourceType(), criteria.resourceTypeLike(), event.resourceType()))
                .filter(event -> matches(criteria.resourceId(), criteria.resourceIdLike(), event.resourceId()))
                .filter(event -> matches(criteria.actor(), criteria.actorLike(), event.actor()))
                .filter(event -> matches(criteria.outcome(), criteria.outcomeLike(), event.outcome()))
                .filter(event -> matchesFrom(criteria, event))
                .filter(event -> matchesTo(criteria, event));
    }

    private Map<String, Long> aggregate(List<PrivacyAuditEvent> events, Function<PrivacyAuditEvent, String> classifier) {
        return events.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()));
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

    private boolean matchesFrom(PrivacyAuditQueryCriteria criteria, PrivacyAuditEvent event) {
        return criteria.occurredFrom() == null || !event.occurredAt().isBefore(criteria.occurredFrom());
    }

    private boolean matchesTo(PrivacyAuditQueryCriteria criteria, PrivacyAuditEvent event) {
        return criteria.occurredTo() == null || !event.occurredAt().isAfter(criteria.occurredTo());
    }
}