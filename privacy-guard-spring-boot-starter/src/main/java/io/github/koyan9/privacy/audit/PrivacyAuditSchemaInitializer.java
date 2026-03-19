/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcOperations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class PrivacyAuditSchemaInitializer {

    private final JdbcOperations jdbcOperations;
    private final ResourceLoader resourceLoader;
    private final String schemaLocation;
    private final String tableName;
    private final String tenantColumnName;

    public PrivacyAuditSchemaInitializer(
            JdbcOperations jdbcOperations,
            ResourceLoader resourceLoader,
            String schemaLocation,
            String tableName
    ) {
        this(jdbcOperations, resourceLoader, schemaLocation, tableName, null);
    }

    public PrivacyAuditSchemaInitializer(
            JdbcOperations jdbcOperations,
            ResourceLoader resourceLoader,
            String schemaLocation,
            String tableName,
            String tenantColumnName
    ) {
        this.jdbcOperations = jdbcOperations;
        this.resourceLoader = resourceLoader;
        this.schemaLocation = schemaLocation;
        this.tableName = tableName;
        this.tenantColumnName = normalizeTenantColumnName(tenantColumnName);
    }

    public void initialize() {
        String script = loadScript();
        String resolvedScript = script
                .replace("${tableName}", tableName)
                .replace("${tenantColumnName}", tenantColumnName);
        Arrays.stream(resolvedScript.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isBlank())
                .forEach(jdbcOperations::execute);
    }

    private String loadScript() {
        Resource resource = resourceLoader.getResource(schemaLocation);
        if (!resource.exists()) {
            throw new IllegalStateException("Privacy audit schema resource not found: " + schemaLocation);
        }
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read privacy audit schema resource: " + schemaLocation, exception);
        }
    }

    private String normalizeTenantColumnName(String value) {
        if (value == null || value.isBlank()) {
            return "tenant_id";
        }
        return value.trim();
    }
}
