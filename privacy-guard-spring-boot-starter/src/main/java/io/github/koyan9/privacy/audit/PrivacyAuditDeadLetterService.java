/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PrivacyAuditDeadLetterService {

    private final PrivacyAuditDeadLetterRepository deadLetterRepository;
    private final PrivacyAuditPublisher replayPublisher;
    private final Set<Long> inFlightReplayIds = ConcurrentHashMap.newKeySet();

    public PrivacyAuditDeadLetterService(
            PrivacyAuditDeadLetterRepository deadLetterRepository,
            PrivacyAuditPublisher replayPublisher
    ) {
        this.deadLetterRepository = Objects.requireNonNull(deadLetterRepository, "deadLetterRepository must not be null");
        this.replayPublisher = Objects.requireNonNull(replayPublisher, "replayPublisher must not be null");
    }

    public List<PrivacyAuditDeadLetterEntry> findAll() {
        return deadLetterRepository.findAll();
    }

    public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
        PrivacyAuditDeadLetterQueryCriteria normalized = criteria == null
                ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                : criteria.normalize();
        return deadLetterRepository.findByCriteria(normalized);
    }

    public Optional<PrivacyAuditDeadLetterEntry> findById(long id) {
        return deadLetterRepository.findById(id);
    }

    public boolean replay(long id) {
        return findById(id)
                .map(entry -> {
                    if (!claim(entry.id())) {
                        return false;
                    }
                    try {
                        replayPublisher.publish(entry.toReplayAuditEvent());
                        return deadLetterRepository.deleteById(id);
                    } finally {
                        release(entry.id());
                    }
                })
                .orElse(false);
    }

    public PrivacyAuditDeadLetterReplayResult replayAll(int limit) {
        return replayByCriteria(PrivacyAuditDeadLetterQueryCriteria.recent(limit));
    }

    public PrivacyAuditDeadLetterReplayResult replayByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return replayEntries(findByCriteria(criteria));
    }

    public PrivacyAuditDeadLetterReplayResult replayEntries(List<PrivacyAuditDeadLetterEntry> entries) {
        List<PrivacyAuditDeadLetterEntry> selected = entries == null ? List.of() : List.copyOf(entries);
        List<Long> replayedIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();

        for (PrivacyAuditDeadLetterEntry entry : selected) {
            if (entry.id() == null) {
                continue;
            }
            if (!replaySelectedEntry(entry)) {
                failedIds.add(entry.id());
                continue;
            }
            if (deadLetterRepository.deleteById(entry.id())) {
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

    public boolean delete(long id) {
        return deadLetterRepository.deleteById(id);
    }

    public int deleteByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
        List<PrivacyAuditDeadLetterEntry> entries = findByCriteria(criteria);
        int deleted = 0;
        for (PrivacyAuditDeadLetterEntry entry : entries) {
            if (entry.id() != null && deadLetterRepository.deleteById(entry.id())) {
                deleted++;
            }
        }
        return deleted;
    }

    boolean replaySelectedEntry(PrivacyAuditDeadLetterEntry entry) {
        if (entry == null || entry.id() == null) {
            return false;
        }
        if (!claim(entry.id())) {
            return false;
        }
        try {
            replayPublisher.publish(entry.toReplayAuditEvent());
            return true;
        } catch (RuntimeException exception) {
            return false;
        } finally {
            release(entry.id());
        }
    }

    private boolean claim(Long id) {
        return id == null || inFlightReplayIds.add(id);
    }

    private void release(Long id) {
        if (id != null) {
            inFlightReplayIds.remove(id);
        }
    }
}
