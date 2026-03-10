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

class PrivacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolverTest {

    private final PrivacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver resolver =
            new PrivacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver();

    @Test
    void usesExplicitSchemaLocationWhenProvided() {
        PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties jdbc = new PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties();
        jdbc.setSchemaLocation("classpath:custom/replay-store.sql");

        assertEquals("classpath:custom/replay-store.sql", resolver.resolve(jdbc, null));
    }

    @Test
    void resolvesConfiguredDialectWithoutDatasource() {
        PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties jdbc = new PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties();
        jdbc.setDialect(PrivacyAuditJdbcDialect.POSTGRESQL);

        assertEquals(PrivacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver.POSTGRESQL_SCHEMA, resolver.resolve(jdbc, null));
    }

    @Test
    void autoDetectsMysqlDialectFromDatasource() throws Exception {
        PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties jdbc = new PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties();
        jdbc.setDialect(PrivacyAuditJdbcDialect.AUTO);

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");

        assertEquals(PrivacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver.MYSQL_SCHEMA, resolver.resolve(jdbc, dataSource));
    }

    @Test
    void fallsBackToGenericWhenDetectionFails() {
        PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties jdbc = new PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties();
        jdbc.setDialect(PrivacyAuditJdbcDialect.AUTO);

        assertEquals(PrivacyAuditDeadLetterWebhookReplayStoreSchemaLocationResolver.GENERIC_SCHEMA, resolver.resolve(jdbc, null));
    }
}
