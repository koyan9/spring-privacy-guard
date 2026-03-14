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
| `privacy.guard.audit.enabled` | `true` | 启用审计能力 |
| `privacy.guard.audit.repository-type` | `NONE` | 审计仓储类型：`NONE`、`IN_MEMORY`、`JDBC` |
| `privacy.guard.audit.dead-letter.repository-type` | `NONE` | 死信仓储类型：`NONE`、`IN_MEMORY`、`JDBC` |
| `privacy.guard.audit.dead-letter.observability.health.warning-threshold` | `1` | 死信积压预警阈值 |
| `privacy.guard.audit.dead-letter.observability.health.down-threshold` | `100` | 死信积压降级阈值 |
| `privacy.guard.audit.dead-letter.observability.alert.enabled` | `false` | 启用死信阈值告警 |
| `privacy.guard.audit.dead-letter.observability.alert.webhook.url` | 空 | 启用内置 webhook 告警 |
| `privacy.guard.audit.dead-letter.observability.alert.email.to` | 空 | 启用内置 email 告警 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.filter.enabled` | `false` | 启用内置 receiver 验签过滤器 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.interceptor.enabled` | `false` | 启用内置 receiver 验签拦截器 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.verification.enabled` | `false` | 通过配置创建 receiver 验签设置 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.verification.bearer-token` | 空 | 内置验签需要的 bearer token |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.verification.signature-secret` | 空 | 内置验签需要的签名密钥 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.verification.max-skew` | `5m` | 允许的时间戳偏移窗口 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file.enabled` | `false` | 启用文件型 replay-store |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.file.path` | `privacy-audit-webhook-replay-store.json` | replay-store 文件路径 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.enabled` | `false` | 启用 JDBC replay-store |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.table-name` | `privacy_audit_webhook_replay_store` | replay-store JDBC 表名 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.initialize-schema` | `false` | 初始化 replay-store SQL 表 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.cleanup-interval` | `5m` | replay-store 清理间隔 |

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

## 审计与死信

Starter 支持完整的审计与死信处理链路：

- 审计事件异步发布
- 批量写入与失败重试
- 死信持久化与重放
- 死信分页查询与统计
- JSON / CSV 导入导出
- 管理动作自身写入审计轨迹

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
- `privacy.audit.deadletters.alert.webhook.failures{type=*,retryable=*}`
- `privacy.audit.deadletters.alert.webhook.last_failure_status`
- `privacy.audit.deadletters.alert.webhook.last_failure_retryable`
- `privacy.audit.deadletters.alert.webhook.last_failure_type{type=*}`

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
- 发布流程参考 `docs/MAINTAINER_GUIDE.md` 与 `docs/RELEASE_EXECUTION_v0.2.0.md`

## 说明

中文文档会优先覆盖常用能力与配置示例；当中文和英文内容存在差异时，请以 `README.md` 的完整说明为准。

## 项目文档

- 文档索引：`docs/INDEX.md`
- 维护者指南：`docs/MAINTAINER_GUIDE.md`
- 接收端运维指引：`docs/RECEIVER_OPERATIONS.md`
- 路线图：`docs/ROADMAP.md`
- 发布检查清单：`docs/RELEASE_CHECKLIST.md`
- `v0.2.0` 发布执行指引：`docs/RELEASE_EXECUTION_v0.2.0.md`
- `v0.2.0` 发布运行手册：`docs/RELEASE_RUNBOOK_v0.2.0.md`
- `v0.2.0` 发布说明草稿：`docs/releases/RELEASE_NOTES_v0.2.0.md`
- `v0.2.0` 发布演练记录：`docs/RELEASE_DRY_RUN_v0.2.0.md`
- `v0.2.0` 英文发布公告：`docs/RELEASE_ANNOUNCEMENT_v0.2.0.md`
- `v0.2.0` 中文发布公告：`docs/RELEASE_ANNOUNCEMENT_v0.2.0.zh-CN.md`
- GitHub Release 可直接复制文案：`docs/GITHUB_RELEASE_COPY_v0.2.0.md`
- GitHub 仓库元数据建议：`docs/GITHUB_METADATA.md`
