/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcOperations;

@Configuration(proxyBeanMethods = false)
@Profile("custom-jdbc-tenant")
class CustomJdbcTenantRepositoryConfiguration {

    @Bean
    CustomJdbcTenantAuditRepository customJdbcTenantAuditRepository(
            JdbcOperations jdbcOperations,
            ObjectMapper objectMapper,
            PrivacyGuardProperties properties
    ) {
        return new CustomJdbcTenantAuditRepository(
                jdbcOperations,
                objectMapper,
                properties.getAudit().getJdbc().getTableName(),
                properties.getAudit().getJdbc().getTenantColumnName(),
                properties.getAudit().getJdbc().getTenantDetailKey()
        );
    }

    @Bean
    CustomJdbcTenantDeadLetterRepository customJdbcTenantDeadLetterRepository(
            JdbcOperations jdbcOperations,
            ObjectMapper objectMapper,
            PrivacyGuardProperties properties
    ) {
        return new CustomJdbcTenantDeadLetterRepository(
                jdbcOperations,
                objectMapper,
                properties.getAudit().getDeadLetter().getJdbc().getTableName(),
                properties.getAudit().getDeadLetter().getJdbc().getTenantColumnName(),
                properties.getAudit().getDeadLetter().getJdbc().getTenantDetailKey()
        );
    }
}
