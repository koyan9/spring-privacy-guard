/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterJdbcProperties;
import io.github.koyan9.privacy.audit.PrivacyAuditJdbcDialect;
import io.github.koyan9.privacy.audit.PrivacyAuditRepositoryType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "privacy.guard")
public class PrivacyGuardProperties {

    private boolean enabled = true;
    private String fallbackMaskChar = "*";
    private final Logging logging = new Logging();
    private final Audit audit = new Audit();
    private final Masking masking = new Masking();
    private final Tenant tenant = new Tenant();

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

    public Masking getMasking() {
        return masking;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public static class Logging {

        private boolean enabled = true;
        private final Logback logback = new Logback();
        private final Mdc mdc = new Mdc();
        private final Structured structured = new Structured();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Logback getLogback() {
            return logback;
        }

        public Mdc getMdc() {
            return mdc;
        }

        public Structured getStructured() {
            return structured;
        }
    }

    public static class Masking {

        private final Text text = new Text();

        public Text getText() {
            return text;
        }

        public static class Text {

            private String emailPattern;
            private String phonePattern;
            private String idCardPattern;
            private java.util.List<AdditionalPattern> additionalPatterns = new java.util.ArrayList<>();

            public String getEmailPattern() {
                return emailPattern;
            }

            public void setEmailPattern(String emailPattern) {
                this.emailPattern = emailPattern;
            }

            public String getPhonePattern() {
                return phonePattern;
            }

            public void setPhonePattern(String phonePattern) {
                this.phonePattern = phonePattern;
            }

            public String getIdCardPattern() {
                return idCardPattern;
            }

            public void setIdCardPattern(String idCardPattern) {
                this.idCardPattern = idCardPattern;
            }

            public java.util.List<AdditionalPattern> getAdditionalPatterns() {
                return additionalPatterns;
            }

            public void setAdditionalPatterns(java.util.List<AdditionalPattern> additionalPatterns) {
                this.additionalPatterns = additionalPatterns == null ? new java.util.ArrayList<>() : additionalPatterns;
            }
        }

        public static class AdditionalPattern {

            private io.github.koyan9.privacy.core.SensitiveType type = io.github.koyan9.privacy.core.SensitiveType.GENERIC;
            private String pattern;

            public io.github.koyan9.privacy.core.SensitiveType getType() {
                return type;
            }

            public void setType(io.github.koyan9.privacy.core.SensitiveType type) {
                this.type = type == null ? io.github.koyan9.privacy.core.SensitiveType.GENERIC : type;
            }

            public String getPattern() {
                return pattern;
            }

            public void setPattern(String pattern) {
                this.pattern = pattern;
            }
        }
    }

    public static class Tenant {

        private boolean enabled = false;
        private String headerName = "X-Privacy-Tenant";
        private String defaultTenant;
        private java.util.Map<String, TenantPolicy> policies = new java.util.LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getDefaultTenant() {
            return defaultTenant;
        }

        public void setDefaultTenant(String defaultTenant) {
            this.defaultTenant = defaultTenant;
        }

        public java.util.Map<String, TenantPolicy> getPolicies() {
            return policies;
        }

        public void setPolicies(java.util.Map<String, TenantPolicy> policies) {
            this.policies = policies == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(policies);
        }
    }

    public static class TenantPolicy {

        private String fallbackMaskChar;
        private final Masking.Text text = new Masking.Text();
        private final TenantAudit audit = new TenantAudit();

        public String getFallbackMaskChar() {
            return fallbackMaskChar;
        }

        public void setFallbackMaskChar(String fallbackMaskChar) {
            this.fallbackMaskChar = fallbackMaskChar;
        }

        public Masking.Text getText() {
            return text;
        }

        public TenantAudit getAudit() {
            return audit;
        }
    }

    public static class TenantAudit {

        private java.util.List<String> includeDetailKeys = new java.util.ArrayList<>();
        private java.util.List<String> excludeDetailKeys = new java.util.ArrayList<>();
        private boolean attachTenantId = false;
        private String tenantDetailKey = "tenantId";

        public java.util.List<String> getIncludeDetailKeys() {
            return includeDetailKeys;
        }

        public void setIncludeDetailKeys(java.util.List<String> includeDetailKeys) {
            this.includeDetailKeys = includeDetailKeys == null ? new java.util.ArrayList<>() : includeDetailKeys;
        }

        public java.util.List<String> getExcludeDetailKeys() {
            return excludeDetailKeys;
        }

        public void setExcludeDetailKeys(java.util.List<String> excludeDetailKeys) {
            this.excludeDetailKeys = excludeDetailKeys == null ? new java.util.ArrayList<>() : excludeDetailKeys;
        }

        public boolean isAttachTenantId() {
            return attachTenantId;
        }

        public void setAttachTenantId(boolean attachTenantId) {
            this.attachTenantId = attachTenantId;
        }

        public String getTenantDetailKey() {
            return tenantDetailKey;
        }

        public void setTenantDetailKey(String tenantDetailKey) {
            this.tenantDetailKey = tenantDetailKey;
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

    public static class Mdc {

        private boolean enabled = false;
        private java.util.List<String> includeKeys = new java.util.ArrayList<>();
        private java.util.List<String> excludeKeys = new java.util.ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public java.util.List<String> getIncludeKeys() {
            return includeKeys;
        }

        public void setIncludeKeys(java.util.List<String> includeKeys) {
            this.includeKeys = includeKeys == null ? new java.util.ArrayList<>() : includeKeys;
        }

        public java.util.List<String> getExcludeKeys() {
            return excludeKeys;
        }

        public void setExcludeKeys(java.util.List<String> excludeKeys) {
            this.excludeKeys = excludeKeys == null ? new java.util.ArrayList<>() : excludeKeys;
        }
    }

    public static class Structured {

        private boolean enabled = false;
        private java.util.List<String> includeKeys = new java.util.ArrayList<>();
        private java.util.List<String> excludeKeys = new java.util.ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public java.util.List<String> getIncludeKeys() {
            return includeKeys;
        }

        public void setIncludeKeys(java.util.List<String> includeKeys) {
            this.includeKeys = includeKeys == null ? new java.util.ArrayList<>() : includeKeys;
        }

        public java.util.List<String> getExcludeKeys() {
            return excludeKeys;
        }

        public void setExcludeKeys(java.util.List<String> excludeKeys) {
            this.excludeKeys = excludeKeys == null ? new java.util.ArrayList<>() : excludeKeys;
        }
    }

    public static class Audit {

        private boolean enabled = true;
        private boolean logEvents = true;
        private PrivacyAuditRepositoryType repositoryType = PrivacyAuditRepositoryType.NONE;
        private final Async async = new Async();
        private final Batch batch = new Batch();
        private final Retry retry = new Retry();
        private final DeadLetter deadLetter = new DeadLetter();
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

        public Async getAsync() {
            return async;
        }

        public Batch getBatch() {
            return batch;
        }

        public Retry getRetry() {
            return retry;
        }

        public DeadLetter getDeadLetter() {
            return deadLetter;
        }

        public Jdbc getJdbc() {
            return jdbc;
        }
    }

    public static class Async {

        private boolean enabled = false;
        private String threadNamePrefix = "privacy-audit-";
        private int threadPoolSize = 1;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }

        public int getThreadPoolSize() {
            return threadPoolSize;
        }

        public void setThreadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
        }
    }

    public static class Batch {

        private boolean enabled = false;
        private int size = 50;
        private Duration flushInterval = Duration.ofMillis(500);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public Duration getFlushInterval() {
            return flushInterval;
        }

        public void setFlushInterval(Duration flushInterval) {
            this.flushInterval = flushInterval;
        }
    }

    public static class Retry {

        private int maxAttempts = 3;
        private Duration backoff = Duration.ofMillis(100);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBackoff() {
            return backoff;
        }

        public void setBackoff(Duration backoff) {
            this.backoff = backoff;
        }
    }

    public static class DeadLetter {

        private PrivacyAuditRepositoryType repositoryType = PrivacyAuditRepositoryType.NONE;
        private final PrivacyAuditDeadLetterJdbcProperties jdbc = new PrivacyAuditDeadLetterJdbcProperties();
        private final Observability observability = new Observability();

        public PrivacyAuditRepositoryType getRepositoryType() {
            return repositoryType;
        }

        public void setRepositoryType(PrivacyAuditRepositoryType repositoryType) {
            this.repositoryType = repositoryType;
        }

        public PrivacyAuditDeadLetterJdbcProperties getJdbc() {
            return jdbc;
        }

        public Observability getObservability() {
            return observability;
        }
    }

    public static class Observability {

        private final Health health = new Health();
        private final Metrics metrics = new Metrics();
        private final Alert alert = new Alert();

        public Health getHealth() {
            return health;
        }

        public Metrics getMetrics() {
            return metrics;
        }

        public Alert getAlert() {
            return alert;
        }
    }

    public static class Health {

        private boolean enabled = true;
        private long warningThreshold = 1;
        private long downThreshold = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getWarningThreshold() {
            return warningThreshold;
        }

        public void setWarningThreshold(long warningThreshold) {
            this.warningThreshold = warningThreshold;
        }

        public long getDownThreshold() {
            return downThreshold;
        }

        public void setDownThreshold(long downThreshold) {
            this.downThreshold = downThreshold;
        }
    }

    public static class Metrics {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Alert {

        private boolean enabled = false;
        private Duration checkInterval = Duration.ofSeconds(30);
        private boolean notifyOnRecovery = true;
        private final AlertLogging logging = new AlertLogging();
        private final AlertWebhook webhook = new AlertWebhook();
        private final AlertEmail email = new AlertEmail();
        private final AlertReceiver receiver = new AlertReceiver();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getCheckInterval() {
            return checkInterval;
        }

        public void setCheckInterval(Duration checkInterval) {
            this.checkInterval = checkInterval;
        }

        public boolean isNotifyOnRecovery() {
            return notifyOnRecovery;
        }

        public void setNotifyOnRecovery(boolean notifyOnRecovery) {
            this.notifyOnRecovery = notifyOnRecovery;
        }

        public AlertLogging getLogging() {
            return logging;
        }

        public AlertWebhook getWebhook() {
            return webhook;
        }

        public AlertEmail getEmail() {
            return email;
        }

        public AlertReceiver getReceiver() {
            return receiver;
        }
    }

    public static class AlertLogging {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class AlertReceiver {

        private final AlertReceiverFilter filter = new AlertReceiverFilter();
        private final AlertReceiverInterceptor interceptor = new AlertReceiverInterceptor();
        private final AlertReceiverMetrics metrics = new AlertReceiverMetrics();
        private final AlertReceiverReplayStore replayStore = new AlertReceiverReplayStore();
        private final AlertReceiverVerification verification = new AlertReceiverVerification();

        public AlertReceiverFilter getFilter() {
            return filter;
        }

        public AlertReceiverInterceptor getInterceptor() {
            return interceptor;
        }

        public AlertReceiverMetrics getMetrics() {
            return metrics;
        }

        public AlertReceiverReplayStore getReplayStore() {
            return replayStore;
        }

        public AlertReceiverVerification getVerification() {
            return verification;
        }
    }

    public static class AlertReceiverFilter {

        private boolean enabled = false;
        private String pathPattern = "/privacy-audit-alerts/**";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }
    }
    public static class AlertReceiverInterceptor {

        private boolean enabled = false;
        private String pathPattern = "/privacy-audit-alerts/**";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }
    }
    public static class AlertReceiverMetrics {

        private boolean enabled = true;
        private Duration expiringSoonWindow = Duration.ofMinutes(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getExpiringSoonWindow() {
            return expiringSoonWindow;
        }

        public void setExpiringSoonWindow(Duration expiringSoonWindow) {
            this.expiringSoonWindow = expiringSoonWindow;
        }
    }

    public static class AlertReceiverReplayStore {

        private final io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties jdbc =
                new io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties();
        private final io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreRedisProperties redis =
                new io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreRedisProperties();
        private final AlertReceiverReplayStoreFile file = new AlertReceiverReplayStoreFile();

        public io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreJdbcProperties getJdbc() {
            return jdbc;
        }

        public io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStoreRedisProperties getRedis() {
            return redis;
        }

        public AlertReceiverReplayStoreFile getFile() {
            return file;
        }
    }

    public static class AlertReceiverReplayStoreFile {

        private boolean enabled = false;
        private String path = "privacy-audit-webhook-replay-store.json";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class AlertReceiverVerification {

        private boolean enabled = false;
        private String bearerToken;
        private String signatureSecret;
        private String signatureAlgorithm = "HmacSHA256";
        private String signatureHeader = "X-Privacy-Alert-Signature";
        private String timestampHeader = "X-Privacy-Alert-Timestamp";
        private String nonceHeader = "X-Privacy-Alert-Nonce";
        private Duration maxSkew = Duration.ofMinutes(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBearerToken() {
            return bearerToken;
        }

        public void setBearerToken(String bearerToken) {
            this.bearerToken = bearerToken;
        }

        public String getSignatureSecret() {
            return signatureSecret;
        }

        public void setSignatureSecret(String signatureSecret) {
            this.signatureSecret = signatureSecret;
        }

        public String getSignatureAlgorithm() {
            return signatureAlgorithm;
        }

        public void setSignatureAlgorithm(String signatureAlgorithm) {
            this.signatureAlgorithm = signatureAlgorithm;
        }

        public String getSignatureHeader() {
            return signatureHeader;
        }

        public void setSignatureHeader(String signatureHeader) {
            this.signatureHeader = signatureHeader;
        }

        public String getTimestampHeader() {
            return timestampHeader;
        }

        public void setTimestampHeader(String timestampHeader) {
            this.timestampHeader = timestampHeader;
        }

        public String getNonceHeader() {
            return nonceHeader;
        }

        public void setNonceHeader(String nonceHeader) {
            this.nonceHeader = nonceHeader;
        }

        public Duration getMaxSkew() {
            return maxSkew;
        }

        public void setMaxSkew(Duration maxSkew) {
            this.maxSkew = maxSkew;
        }
    }
    public static class AlertWebhook {

        private String url;
        private String bearerToken;
        private String signatureSecret;
        private String signatureAlgorithm = "HmacSHA256";
        private String signatureHeader = "X-Privacy-Alert-Signature";
        private String timestampHeader = "X-Privacy-Alert-Timestamp";
        private String nonceHeader = "X-Privacy-Alert-Nonce";
        private int maxAttempts = 3;
        private Duration backoff = Duration.ofMillis(200);
        private BackoffPolicy backoffPolicy = BackoffPolicy.FIXED;
        private Duration maxBackoff;
        private double jitter = 0.0d;
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getBearerToken() {
            return bearerToken;
        }

        public void setBearerToken(String bearerToken) {
            this.bearerToken = bearerToken;
        }

        public String getSignatureSecret() {
            return signatureSecret;
        }

        public void setSignatureSecret(String signatureSecret) {
            this.signatureSecret = signatureSecret;
        }

        public String getSignatureAlgorithm() {
            return signatureAlgorithm;
        }

        public void setSignatureAlgorithm(String signatureAlgorithm) {
            this.signatureAlgorithm = signatureAlgorithm;
        }

        public String getSignatureHeader() {
            return signatureHeader;
        }

        public void setSignatureHeader(String signatureHeader) {
            this.signatureHeader = signatureHeader;
        }

        public String getTimestampHeader() {
            return timestampHeader;
        }

        public void setTimestampHeader(String timestampHeader) {
            this.timestampHeader = timestampHeader;
        }

        public String getNonceHeader() {
            return nonceHeader;
        }

        public void setNonceHeader(String nonceHeader) {
            this.nonceHeader = nonceHeader;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBackoff() {
            return backoff;
        }

        public void setBackoff(Duration backoff) {
            this.backoff = backoff;
        }

        public BackoffPolicy getBackoffPolicy() {
            return backoffPolicy;
        }

        public void setBackoffPolicy(BackoffPolicy backoffPolicy) {
            this.backoffPolicy = backoffPolicy == null ? BackoffPolicy.FIXED : backoffPolicy;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }

        public double getJitter() {
            return jitter;
        }

        public void setJitter(double jitter) {
            this.jitter = jitter;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public enum BackoffPolicy {
            FIXED,
            EXPONENTIAL
        }
    }

    public static class AlertEmail {

        private String from;
        private String to;
        private String subjectPrefix = "[spring-privacy-guard]";

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public String getSubjectPrefix() {
            return subjectPrefix;
        }

        public void setSubjectPrefix(String subjectPrefix) {
            this.subjectPrefix = subjectPrefix;
        }
    }

    public static class Jdbc {

        private String tableName = "privacy_audit_event";
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
}
