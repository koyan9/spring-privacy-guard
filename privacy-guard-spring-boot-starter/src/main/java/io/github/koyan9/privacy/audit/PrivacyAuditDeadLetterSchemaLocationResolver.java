/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

public class PrivacyAuditDeadLetterSchemaLocationResolver {

    public static final String GENERIC_SCHEMA = "classpath:META-INF/privacy-guard/privacy-audit-dead-letter-schema-generic.sql";
    public static final String H2_SCHEMA = "classpath:META-INF/privacy-guard/privacy-audit-dead-letter-schema-h2.sql";
    public static final String POSTGRESQL_SCHEMA = "classpath:META-INF/privacy-guard/privacy-audit-dead-letter-schema-postgresql.sql";
    public static final String MYSQL_SCHEMA = "classpath:META-INF/privacy-guard/privacy-audit-dead-letter-schema-mysql.sql";

    public String resolve(PrivacyAuditDeadLetterJdbcProperties jdbcProperties, DataSource dataSource) {
        if (jdbcProperties.getSchemaLocation() != null && !jdbcProperties.getSchemaLocation().isBlank()) {
            return jdbcProperties.getSchemaLocation();
        }

        PrivacyAuditJdbcDialect dialect = jdbcProperties.getDialect();
        if (dialect == null || dialect == PrivacyAuditJdbcDialect.AUTO) {
            dialect = detectDialect(dataSource);
        }
        return switch (dialect) {
            case H2 -> H2_SCHEMA;
            case POSTGRESQL -> POSTGRESQL_SCHEMA;
            case MYSQL -> MYSQL_SCHEMA;
            case GENERIC, AUTO -> GENERIC_SCHEMA;
        };
    }

    PrivacyAuditJdbcDialect detectDialect(DataSource dataSource) {
        if (dataSource == null) {
            return PrivacyAuditJdbcDialect.GENERIC;
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            if (metaData == null || metaData.getDatabaseProductName() == null) {
                return PrivacyAuditJdbcDialect.GENERIC;
            }
            String productName = metaData.getDatabaseProductName().toLowerCase();
            if (productName.contains("postgresql")) {
                return PrivacyAuditJdbcDialect.POSTGRESQL;
            }
            if (productName.contains("mysql")) {
                return PrivacyAuditJdbcDialect.MYSQL;
            }
            if (productName.contains("h2")) {
                return PrivacyAuditJdbcDialect.H2;
            }
        } catch (SQLException exception) {
            return PrivacyAuditJdbcDialect.GENERIC;
        }
        return PrivacyAuditJdbcDialect.GENERIC;
    }
}
