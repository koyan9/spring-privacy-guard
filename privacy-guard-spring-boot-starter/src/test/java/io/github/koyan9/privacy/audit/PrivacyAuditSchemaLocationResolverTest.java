/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivacyAuditSchemaLocationResolverTest {

    private final PrivacyAuditSchemaLocationResolver resolver = new PrivacyAuditSchemaLocationResolver();

    @Test
    void usesExplicitSchemaLocationWhenProvided() {
        PrivacyGuardProperties.Jdbc jdbc = new PrivacyGuardProperties.Jdbc();
        jdbc.setSchemaLocation("classpath:custom/privacy-audit.sql");

        assertEquals("classpath:custom/privacy-audit.sql", resolver.resolve(jdbc, null));
    }

    @Test
    void resolvesConfiguredDialectWithoutDatasource() {
        PrivacyGuardProperties.Jdbc jdbc = new PrivacyGuardProperties.Jdbc();
        jdbc.setDialect(PrivacyAuditJdbcDialect.POSTGRESQL);

        assertEquals(PrivacyAuditSchemaLocationResolver.POSTGRESQL_SCHEMA, resolver.resolve(jdbc, null));
    }

    @Test
    void autoDetectsMysqlDialectFromDatasource() throws Exception {
        PrivacyGuardProperties.Jdbc jdbc = new PrivacyGuardProperties.Jdbc();
        jdbc.setDialect(PrivacyAuditJdbcDialect.AUTO);

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");

        assertEquals(PrivacyAuditSchemaLocationResolver.MYSQL_SCHEMA, resolver.resolve(jdbc, dataSource));
    }

    @Test
    void fallsBackToGenericWhenDetectionFails() {
        PrivacyGuardProperties.Jdbc jdbc = new PrivacyGuardProperties.Jdbc();
        jdbc.setDialect(PrivacyAuditJdbcDialect.AUTO);

        assertEquals(PrivacyAuditSchemaLocationResolver.GENERIC_SCHEMA, resolver.resolve(jdbc, null));
    }
}