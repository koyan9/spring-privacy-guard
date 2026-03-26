/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.util.function.Predicate;

@StableSpi
public interface PrivacyTenantAuditDeadLetterReplayRepository {

    PrivacyAuditDeadLetterReplayResult replayByCriteria(
            String tenantId,
            String tenantDetailKey,
            PrivacyAuditDeadLetterQueryCriteria criteria,
            Predicate<PrivacyAuditDeadLetterEntry> replayAction
    );
}
