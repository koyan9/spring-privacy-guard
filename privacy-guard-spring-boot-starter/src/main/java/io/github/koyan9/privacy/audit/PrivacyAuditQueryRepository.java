/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.util.List;

public interface PrivacyAuditQueryRepository {

    List<PrivacyAuditEvent> findByCriteria(PrivacyAuditQueryCriteria criteria);
}