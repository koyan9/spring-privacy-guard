# spring-privacy-guard

[English](README.md) | [简体中文](README.zh-CN.md)

[![CI](https://github.com/koyan9/spring-privacy-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/koyan9/spring-privacy-guard/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/koyan9/spring-privacy-guard?display_name=tag)](https://github.com/koyan9/spring-privacy-guard/releases/latest)
[![Changelog](https://img.shields.io/badge/Changelog-0.4.0-0f766e)](CHANGELOG.md)
[![Release Notes](https://img.shields.io/badge/Release%20Notes-v0.4.0-1d4ed8)](docs/releases/RELEASE_NOTES_v0.4.0.md)
[![Security](https://img.shields.io/badge/Security-Policy-7f1d1d)](SECURITY.md)
[![Support](https://img.shields.io/badge/Support-Guide-1f2937)](SUPPORT.md)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-orange)](#quick-start)
[![Docs Index](https://img.shields.io/badge/Docs-Index-6f42c1)](docs/INDEX.md)
[![中文文档](https://img.shields.io/badge/Docs-zh--CN-green)](README.zh-CN.md)

A Spring Boot starter for masking sensitive data, sanitizing logs, tracing privacy operations, and operating dead-letter workflows securely.

## Development Status

- Latest published release: `v0.4.0`
- Current next-work focus after `v0.4.0`: deeper repository-native tenant isolation beyond the current built-in SPI coverage, broader production rollout guides, and the next release cycle setup
- Newly completed in the current development cycle: tenant-native dead-letter replay for built-in `IN_MEMORY` / `JDBC` repositories, stable tenant logging policy resolution, tenant-scoped dead-letter observability policy overrides, tenant operational telemetry for alerting and receiver routes, and PostgreSQL + Redis multi-instance sample recipes
- Best sources for current progress: `CHANGELOG.md` for completed/unreleased work, `docs/ROADMAP.md` for next priorities, and `docs/INDEX.md` for the doc map

## Modules

- `privacy-guard-core/`: masking annotations, built-in sensitive types, text sanitization, and masking SPI contracts.
- `privacy-guard-spring-boot-starter/`: Spring Boot auto-configuration, Jackson integration, Logback integration, audit storage, dead-letter handling, webhook/email alerts, and receiver verification support.
- `samples/privacy-demo/`: runnable sample covering masking, auditing, dead-letter operations, signed alert delivery, and verified receiver flows.

## Features

- Field masking with `@SensitiveData` and custom `MaskingStrategy`
- Tenant-aware privacy policies with per-tenant fallback mask characters, text rules, and Logback MDC/structured key selection
- Safe JSON serialization and free-text log sanitization
- Audit recording, querying, pagination, sorting, and statistics
- `IN_MEMORY` and `JDBC` repositories for audits and dead letters
- Async publishing, batching, retries, dead letters, replay, and export/import
- Actuator health checks and Micrometer metrics for backlog and alert delivery
- Signed webhook/email alerts plus reusable webhook verifier, replay store, filter, and interceptor support

## Quick Start

Requirements:

- Java `17+`
- Spring Boot `3.3.x`

Add the starter:

```xml
<dependency>
    <groupId>io.github.koyan9</groupId>
    <artifactId>spring-privacy-guard-spring-boot-starter</artifactId>
    <version>0.4.0</version>
</dependency>
```

Example model:

```java
public record PatientView(
        @SensitiveData(type = SensitiveType.NAME) String patientName,
        @SensitiveData(type = SensitiveType.PHONE) String phone,
        @SensitiveData(type = SensitiveType.ID_CARD) String idCard,
        @SensitiveData(type = SensitiveType.EMAIL) String email
) {
}
```

Minimal config:

```yaml
privacy:
  guard:
    fallback-mask-char: "*"
    audit:
      repository-type: IN_MEMORY
```

Recommended quick-start paths:

- Basic starter integration: start with the model and minimal config above, then review `## Key Config`
- Multi-tenant integration: read `docs/MULTI_TENANT_GUIDE.md`
- Tenant observability, health, and alerting: read `docs/TENANT_OBSERVABILITY_GUIDE.md`
- Runnable demo, receiver verification, and multi-instance rehearsal: read `samples/privacy-demo/README.md`
- Current unreleased progress and what is landing next: read `CHANGELOG.md` and `docs/ROADMAP.md`

## Key Config

| Property | Default | Notes |
| --- | --- | --- |
| `privacy.guard.enabled` | `true` | Master switch for the starter. |
| `privacy.guard.logging.enabled` | `true` | Enables privacy-safe logging helpers. |
| `privacy.guard.logging.mdc.enabled` | `false` | Enables masking MDC values for Logback events. |
| `privacy.guard.logging.mdc.include-keys` | empty | Optional allowlist of MDC keys to sanitize. |
| `privacy.guard.logging.mdc.exclude-keys` | empty | Optional denylist of MDC keys to skip. |
| `privacy.guard.logging.structured.enabled` | `false` | Enables masking structured logging fields. |
| `privacy.guard.logging.structured.include-keys` | empty | Optional allowlist of structured keys to sanitize. |
| `privacy.guard.logging.structured.exclude-keys` | empty | Optional denylist of structured keys to skip. |
| `privacy.guard.tenant.enabled` | `false` | Enables tenant-aware privacy policy resolution and request-header tenant propagation. |
| `privacy.guard.tenant.header-name` | `X-Privacy-Tenant` | Request header used to populate the current tenant in servlet applications. |
| `privacy.guard.tenant.default-tenant` | empty | Fallback tenant identifier when no tenant header is present. |
| `privacy.guard.masking.text.email-pattern` | empty | Override email regex used by text masking. |
| `privacy.guard.masking.text.phone-pattern` | empty | Override phone regex used by text masking. |
| `privacy.guard.masking.text.id-card-pattern` | empty | Override ID card regex used by text masking. |
| `privacy.guard.masking.text.additional-patterns` | empty | Extra text masking rules mapped to a SensitiveType. |
| `privacy.guard.audit.enabled` | `true` | Enables audit services and publishers. |
| `privacy.guard.audit.repository-type` | `NONE` | `NONE`, `IN_MEMORY`, or `JDBC`. |
| `privacy.guard.audit.async.thread-pool-size` | `1` | Thread pool size for async/batch audit executor. |
| `privacy.guard.audit.dead-letter.repository-type` | `NONE` | Dead-letter storage type. |
| `privacy.guard.audit.dead-letter.observability.health.tenant-enabled` | `false` | Exposes a tenant-aware dead-letter health indicator when Actuator is present. |
| `privacy.guard.audit.dead-letter.observability.health.tenant-ids` | empty | Optional tenant IDs included in the tenant-aware dead-letter health indicator. |
| `privacy.guard.audit.dead-letter.observability.health.warning-threshold` | `1` | Backlog warning threshold. |
| `privacy.guard.audit.dead-letter.observability.health.down-threshold` | `100` | Backlog down threshold. |
| `privacy.guard.audit.dead-letter.observability.alert.enabled` | `false` | Enables dead-letter alerting. |
| `privacy.guard.audit.dead-letter.observability.alert.tenant.enabled` | `false` | Enables tenant-scoped dead-letter alert monitoring for the configured tenant list. |
| `privacy.guard.audit.dead-letter.observability.alert.tenant.tenant-ids` | empty | Optional allowlist of tenants monitored by tenant-scoped dead-letter alert routing. |
| `privacy.guard.audit.dead-letter.observability.alert.tenant.routes` | empty | Optional tenant-to-target route overrides for webhook/email dead-letter alerts. |
| `privacy.guard.audit.dead-letter.observability.alert.webhook.url` | empty | Enables built-in webhook alerts. |
| `privacy.guard.audit.dead-letter.observability.alert.webhook.backoff-policy` | `FIXED` | Retry backoff policy for webhook alerts. |
| `privacy.guard.audit.dead-letter.observability.alert.webhook.max-backoff` | `backoff*10 (max 30s)` | Max delay for webhook retry backoff when exponential policy is used. |
| `privacy.guard.audit.dead-letter.observability.alert.webhook.jitter` | `0` | Jitter factor (0-1) applied to webhook retry delays. |
| `privacy.guard.audit.dead-letter.observability.alert.email.to` | empty | Enables built-in email alerts. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.filter.enabled` | `false` | Enables the built-in receiver verification filter. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor.enabled` | `false` | Enables the built-in receiver verification interceptor. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.verification.enabled` | `false` | Creates receiver verification settings from properties. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.verification.bearer-token` | empty | Optional bearer token required by the built-in verifier. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.verification.signature-secret` | empty | Optional HMAC secret required by the built-in verifier. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.verification.max-skew` | `5m` | Allowed timestamp skew for receiver verification. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file.enabled` | `false` | Enables the file-backed replay store for receiver verification. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file.path` | `privacy-audit-webhook-replay-store.json` | File path for replay store entries. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.namespace` | empty | Optional namespace prefixed to replay-store nonces to separate receiver domains on a shared backend. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.redis.enabled` | `false` | Enables the Redis-backed replay store for receiver verification. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.redis.key-prefix` | `privacy:audit:webhook:replay:` | Redis key prefix for replay store entries. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.redis.scan-batch-size` | `500` | Redis SCAN batch size used by replay store snapshot and clear operations. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.enabled` | `false` | Enables the JDBC replay store for receiver verification. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.table-name` | `privacy_audit_webhook_replay_store` | JDBC table name for replay store entries. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.initialize-schema` | `false` | Runs the replay store schema initializer. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.cleanup-interval` | `5m` | Minimum interval between replay store cleanup passes. |
| `privacy.guard.audit.jdbc.tenant-column-name` | empty | Optional dedicated JDBC tenant column used by the audit repository for tenant-native writes and reads. |
| `privacy.guard.audit.jdbc.tenant-detail-key` | `tenantId` | Audit detail key copied into the optional audit tenant column when configured. |
| `privacy.guard.audit.dead-letter.jdbc.tenant-column-name` | empty | Optional dedicated JDBC tenant column used by the dead-letter repository for tenant-native writes and reads. |
| `privacy.guard.audit.dead-letter.jdbc.tenant-detail-key` | `tenantId` | Dead-letter detail key copied into the optional tenant column when configured. |

For the full property matrix, examples, and advanced options, see the sections below.

## Custom Masking

Implement `MaskingStrategy` when built-in `SensitiveType` rules are not enough:

```java
@Bean
MaskingStrategy customNameMaskingStrategy() {
    return new MaskingStrategy() {
        @Override
        public boolean supports(MaskingContext context) {
            return context.sensitiveType() == SensitiveType.NAME;
        }

        @Override
        public String mask(String value, MaskingContext context) {
            return "[custom]" + value;
        }
    };
}
```

## Stable SPI

Public extension points that are intended to remain compatible within the current minor line are marked with `@StableSpi`.

Stable interfaces include:

- `MaskingStrategy`
- `PrivacyTenantProvider`
- `PrivacyTenantContextScope`
- `PrivacyTenantContextSnapshot`
- `PrivacyTenantPolicyResolver`
- `PrivacyTenantAwareMaskingStrategy`
- `PrivacyTenantAuditPolicyResolver`
- `PrivacyTenantLoggingPolicyResolver`
- `PrivacyTenantAuditReadRepository`
- `PrivacyTenantAuditDeadLetterReadRepository`
- `PrivacyTenantAuditWriteRepository`
- `PrivacyTenantAuditDeadLetterWriteRepository`
- `PrivacyTenantAuditDeadLetterDeleteRepository`
- `PrivacyTenantAuditDeadLetterReplayRepository`
- `PrivacyAuditPublisher`
- `PrivacyAuditRepository`, `PrivacyAuditQueryRepository`, and `PrivacyAuditStatsRepository`
- `PrivacyAuditDeadLetterRepository`, `PrivacyAuditDeadLetterStatsRepository`, and `PrivacyAuditDeadLetterHandler`
- `PrivacyAuditDeadLetterAlertCallback`
- `PrivacyAuditDeadLetterWebhookReplayStore`
- `PrivacyAuditDeadLetterWebhookAlertTelemetry`
- `PrivacyAuditDeadLetterWebhookVerificationTelemetry`

Directly coupled carrier types such as `MaskingContext`, `TextMaskingRule`, `PrivacyTenantPolicy`, `PrivacyTenantAuditPolicy`, `PrivacyTenantLoggingPolicy`, `PrivacyTenantAuditWriteRequest`, `PrivacyTenantAuditDeadLetterWriteRequest`, `PrivacyAuditEvent`, `PrivacyAuditQueryCriteria`, `PrivacyAuditDeadLetterEntry`, and replay-store snapshot records are also marked with `@StableSpi`.

Repository implementations, auto-configuration classes, servlet adapters, metrics binders, and schema helpers are internal runtime wiring rather than stable SPI.

## Tenant Policies

Enable tenant-aware masking when different tenants need different fallback mask characters or text detection rules:

```yaml
privacy:
  guard:
    tenant:
      enabled: true
      header-name: X-Privacy-Tenant
      default-tenant: public
      policies:
        tenant-a:
          fallback-mask-char: "#"
          text:
            additional-patterns:
              - type: GENERIC
                pattern: EMP\d{4}
        tenant-b:
          fallback-mask-char: X
```

When tenant mode is enabled in a servlet application, the starter registers a request filter that reads the configured tenant header into the current thread context for JSON masking, log sanitization, and audit sanitization.
Custom masking beans can inspect the active tenant by implementing `PrivacyTenantAwareMaskingStrategy`.
Tenant policies can also narrow audit details per tenant:

```yaml
privacy:
  guard:
    tenant:
      policies:
        tenant-a:
          audit:
            include-detail-keys:
              - phone
              - employeeCode
            attach-tenant-id: true
            tenant-detail-key: tenant
        tenant-b:
          audit:
            include-detail-keys:
              - phone
            attach-tenant-id: true
            tenant-detail-key: tenant
```

`include-detail-keys` is applied before `exclude-detail-keys`, and the tenant detail tag is appended after sanitization when enabled.
Tenant policies can also override which MDC or structured logging keys are sanitized for that tenant:

```yaml
privacy:
  guard:
    tenant:
      policies:
        tenant-a:
          logging:
            mdc:
              enabled: true
              include-keys:
                - email
                - phone
        tenant-b:
          logging:
            structured:
              enabled: true
              include-keys:
                - phone
```

When a tenant logging override is present, its `enabled`, `include-keys`, and `exclude-keys` values replace the global MDC or structured logging selection for that tenant. Unset tenant logging fields inherit the global logging settings.
Tenant policies can also override dead-letter backlog thresholds and recovery notifications per tenant:

```yaml
privacy:
  guard:
    tenant:
      policies:
        tenant-a:
          observability:
            dead-letter:
              warning-threshold: 1
              down-threshold: 2
              notify-on-recovery: true
        tenant-b:
          observability:
            dead-letter:
              warning-threshold: 2
              down-threshold: 4
              notify-on-recovery: false
```

These overrides only affect the tenant-scoped dead-letter backlog snapshots, tenant health summary, and tenant alert monitor. Unset fields fall back to the existing global dead-letter observability settings.
For the full request/header contract, stable SPI customization points, and tenant-scoped helper and facade usage, see `docs/MULTI_TENANT_GUIDE.md`.

## Audit and Dead Letters

The starter supports:

- async and batched audit publishing
- retry with dead-letter fallback
- dead-letter query, cleanup, replay, export, and import
- operation-audit traces for management activity
- tenant-aware audit query and stats via `PrivacyTenantAuditQueryService`
- tenant-aware dead-letter query and stats via `PrivacyTenantAuditDeadLetterQueryService`
- tenant-aware dead-letter batch delete and replay via `PrivacyTenantAuditDeadLetterOperationsService`
- tenant-aware dead-letter export, import, and manifest flows via `PrivacyTenantAuditDeadLetterExchangeService`
- unified tenant-aware management entry point via `PrivacyTenantAuditManagementService`
- optional tenant-scoped dead-letter alert monitoring through `PrivacyTenantAuditDeadLetterAlertMonitor` and `PrivacyTenantAuditDeadLetterAlertCallback`

Sample dead-letter endpoints include:

- `GET /audit-dead-letters`
- `GET /audit-dead-letters/stats`
- `DELETE /audit-dead-letters/{id}`
- `POST /audit-dead-letters/{id}/replay`
- `POST /audit-dead-letters/replay?limit=100`

## Observability and Alerts

### Actuator & Metrics

When Actuator is present, the starter can expose:

- `privacyAuditDeadLetters` health
- `privacy.audit.deadletters.total`
- `privacy.audit.deadletters.state{state=*}`
- `privacy.audit.deadletters.threshold{level=*}`
- `privacy.audit.deadletters.alert.webhook.attempts`
- `privacy.audit.deadletters.alert.webhook.retries`
- `privacy.audit.deadletters.alert.webhook.deliveries{outcome=*}`
- `privacy.audit.deadletters.alert.webhook.last_delivery_seconds{outcome=*}`
- `privacy.audit.deadletters.alert.webhook.failures{type=*,retryable=*,category=*}`
- `privacy.audit.deadletters.alert.webhook.last_failure_status`
- `privacy.audit.deadletters.alert.webhook.last_failure_retryable`
- `privacy.audit.deadletters.alert.webhook.last_failure_type{type=*}`
- `privacy.audit.deadletters.receiver.replay_store.count`
- `privacy.audit.deadletters.receiver.replay_store.expiring_soon`
- `privacy.audit.deadletters.receiver.replay_store.expiry_seconds{kind=*}`
- `privacy.audit.deadletters.receiver.replay_store.cleanup.last_count`
- `privacy.audit.deadletters.receiver.replay_store.cleanup.last_duration_ms`
- `privacy.audit.deadletters.receiver.replay_store.cleanup.last_timestamp`
- `privacy.audit.deadletters.receiver.verification.failures{reason=*}`
- `privacy.audit.tenant.read.path{domain=*,path=*}`
- `privacy.audit.tenant.read.path{domain=dead_letter_export,path=*}`
- `privacy.audit.tenant.read.path{domain=dead_letter_manifest,path=*}`
- `privacy.audit.tenant.write.path{domain=*,path=*}`
- `privacy.audit.tenant.write.path{domain=dead_letter_import,path=*}`
- `privacy.audit.deadletters.alert.tenant.transitions{tenant=*,state=*,recovery=*}`
- `privacy.audit.deadletters.alert.tenant.deliveries{tenant=*,channel=*,outcome=*}`
- `privacy.audit.deadletters.receiver.route.failures{route=*,reason=*}`

### Built-in Webhook Alerts

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            enabled: true
            webhook:
              url: https://example.com/privacy-alerts
              bearer-token: demo-token
              signature-secret: demo-hmac-secret
              signature-algorithm: HmacSHA256
              signature-header: X-Privacy-Alert-Signature
              timestamp-header: X-Privacy-Alert-Timestamp
              nonce-header: X-Privacy-Alert-Nonce
              max-attempts: 3
              backoff: 200ms
```

The built-in webhook callback retries failed deliveries, signs `timestamp.nonce.payload`, and records delivery metrics when Micrometer is available.
When delivery fails, the callback logs include failure type, status code, retryable flag, and a short error message.
When tenant alert monitoring is enabled, the built-in tenant webhook wrapper also includes `tenantId` in the alert payload.
You can also route specific tenants to different webhook targets:

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            enabled: true
            tenant:
              enabled: true
              tenant-ids:
                - tenant-a
              routes:
                tenant-a:
                  webhook:
                    url: https://tenant-a.example.com/privacy-alerts
                    bearer-token: tenant-a-token
```

If the same application also receives tenant-specific callbacks, you can route receiver verification by path:

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            receiver:
              filter:
                enabled: true
            tenant:
              routes:
                tenant-a:
                  receiver:
                    path-pattern: /tenant-a/privacy-alerts
                    bearer-token: tenant-a-token
                    signature-secret: tenant-a-secret
                    replay-namespace: tenant-a-receiver
```

### Built-in Email Alerts

```yaml
spring:
  mail:
    host: smtp.example.com
    port: 587
    username: demo-user
    password: demo-password

privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            enabled: true
            email:
              from: privacy@example.com
              to: ops@example.com
```

When tenant alert monitoring is enabled, the built-in tenant email wrapper appends the tenant marker to the subject and includes `tenantId:` in the body.
You can also override recipients or subject prefixes per tenant:

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            enabled: true
            tenant:
              enabled: true
              tenant-ids:
                - tenant-a
              routes:
                tenant-a:
                  email:
                    to: tenant-a-ops@example.com
                    subject-prefix: "[tenant-a]"
```

### Webhook Verification SPI

Applications receiving signed alerts can reuse:

- `PrivacyAuditDeadLetterWebhookRequestVerifier`
- `PrivacyAuditDeadLetterWebhookReplayStore`
- `InMemoryPrivacyAuditDeadLetterWebhookReplayStore`
- `FilePrivacyAuditDeadLetterWebhookReplayStore`
- `RedisPrivacyAuditDeadLetterWebhookReplayStore`
- `JdbcPrivacyAuditDeadLetterWebhookReplayStore`
- `PrivacyAuditDeadLetterWebhookVerificationFilter`
- `PrivacyAuditDeadLetterWebhookVerificationInterceptor`

Choose one of these protection modes:

- `privacy.guard.audit.dead-letter.observability.alert.receiver.filter.enabled=true`
- `privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor.enabled=true`
- manual verifier injection in your own endpoint code

Verification failures return a JSON body with an error message and a reason code, for example:

```json
{"error":"Invalid signature","reason":"INVALID_SIGNATURE"}
```

Reason codes include `INVALID_AUTHORIZATION`, `MISSING_SIGNATURE_HEADERS`, `INVALID_TIMESTAMP`, `EXPIRED_TIMESTAMP`, `INVALID_SIGNATURE`, and `REPLAY_DETECTED`.

You can supply verification settings with properties instead of defining a bean:

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            receiver:
              verification:
                enabled: true
                bearer-token: demo-receiver-token
                signature-secret: demo-receiver-secret
                max-skew: 5m
```

If receiver verification is enabled without a bearer token or signature secret, verification becomes a no-op and only basic routing is applied.

### File Replay Store

Use the file-backed replay store for single-instance deployments:

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            receiver:
              replay-store:
                file:
                  enabled: true
                  path: /var/lib/privacy-audit/replay-store.json
```

### Redis Replay Store

Use the Redis-backed replay store when you need shared nonce state without managing JDBC schema:

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            receiver:
              replay-store:
                redis:
                  enabled: true
                  key-prefix: privacy:audit:webhook:replay:
                  scan-batch-size: 500
```

### JDBC Replay Store

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            receiver:
              replay-store:
                jdbc:
                  enabled: true
                  initialize-schema: true
                  table-name: privacy_audit_webhook_replay_store
```

For production MySQL/PostgreSQL rollout guidance, schema-management recommendations, optional tenant-column rollout, and migration notes across audit, dead-letter, and replay-store tables, see `docs/JDBC_PRODUCTION_GUIDE.md`.
If more than one replay-store backend is enabled, the starter prefers `JDBC`, then `Redis`, then `file`, and finally falls back to `InMemory`.

## Sample App

`samples/privacy-demo/` demonstrates:

- masking and audit query endpoints
- dead-letter export/import/replay endpoints
- signed receiver endpoint: `POST /demo-alert-receiver`
- receiver inspection: `GET /demo-alert-receiver/last`
- replay-store query: `GET /demo-alert-receiver/replay-store?limit=20&offset=0`
- replay-store stats: `GET /demo-alert-receiver/replay-store/stats?expiringWithin=PT5M`
- replay-store clear: `DELETE /demo-alert-receiver/replay-store`

Current sample status:

- the default sample profile already exercises tenant-aware management and `/demo-tenants/observability`
- built-in `IN_MEMORY` and `JDBC` dead-letter repositories now report `dead_letter_replay=native`
- JDBC and Redis multi-instance rehearsal scripts are available in both PowerShell and shell variants under `samples/privacy-demo/scripts/`
- `postgres-redis-tenant` and `postgres-redis-tenant-node2` add a production-like sample topology with JDBC audit/dead-letter storage on PostgreSQL plus shared Redis replay-store protection
- `samples/privacy-demo/docker-compose.postgres-redis.yml` can boot PostgreSQL, Redis, and two sample nodes together for local rollout rehearsal
- `/demo-tenants/observability` now reports repository tenant capability flags and exchange-path telemetry for import/export/manifest verification
- `fallback-tenant` adds a generic-only comparison profile that intentionally disables tenant SPI support so `fallback` counters can be verified locally
- `custom-tenant-native` adds a custom repository SPI reference profile that keeps native tenant paths without relying on the built-in repository implementations
- `custom-jdbc-tenant` adds a custom JDBC tenant repository reference that keeps native tenant paths while still using sample-owned JDBC beans instead of the built-in repository beans
- `custom-jdbc-tenant-node2` extends that custom JDBC reference into the same local two-node rehearsal model used by the built-in JDBC sample

The sample uses filter mode by default. To switch to interceptor mode:

- Windows: `mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=interceptor`
- macOS / Linux: `./mvnw spring-boot:run -Dspring-boot.run.profiles=interceptor`

## Local Development

- Verify everything: `./mvnw -q verify` or `mvnw.cmd -q verify`
- Install local artifacts: `./mvnw -q -DskipTests install`
- Run sample: `./mvnw -q -f samples/privacy-demo/pom.xml spring-boot:run`

## Maintainer Quick Start

- Triage using `SUPPORT.md`, `SECURITY.md`, and `docs/GITHUB_LABELS.md`
- Run `python scripts/check_repo_hygiene.py` before tagging
- Follow `docs/MAINTAINER_GUIDE.md`; the latest published release template is now `docs/RELEASE_EXECUTION_v0.4.0.md`

## Roadmap

Recent `v0.3.0` release-readiness and post-release polish:

1. JDBC production guide and migration notes across audit, dead-letter, and replay-store tables
2. Stable SPI markers for supported extension points and their carrier types
3. Final docs, roadmap, and GitHub release copy consistency pass
4. Tenant observability sample endpoint and lazy Micrometer telemetry registration fixes
5. Tenant-native dead-letter replay for built-in `IN_MEMORY` / `JDBC` repositories plus observability alignment
6. Stable tenant logging policy resolution over the existing `privacy.guard.tenant.policies.*.logging.*` model
7. Cross-platform JDBC / Redis multi-instance verification scripts for the sample app
8. Tenant-scoped dead-letter observability policy overrides for thresholds and recovery notifications
9. Tenant operational telemetry for alert transitions, tenant delivery outcomes, and receiver route verification failures
10. PostgreSQL + Redis production-like sample profiles, compose recipe, and verification scripts

Post-release themes and longer-term follow-up stay in `docs/ROADMAP.md`.

## Project Docs

- Documentation index: `docs/INDEX.md`
- JDBC production guide: `docs/JDBC_PRODUCTION_GUIDE.md`
- Multi-tenant guide: `docs/MULTI_TENANT_GUIDE.md`
- Tenant adoption playbook: `docs/TENANT_ADOPTION_PLAYBOOK.md`
- Tenant observability guide: `docs/TENANT_OBSERVABILITY_GUIDE.md`
- Maintainer guide: `docs/MAINTAINER_GUIDE.md`
- Receiver operations: `docs/RECEIVER_OPERATIONS.md`
- Roadmap: `docs/ROADMAP.md`
- Release checklist: `docs/RELEASE_CHECKLIST.md`
- Current published release notes: `docs/releases/RELEASE_NOTES_v0.4.0.md`
- Current published release execution guide: `docs/RELEASE_EXECUTION_v0.4.0.md`
- Current published release runbook: `docs/RELEASE_RUNBOOK_v0.4.0.md`
- Current published release dry run record: `docs/RELEASE_DRY_RUN_v0.4.0.md`
- Current published release announcement: `docs/RELEASE_ANNOUNCEMENT_v0.4.0.md`
- Current published release announcement (zh-CN): `docs/RELEASE_ANNOUNCEMENT_v0.4.0.zh-CN.md`
- Current published GitHub release copy: `docs/GITHUB_RELEASE_COPY_v0.4.0.md`
- Previous release notes archive: `docs/releases/RELEASE_NOTES_v0.3.0.md`
- Older release notes archive: `docs/releases/RELEASE_NOTES_v0.2.0.md`
- GitHub metadata: `docs/GITHUB_METADATA.md`

## Release Notes

The release workflow looks for `docs/releases/RELEASE_NOTES_<tag>.md` first and falls back to `docs/releases/RELEASE_NOTES_TEMPLATE.md`.
