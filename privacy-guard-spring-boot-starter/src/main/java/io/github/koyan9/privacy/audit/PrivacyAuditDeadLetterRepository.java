/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.util.List;
import java.util.Optional;

@StableSpi
public interface PrivacyAuditDeadLetterRepository {

    void save(PrivacyAuditDeadLetterEntry entry);

    default void saveAll(List<PrivacyAuditDeadLetterEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (PrivacyAuditDeadLetterEntry entry : entries) {
            save(entry);
        }
    }

    default List<PrivacyAuditDeadLetterEntry> findAll() {
        return List.of();
    }

    default List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return findAll();
    }

    default Optional<PrivacyAuditDeadLetterEntry> findById(long id) {
        return findAll().stream().filter(entry -> entry.id() != null && entry.id() == id).findFirst();
    }

    default boolean deleteById(long id) {
        return false;
    }
}
