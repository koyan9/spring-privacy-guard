/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.JdbcPrivacyAuditRepository;
import org.springframework.jdbc.core.JdbcOperations;

class CustomJdbcTenantAuditRepository extends JdbcPrivacyAuditRepository {

    CustomJdbcTenantAuditRepository(
            JdbcOperations jdbcOperations,
            ObjectMapper objectMapper,
            String tableName,
            String tenantColumnName,
            String tenantDetailKey
    ) {
        super(jdbcOperations, objectMapper, tableName, tenantColumnName, tenantDetailKey);
    }
}
