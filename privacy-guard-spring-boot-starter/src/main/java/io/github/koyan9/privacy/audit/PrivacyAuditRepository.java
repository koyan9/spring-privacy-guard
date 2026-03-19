/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.util.List;

@StableSpi
public interface PrivacyAuditRepository {

    void save(PrivacyAuditEvent event);

    default void saveAll(List<PrivacyAuditEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (PrivacyAuditEvent event : events) {
            save(event);
        }
    }
}
