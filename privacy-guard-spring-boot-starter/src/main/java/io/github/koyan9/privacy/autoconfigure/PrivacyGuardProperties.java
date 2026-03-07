/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import io.github.koyan9.privacy.audit.PrivacyAuditJdbcDialect;
import io.github.koyan9.privacy.audit.PrivacyAuditRepositoryType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "privacy.guard")
public class PrivacyGuardProperties {

    private boolean enabled = true;
    private String fallbackMaskChar = "*";
    private final Logging logging = new Logging();
    private final Audit audit = new Audit();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFallbackMaskChar() {
        return fallbackMaskChar;
    }

    public void setFallbackMaskChar(String fallbackMaskChar) {
        this.fallbackMaskChar = fallbackMaskChar;
    }

    public Logging getLogging() {
        return logging;
    }

    public Audit getAudit() {
        return audit;
    }

    public static class Logging {

        private boolean enabled = true;
        private final Logback logback = new Logback();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Logback getLogback() {
            return logback;
        }
    }

    public static class Logback {

        private boolean installTurboFilter = false;
        private boolean blockUnsafeMessages = true;

        public boolean isInstallTurboFilter() {
            return installTurboFilter;
        }

        public void setInstallTurboFilter(boolean installTurboFilter) {
            this.installTurboFilter = installTurboFilter;
        }

        public boolean isBlockUnsafeMessages() {
            return blockUnsafeMessages;
        }

        public void setBlockUnsafeMessages(boolean blockUnsafeMessages) {
            this.blockUnsafeMessages = blockUnsafeMessages;
        }
    }

    public static class Audit {

        private boolean enabled = true;
        private boolean logEvents = true;
        private PrivacyAuditRepositoryType repositoryType = PrivacyAuditRepositoryType.NONE;
        private final Jdbc jdbc = new Jdbc();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLogEvents() {
            return logEvents;
        }

        public void setLogEvents(boolean logEvents) {
            this.logEvents = logEvents;
        }

        public PrivacyAuditRepositoryType getRepositoryType() {
            return repositoryType;
        }

        public void setRepositoryType(PrivacyAuditRepositoryType repositoryType) {
            this.repositoryType = repositoryType;
        }

        public Jdbc getJdbc() {
            return jdbc;
        }
    }

    public static class Jdbc {

        private String tableName = "privacy_audit_event";
        private boolean initializeSchema = false;
        private String schemaLocation;
        private PrivacyAuditJdbcDialect dialect = PrivacyAuditJdbcDialect.AUTO;

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
}