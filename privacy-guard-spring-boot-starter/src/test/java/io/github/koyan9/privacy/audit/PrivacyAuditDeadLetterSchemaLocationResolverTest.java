/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivacyAuditDeadLetterSchemaLocationResolverTest {

    private final PrivacyAuditDeadLetterSchemaLocationResolver resolver = new PrivacyAuditDeadLetterSchemaLocationResolver();

    @Test
    void usesExplicitSchemaLocationWhenProvided() {
        PrivacyAuditDeadLetterJdbcProperties jdbc = new PrivacyAuditDeadLetterJdbcProperties();
        jdbc.setSchemaLocation("classpath:custom/dead-letter.sql");

        assertEquals("classpath:custom/dead-letter.sql", resolver.resolve(jdbc, null));
    }

    @Test
    void resolvesConfiguredDialectWithoutDatasource() {
        PrivacyAuditDeadLetterJdbcProperties jdbc = new PrivacyAuditDeadLetterJdbcProperties();
        jdbc.setDialect(PrivacyAuditJdbcDialect.POSTGRESQL);

        assertEquals(PrivacyAuditDeadLetterSchemaLocationResolver.POSTGRESQL_SCHEMA, resolver.resolve(jdbc, null));
    }

    @Test
    void autoDetectsMysqlDialectFromDatasource() throws Exception {
        PrivacyAuditDeadLetterJdbcProperties jdbc = new PrivacyAuditDeadLetterJdbcProperties();
        jdbc.setDialect(PrivacyAuditJdbcDialect.AUTO);

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");

        assertEquals(PrivacyAuditDeadLetterSchemaLocationResolver.MYSQL_SCHEMA, resolver.resolve(jdbc, dataSource));
    }

    @Test
    void fallsBackToGenericWhenDetectionFails() {
        PrivacyAuditDeadLetterJdbcProperties jdbc = new PrivacyAuditDeadLetterJdbcProperties();
        jdbc.setDialect(PrivacyAuditJdbcDialect.AUTO);

        assertEquals(PrivacyAuditDeadLetterSchemaLocationResolver.GENERIC_SCHEMA, resolver.resolve(jdbc, null));
    }
}
