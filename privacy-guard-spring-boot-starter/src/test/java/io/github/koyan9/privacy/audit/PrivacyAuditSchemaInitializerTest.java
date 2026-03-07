/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcOperations;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PrivacyAuditSchemaInitializerTest {

    @Test
    void initializesSchemaFromClasspathResource() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        PrivacyAuditSchemaInitializer initializer = new PrivacyAuditSchemaInitializer(
                jdbcOperations,
                resourceLoader,
                "classpath:META-INF/privacy-guard/privacy-audit-schema-h2.sql",
                "audit_event"
        );

        initializer.initialize();

        verify(jdbcOperations).execute(contains("create table if not exists audit_event"));
    }
}