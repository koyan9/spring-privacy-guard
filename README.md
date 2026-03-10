# spring-privacy-guard

[English](README.md) | [简体中文](README.zh-CN.md)

[![CI](https://github.com/koyan9/spring-privacy-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/koyan9/spring-privacy-guard/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/koyan9/spring-privacy-guard?display_name=tag)](https://github.com/koyan9/spring-privacy-guard/releases/latest)
[![Changelog](https://img.shields.io/badge/Changelog-0.2.0-0f766e)](CHANGELOG.md)
[![Release Notes](https://img.shields.io/badge/Release%20Notes-v0.2.0-1d4ed8)](docs/releases/RELEASE_NOTES_v0.2.0.md)
[![Security](https://img.shields.io/badge/Security-Policy-7f1d1d)](SECURITY.md)
[![Support](https://img.shields.io/badge/Support-Guide-1f2937)](SUPPORT.md)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-orange)](#quick-start)
[![Docs Index](https://img.shields.io/badge/Docs-Index-6f42c1)](docs/INDEX.md)
[![中文文档](https://img.shields.io/badge/Docs-zh--CN-green)](README.zh-CN.md)

A Spring Boot starter for masking sensitive data, sanitizing logs, tracing privacy operations, and operating dead-letter workflows securely.

## Modules

- `privacy-guard-core/`: masking annotations, built-in sensitive types, text sanitization, and masking SPI contracts.
- `privacy-guard-spring-boot-starter/`: Spring Boot auto-configuration, Jackson integration, Logback integration, audit storage, dead-letter handling, webhook/email alerts, and receiver verification support.
- `samples/privacy-demo/`: runnable sample covering masking, auditing, dead-letter operations, signed alert delivery, and verified receiver flows.

## Features

- Field masking with `@SensitiveData` and custom `MaskingStrategy`
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
    <version>0.2.0</version>
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

## Key Config

| Property | Default | Notes |
| --- | --- | --- |
| `privacy.guard.enabled` | `true` | Master switch for the starter. |
| `privacy.guard.logging.enabled` | `true` | Enables privacy-safe logging helpers. |
| `privacy.guard.audit.enabled` | `true` | Enables audit services and publishers. |
| `privacy.guard.audit.repository-type` | `NONE` | `NONE`, `IN_MEMORY`, or `JDBC`. |
| `privacy.guard.audit.dead-letter.repository-type` | `NONE` | Dead-letter storage type. |
| `privacy.guard.audit.dead-letter.observability.health.warning-threshold` | `1` | Backlog warning threshold. |
| `privacy.guard.audit.dead-letter.observability.health.down-threshold` | `100` | Backlog down threshold. |
| `privacy.guard.audit.dead-letter.observability.alert.enabled` | `false` | Enables dead-letter alerting. |
| `privacy.guard.audit.dead-letter.observability.alert.webhook.url` | empty | Enables built-in webhook alerts. |
| `privacy.guard.audit.dead-letter.observability.alert.email.to` | empty | Enables built-in email alerts. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.filter.enabled` | `false` | Enables the built-in receiver verification filter. |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor.enabled` | `false` | Enables the built-in receiver verification interceptor. |

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

## Audit and Dead Letters

The starter supports:

- async and batched audit publishing
- retry with dead-letter fallback
- dead-letter query, cleanup, replay, export, and import
- operation-audit traces for management activity

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
- `privacy.audit.deadletters.receiver.replay_store.count`
- `privacy.audit.deadletters.receiver.replay_store.expiring_soon`
- `privacy.audit.deadletters.receiver.replay_store.expiry_seconds{kind=*}`

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

### Webhook Verification SPI

Applications receiving signed alerts can reuse:

- `PrivacyAuditDeadLetterWebhookRequestVerifier`
- `PrivacyAuditDeadLetterWebhookReplayStore`
- `InMemoryPrivacyAuditDeadLetterWebhookReplayStore`
- `FilePrivacyAuditDeadLetterWebhookReplayStore`
- `PrivacyAuditDeadLetterWebhookVerificationFilter`
- `PrivacyAuditDeadLetterWebhookVerificationInterceptor`

Choose one of these protection modes:

- `privacy.guard.audit.dead-letter.observability.alert.receiver.filter.enabled=true`
- `privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor.enabled=true`
- manual verifier injection in your own endpoint code

## Sample App

`samples/privacy-demo/` demonstrates:

- masking and audit query endpoints
- dead-letter export/import/replay endpoints
- signed receiver endpoint: `POST /demo-alert-receiver`
- receiver inspection: `GET /demo-alert-receiver/last`
- replay-store query: `GET /demo-alert-receiver/replay-store?limit=20&offset=0`
- replay-store stats: `GET /demo-alert-receiver/replay-store/stats?expiringWithin=PT5M`
- replay-store clear: `DELETE /demo-alert-receiver/replay-store`

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
- Follow `docs/MAINTAINER_GUIDE.md` and `docs/RELEASE_EXECUTION_v0.2.0.md` for releases

## Roadmap

Recommended next development focus for `0.2.0`:

1. Receiver-side metrics/health consolidation and dashboard examples
2. Stronger receiver persistence options beyond file-backed replay stores
3. Broader database and production deployment examples
4. API polish for alert callbacks and receiver auto-configuration
5. Release packaging, badges, and docs hardening

## Project Docs

- Documentation index: `docs/INDEX.md`
- Maintainer guide: `docs/MAINTAINER_GUIDE.md`
- Receiver operations: `docs/RECEIVER_OPERATIONS.md`
- Roadmap: `docs/ROADMAP.md`
- Release checklist: `docs/RELEASE_CHECKLIST.md`
- Release execution guide: `docs/RELEASE_EXECUTION_v0.2.0.md`
- Release runbook: `docs/RELEASE_RUNBOOK_v0.2.0.md`
- Draft release notes: `docs/releases/RELEASE_NOTES_v0.2.0.md`
- Release dry run: `docs/RELEASE_DRY_RUN_v0.2.0.md`
- Release announcement: `docs/RELEASE_ANNOUNCEMENT_v0.2.0.md`
- Release announcement (zh-CN): `docs/RELEASE_ANNOUNCEMENT_v0.2.0.zh-CN.md`
- GitHub release copy: `docs/GITHUB_RELEASE_COPY_v0.2.0.md`
- GitHub metadata: `docs/GITHUB_METADATA.md`

## Release Notes

The release workflow looks for `docs/releases/RELEASE_NOTES_<tag>.md` first and falls back to `docs/releases/RELEASE_NOTES_TEMPLATE.md`.
