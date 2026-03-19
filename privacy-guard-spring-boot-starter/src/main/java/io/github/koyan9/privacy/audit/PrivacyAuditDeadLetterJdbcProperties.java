/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

public class PrivacyAuditDeadLetterJdbcProperties {

    private String tableName = "privacy_audit_dead_letter";
    private String tenantColumnName;
    private String tenantDetailKey = "tenantId";
    private boolean initializeSchema = false;
    private String schemaLocation;
    private PrivacyAuditJdbcDialect dialect = PrivacyAuditJdbcDialect.AUTO;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTenantColumnName() {
        return tenantColumnName;
    }

    public void setTenantColumnName(String tenantColumnName) {
        this.tenantColumnName = tenantColumnName;
    }

    public String getTenantDetailKey() {
        return tenantDetailKey;
    }

    public void setTenantDetailKey(String tenantDetailKey) {
        this.tenantDetailKey = tenantDetailKey;
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
