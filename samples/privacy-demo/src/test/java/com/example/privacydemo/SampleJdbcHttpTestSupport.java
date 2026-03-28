/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import org.springframework.jdbc.core.JdbcOperations;

abstract class SampleJdbcHttpTestSupport extends SampleReceiverRouteTestSupport {

    protected static final String JDBC_AUDIT_TABLE = "privacy_audit_event_jdbc_demo";
    protected static final String JDBC_DEAD_LETTER_TABLE = "privacy_audit_dead_letter_jdbc_demo";
    protected static final String JDBC_REPLAY_TABLE = "privacy_audit_webhook_replay_store_jdbc_demo";

    protected static final String POSTGRES_SAMPLE_AUDIT_TABLE = "privacy_audit_event_postgres_demo";
    protected static final String POSTGRES_SAMPLE_DEAD_LETTER_TABLE = "privacy_audit_dead_letter_postgres_demo";

    protected void resetJdbcState(
            JdbcOperations jdbcOperations,
            PrivacyAuditDeadLetterWebhookReplayStore replayStore,
            String auditTable,
            String deadLetterTable,
            String replayTable
    ) {
        jdbcOperations.update("delete from " + auditTable);
        jdbcOperations.update("delete from " + deadLetterTable);
        if (replayTable != null && !replayTable.isBlank()) {
            jdbcOperations.update("delete from " + replayTable);
        }
        replayStore.clear();
    }

    protected String queryAuditDetailsJson(
            JdbcOperations jdbcOperations,
            String auditTable,
            String action,
            String tenantId
    ) {
        return jdbcOperations.queryForObject(
                "select details_json from " + auditTable + " where action = ? and tenant_key = ?",
                String.class,
                action,
                tenantId
        );
    }

    protected String queryDeadLetterDetailsJsonByTenant(
            JdbcOperations jdbcOperations,
            String deadLetterTable,
            String tenantId
    ) {
        return jdbcOperations.queryForObject(
                "select details_json from " + deadLetterTable + " where tenant_key = ?",
                String.class,
                tenantId
        );
    }
}
