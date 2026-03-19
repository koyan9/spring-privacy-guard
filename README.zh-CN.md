# spring-privacy-guard

[English](README.md) | [简体中文](README.zh-CN.md)

[![CI](https://github.com/koyan9/spring-privacy-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/koyan9/spring-privacy-guard/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/koyan9/spring-privacy-guard?display_name=tag)](https://github.com/koyan9/spring-privacy-guard/releases/latest)
[![Changelog](https://img.shields.io/badge/Changelog-0.2.0-0f766e)](CHANGELOG.md)
[![Release Notes](https://img.shields.io/badge/Release%20Notes-v0.2.0-1d4ed8)](docs/releases/RELEASE_NOTES_v0.2.0.md)
[![Security](https://img.shields.io/badge/Security-Policy-7f1d1d)](SECURITY.md)
[![Support](https://img.shields.io/badge/Support-Guide-1f2937)](SUPPORT.md)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-orange)](README.md#quick-start)
[![Docs Index](https://img.shields.io/badge/Docs-Index-6f42c1)](docs/INDEX.md)
[![English Docs](https://img.shields.io/badge/Docs-English-blue)](README.md)

`spring-privacy-guard` 是一个面向 Spring Boot 的隐私保护 Starter，用于敏感数据脱敏、日志清洗、隐私审计追踪，以及安全可运维的死信处理与告警。

## 模块说明

- `privacy-guard-core/`：脱敏注解、内置敏感类型、文本清洗、脱敏 SPI。
- `privacy-guard-spring-boot-starter/`：Spring Boot 自动配置、Jackson 集成、Logback 集成、审计存储、查询、死信、告警与可观测能力。
- `samples/privacy-demo/`：可直接运行的示例应用，覆盖脱敏、审计、死信、签名 webhook / email 告警，以及 receiver 校验流程。

## 核心能力

- 基于 `@SensitiveData` 的字段级脱敏
- 支持按租户配置不同的脱敏字符与文本规则
- Jackson JSON 输出脱敏
- 通过 `PrivacyLoggerFactory` 实现自由文本日志清洗
- 审计事件记录、查询、分页、排序和统计
- 内置 `IN_MEMORY` / `JDBC` 审计与死信仓储
- 自定义 `MaskingStrategy` SPI
- 异步审计发布、批量写入、重试与死信回退
- 死信查询、清理、重放、JSON/CSV 导入导出
- Actuator 健康检查与 Micrometer 指标
- 签名 webhook、email 告警、receiver 验签与 replay 防护

## 快速开始

环境要求：

- Java `17+`
- Spring Boot `3.3.x`

添加依赖：

```xml
<dependency>
    <groupId>io.github.koyan9</groupId>
    <artifactId>spring-privacy-guard-spring-boot-starter</artifactId>
    <version>0.2.0</version>
</dependency>
```

给需要脱敏的字段添加注解：

```java
public record PatientView(
        @SensitiveData(type = SensitiveType.NAME) String patientName,
        @SensitiveData(type = SensitiveType.PHONE) String phone,
        @SensitiveData(type = SensitiveType.ID_CARD) String idCard,
        @SensitiveData(type = SensitiveType.EMAIL) String email
) {
}
```

最小配置示例：

```yaml
privacy:
  guard:
    fallback-mask-char: "*"
    audit:
      repository-type: IN_MEMORY
```

## 常用配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `privacy.guard.enabled` | `true` | Starter 总开关 |
| `privacy.guard.fallback-mask-char` | `*` | 默认脱敏字符 |
| `privacy.guard.logging.enabled` | `true` | 启用日志清洗能力 |
| `privacy.guard.logging.mdc.enabled` | `false` | 启用 Logback MDC 值脱敏 |
| `privacy.guard.logging.mdc.include-keys` | 空 | 仅脱敏指定 MDC 键 |
| `privacy.guard.logging.mdc.exclude-keys` | 空 | 跳过指定 MDC 键 |
| `privacy.guard.logging.structured.enabled` | `false` | 启用结构化日志字段脱敏 |
| `privacy.guard.logging.structured.include-keys` | 空 | 仅脱敏指定结构化字段 |
| `privacy.guard.logging.structured.exclude-keys` | 空 | 跳过指定结构化字段 |
| `privacy.guard.tenant.enabled` | `false` | 启用多租户隐私策略解析与请求头租户透传 |
| `privacy.guard.tenant.header-name` | `X-Privacy-Tenant` | Servlet 场景下读取当前租户的请求头 |
| `privacy.guard.tenant.default-tenant` | 空 | 未传租户请求头时使用的默认租户 |
| `privacy.guard.masking.text.email-pattern` | 空 | 覆盖文本脱敏使用的 email 正则 |
| `privacy.guard.masking.text.phone-pattern` | 空 | 覆盖文本脱敏使用的手机号正则 |
| `privacy.guard.masking.text.id-card-pattern` | 空 | 覆盖文本脱敏使用的身份证号正则 |
| `privacy.guard.masking.text.additional-patterns` | 空 | 追加映射到 `SensitiveType` 的文本脱敏规则 |
| `privacy.guard.audit.enabled` | `true` | 启用审计能力 |
| `privacy.guard.audit.repository-type` | `NONE` | 审计仓储类型：`NONE`、`IN_MEMORY`、`JDBC` |
| `privacy.guard.audit.async.thread-pool-size` | `1` | 异步 / 批量审计执行器线程数 |
| `privacy.guard.audit.dead-letter.repository-type` | `NONE` | 死信仓储类型：`NONE`、`IN_MEMORY`、`JDBC` |
| `privacy.guard.audit.dead-letter.observability.health.warning-threshold` | `1` | 死信积压预警阈值 |
| `privacy.guard.audit.dead-letter.observability.health.down-threshold` | `100` | 死信积压降级阈值 |
| `privacy.guard.audit.dead-letter.observability.alert.enabled` | `false` | 启用死信阈值告警 |
| `privacy.guard.audit.dead-letter.observability.alert.webhook.url` | 空 | 启用内置 webhook 告警 |
| `privacy.guard.audit.dead-letter.observability.alert.webhook.backoff-policy` | `FIXED` | webhook 告警重试退避策略 |
| `privacy.guard.audit.dead-letter.observability.alert.webhook.max-backoff` | `backoff*10 (max 30s)` | webhook 指数退避的最大等待时间 |
| `privacy.guard.audit.dead-letter.observability.alert.webhook.jitter` | `0` | webhook 重试延迟的抖动系数 (0-1) |
| `privacy.guard.audit.dead-letter.observability.alert.email.to` | 空 | 启用内置 email 告警 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.filter.enabled` | `false` | 启用内置 receiver 验签过滤器 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor.enabled` | `false` | 启用内置 receiver 验签拦截器 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.verification.enabled` | `false` | 通过配置创建 receiver 验签设置 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.verification.bearer-token` | 空 | 内置验签需要的 bearer token |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.verification.signature-secret` | 空 | 内置验签需要的签名密钥 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.verification.max-skew` | `5m` | 允许的时间戳偏移窗口 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file.enabled` | `false` | 启用文件型 replay-store |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file.path` | `privacy-audit-webhook-replay-store.json` | replay-store 文件路径 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.redis.enabled` | `false` | 启用 Redis replay-store |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.redis.key-prefix` | `privacy:audit:webhook:replay:` | Redis replay-store 键前缀 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.redis.scan-batch-size` | `500` | replay-store 快照与清理时使用的 Redis SCAN 批大小 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.enabled` | `false` | 启用 JDBC replay-store |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.table-name` | `privacy_audit_webhook_replay_store` | replay-store JDBC 表名 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.initialize-schema` | `false` | 初始化 replay-store SQL 表 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.cleanup-interval` | `5m` | replay-store 清理间隔 |
| `privacy.guard.audit.jdbc.tenant-column-name` | 空 | 审计 JDBC 仓储可选的专用租户列名，用于原生租户写入与查询。 |
| `privacy.guard.audit.jdbc.tenant-detail-key` | `tenantId` | 开启专用租户列后，从 audit detail 中读取租户值的键名。 |
| `privacy.guard.audit.dead-letter.jdbc.tenant-column-name` | 空 | 死信 JDBC 仓储可选的专用租户列名，用于原生租户写入与查询。 |
| `privacy.guard.audit.dead-letter.jdbc.tenant-detail-key` | `tenantId` | 开启死信专用租户列后，从 detail 中读取租户值的键名。 |

完整配置说明、全部属性以及详细默认值，请参考 `README.md` 中的英文完整文档。

## 自定义脱敏策略

当内置 `SensitiveType` 不满足需求时，可以实现 `MaskingStrategy`：

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

示例实现可参考 `samples/privacy-demo/src/main/java/com/example/privacydemo/DemoNameMaskingStrategy.java`。

## 稳定 SPI

仓库中承诺在当前 minor 版本线内保持兼容的公开扩展点，都会标记 `@StableSpi`。

稳定接口包括：

- `MaskingStrategy`
- `PrivacyTenantProvider`
- `PrivacyTenantContextScope`
- `PrivacyTenantContextSnapshot`
- `PrivacyTenantPolicyResolver`
- `PrivacyTenantAwareMaskingStrategy`
- `PrivacyTenantAuditPolicyResolver`
- `PrivacyTenantAuditReadRepository`
- `PrivacyTenantAuditDeadLetterReadRepository`
- `PrivacyTenantAuditWriteRepository`
- `PrivacyTenantAuditDeadLetterWriteRepository`
- `PrivacyAuditPublisher`
- `PrivacyAuditRepository`、`PrivacyAuditQueryRepository`、`PrivacyAuditStatsRepository`
- `PrivacyAuditDeadLetterRepository`、`PrivacyAuditDeadLetterStatsRepository`、`PrivacyAuditDeadLetterHandler`
- `PrivacyAuditDeadLetterAlertCallback`
- `PrivacyAuditDeadLetterWebhookReplayStore`
- `PrivacyAuditDeadLetterWebhookAlertTelemetry`
- `PrivacyAuditDeadLetterWebhookVerificationTelemetry`

与这些扩展点直接耦合的载荷 / 上下文类型，例如 `MaskingContext`、`TextMaskingRule`、`PrivacyTenantPolicy`、`PrivacyTenantAuditPolicy`、`PrivacyTenantAuditWriteRequest`、`PrivacyTenantAuditDeadLetterWriteRequest`、`PrivacyAuditEvent`、`PrivacyAuditQueryCriteria`、`PrivacyAuditDeadLetterEntry` 以及 replay-store 快照记录，也会同步标记 `@StableSpi`。

自动配置、具体仓储实现、Servlet 适配器、指标 binder、schema resolver 等运行时装配类属于内部实现，不在稳定 SPI 承诺范围内。

## 多租户隐私策略

当不同租户需要不同的脱敏字符或文本检测规则时，可以启用多租户策略：

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

在 Servlet 应用中启用 tenant 模式后，starter 会自动注册一个过滤器，把请求头中的租户写入当前线程上下文，供 JSON 脱敏、日志清洗和审计清洗共享使用。
如果你需要按租户定制字段脱敏逻辑，可以实现 `PrivacyTenantAwareMaskingStrategy`。
租户策略也可以进一步约束审计 detail：

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

启用后，`include-detail-keys` 会先筛选 detail，再应用 `exclude-detail-keys`；如果开启 `attach-tenant-id`，租户标识会在清洗完成后附加到审计 detail 中。
完整的多租户请求头约定、稳定 SPI 扩展点、管理服务与示例端点说明，请参考 `docs/MULTI_TENANT_GUIDE.md`。

## 审计与死信

Starter 支持完整的审计与死信处理链路：

- 审计事件异步发布
- 批量写入与失败重试
- 死信持久化与重放
- 死信分页查询与统计
- JSON / CSV 导入导出
- 管理动作自身写入审计轨迹
- 通过 `PrivacyTenantAuditQueryService` 提供按租户过滤的审计查询与统计
- 通过 `PrivacyTenantAuditDeadLetterQueryService` 提供按租户过滤的死信查询与统计
- 通过 `PrivacyTenantAuditDeadLetterOperationsService` 提供按租户的死信批量删除与批量重放
- 通过 `PrivacyTenantAuditDeadLetterExchangeService` 提供按租户的死信导出、导入和 manifest 校验流程
- 通过 `PrivacyTenantAuditManagementService` 提供统一的多租户管理入口

常见死信能力：

- `GET /audit-dead-letters`
- `GET /audit-dead-letters/stats`
- `DELETE /audit-dead-letters/{id}`
- `POST /audit-dead-letters/{id}/replay`
- `POST /audit-dead-letters/replay?limit=100`

## 可观测与告警

### Actuator 与指标

当应用引入 Actuator 时，可以暴露：

- `privacyAuditDeadLetters` 健康检查
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

### 内置 webhook / email 告警

Webhook 告警示例：

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

当回调失败时，日志会包含失败类型、状态码、是否可重试以及简短错误信息。

Email 告警示例：

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
              subject-prefix: [privacy-guard]
```

## Webhook Receiver 验签 SPI

Starter 提供可复用的 webhook receiver 校验能力：

- `PrivacyAuditDeadLetterWebhookRequestVerifier`
- `PrivacyAuditDeadLetterWebhookReplayStore`
- `InMemoryPrivacyAuditDeadLetterWebhookReplayStore`
- `FilePrivacyAuditDeadLetterWebhookReplayStore`
- `RedisPrivacyAuditDeadLetterWebhookReplayStore`
- `JdbcPrivacyAuditDeadLetterWebhookReplayStore`
- `PrivacyAuditDeadLetterWebhookVerificationFilter`
- `PrivacyAuditDeadLetterWebhookVerificationInterceptor`

你可以选择：

- 使用 `filter` 模式保护接收端路径
- 使用 `interceptor` 模式保护接收端路径
- 直接在业务代码中注入 verifier 做手动校验

验签失败时会返回包含原因码的 JSON 响应，例如：

```json
{"error":"Invalid signature","reason":"INVALID_SIGNATURE"}
```

原因码包括 `INVALID_AUTHORIZATION`、`MISSING_SIGNATURE_HEADERS`、`INVALID_TIMESTAMP`、`EXPIRED_TIMESTAMP`、`INVALID_SIGNATURE`、`REPLAY_DETECTED`。

也可以直接通过配置创建验签设置：

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

如果启用 receiver 验签但未配置 bearer token 或 signature secret，验签将变为放行模式。

### 文件型 Replay-Store 示例

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

### Redis Replay-Store 示例

当你需要多实例共享 nonce 状态、但不想维护 JDBC schema 时，可以使用 Redis：

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

### JDBC Replay-Store 示例

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

生产环境中使用 MySQL / PostgreSQL 时，建议同时阅读 `docs/JDBC_PRODUCTION_GUIDE.md`，其中包含 schema 管理、可选租户列、索引建议和迁移步骤。
如果同时启用了多个 replay-store 后端，starter 会按 `JDBC`、`Redis`、`file`、`InMemory` 的顺序选择。

## 示例应用

`samples/privacy-demo/` 默认展示：

- `GET /patients/demo`
- `GET /audit-events`
- `GET /audit-dead-letters`
- `POST /demo-alert-receiver`
- `GET /demo-alert-receiver/last`
- `GET /demo-alert-receiver/replay-store`
- `GET /demo-alert-receiver/replay-store/stats`
- `DELETE /demo-alert-receiver/replay-store`

默认使用 receiver filter 模式；切换到拦截器模式可使用：

- Windows：`mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=interceptor`
- macOS / Linux：`./mvnw spring-boot:run -Dspring-boot.run.profiles=interceptor`

## 本地开发

- 全量校验：`./mvnw -q verify` 或 `mvnw.cmd -q verify`
- 安装本地构件：`./mvnw -q -DskipTests install`
- 运行示例：`./mvnw -q -f samples/privacy-demo/pom.xml spring-boot:run`

如果你只想快速验证签名 receiver，可以直接运行：

- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/verify-alert-receiver.ps1`

## 维护者快速入口

- 排障与分诊参考 `SUPPORT.md`、`SECURITY.md`、`docs/GITHUB_LABELS.md`
- 发布前执行 `python scripts/check_repo_hygiene.py`
- 发布流程参考 `docs/MAINTAINER_GUIDE.md` 与 `docs/RELEASE_EXECUTION_v0.3.0.md`

## 说明

中文文档会优先覆盖常用能力与配置示例；当中文和英文内容存在差异时，请以 `README.md` 的完整说明为准。

## 项目文档

- 文档索引：`docs/INDEX.md`
- JDBC 生产指引：`docs/JDBC_PRODUCTION_GUIDE.md`
- 多租户接入指南：`docs/MULTI_TENANT_GUIDE.md`
- 多租户采用手册：`docs/TENANT_ADOPTION_PLAYBOOK.md`
- 维护者指南：`docs/MAINTAINER_GUIDE.md`
- 接收端运维指引：`docs/RECEIVER_OPERATIONS.md`
- 路线图：`docs/ROADMAP.md`
- 发布检查清单：`docs/RELEASE_CHECKLIST.md`
- 当前已发布版本说明：`docs/releases/RELEASE_NOTES_v0.2.0.md`
- `v0.3.0` 发布执行指引草稿：`docs/RELEASE_EXECUTION_v0.3.0.md`
- `v0.3.0` 发布运行手册草稿：`docs/RELEASE_RUNBOOK_v0.3.0.md`
- `v0.3.0` 发布说明草稿：`docs/releases/RELEASE_NOTES_v0.3.0.md`
- `v0.3.0` 发布演练记录：`docs/RELEASE_DRY_RUN_v0.3.0.md`
- `v0.3.0` 英文发布公告：`docs/RELEASE_ANNOUNCEMENT_v0.3.0.md`
- `v0.3.0` 中文发布公告：`docs/RELEASE_ANNOUNCEMENT_v0.3.0.zh-CN.md`
- GitHub Release 可直接复制文案草稿：`docs/GITHUB_RELEASE_COPY_v0.3.0.md`
- GitHub 仓库元数据建议：`docs/GITHUB_METADATA.md`
