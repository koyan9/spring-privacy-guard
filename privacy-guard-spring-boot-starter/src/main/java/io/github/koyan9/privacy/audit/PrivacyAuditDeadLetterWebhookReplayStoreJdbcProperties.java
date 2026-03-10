/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

public class PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties {

    private boolean enabled = false;
    private String tableName = "privacy_audit_webhook_replay_store";
    private boolean initializeSchema = false;
    private String schemaLocation;
    private PrivacyAuditJdbcDialect dialect = PrivacyAuditJdbcDialect.AUTO;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public boolean isInitializeSchema() {
        return initializeSchema;
    }

    public void setInitializeSchema(boolean initializeSchema) {
        this.initializeSchema = initializeSchema;
    }

    public String getSchemaLocation() {
        return schemaLocation;
    }

    public void setSchemaLocation(String schemaLocation) {
        this.schemaLocation = schemaLocation;
    }

    public PrivacyAuditJdbcDialect getDialect() {
        return dialect;
    }

    public void setDialect(PrivacyAuditJdbcDialect dialect) {
        this.dialect = dialect;
    }
}
