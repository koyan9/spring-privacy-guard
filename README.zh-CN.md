# spring-privacy-guard

[English](README.md) | [简体中文](README.zh-CN.md)

[![CI](https://github.com/koyan9/spring-privacy-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/koyan9/spring-privacy-guard/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/koyan9/spring-privacy-guard?display_name=tag)](https://github.com/koyan9/spring-privacy-guard/releases/latest)
[![Changelog](https://img.shields.io/badge/Changelog-0.3.0-0f766e)](CHANGELOG.md)
[![Release Notes](https://img.shields.io/badge/Release%20Notes-v0.3.0-1d4ed8)](docs/releases/RELEASE_NOTES_v0.3.0.md)
[![Security](https://img.shields.io/badge/Security-Policy-7f1d1d)](SECURITY.md)
[![Support](https://img.shields.io/badge/Support-Guide-1f2937)](SUPPORT.md)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-orange)](README.md#quick-start)
[![Docs Index](https://img.shields.io/badge/Docs-Index-6f42c1)](docs/INDEX.md)
[![English Docs](https://img.shields.io/badge/Docs-English-blue)](README.md)

一个面向 Spring Boot 的隐私安全 Starter，用于敏感数据脱敏、日志清洗、隐私审计跟踪，以及可安全运维的死信、告警和 receiver 验签流程。

## 当前状态

- 最新已发布版本：`v0.3.0`
- 当前分支准备中的下一版候选：`v0.4.0`
- 当前未发布开发重点：`v0.3.0` 之后的多租户补强，包括 observability、tenant-scoped dead-letter management、receiver routing，以及多实例 sample workflow
- 本开发周期已完成：
  - tenant-native dead-letter replay
  - stable tenant logging policy
  - tenant dead-letter observability policy
  - tenant operational telemetry
  - import/export/manifest exchange-path telemetry
  - built-in / fallback / custom native / custom JDBC / production-like sample 对照矩阵
- 当前进度建议优先阅读：`CHANGELOG.md`、`docs/ROADMAP.md`、`docs/INDEX.md`

## 模块说明

- `privacy-guard-core/`
  - 脱敏注解、内置敏感类型、文本清洗、脱敏 SPI
- `privacy-guard-spring-boot-starter/`
  - Spring Boot 自动配置、Jackson 集成、Logback 集成、审计存储、死信处理、告警、receiver 验签
- `samples/privacy-demo/`
  - 可运行示例，覆盖 masking、audit、dead-letter、receiver verification、replay-store、native/fallback/custom repository 对照

## 核心能力

- 基于 `@SensitiveData` 的字段级脱敏
- 自定义 `MaskingStrategy` 与 `PrivacyTenantAwareMaskingStrategy`
- 文本日志清洗与 JSON 输出脱敏
- 审计记录、查询、分页、排序、统计
- `IN_MEMORY` 与 `JDBC` 审计 / 死信仓储
- 异步发布、批量写入、重试、死信回退、重放、导入导出
- Actuator 健康检查与 Micrometer 指标
- webhook / email 告警
- receiver 验签、replay-store、防重放
- 多租户策略、tenant-native SPI、native / fallback 可观测性

## 快速开始

环境要求：

- Java `17+`
- Spring Boot `3.3.x`

如果你使用当前已发布版本，依赖示例为：

```xml
<dependency>
    <groupId>io.github.koyan9</groupId>
    <artifactId>spring-privacy-guard-spring-boot-starter</artifactId>
    <version>0.3.0</version>
</dependency>
```

示例模型：

```java
public record PatientView(
        @SensitiveData(type = SensitiveType.NAME) String patientName,
        @SensitiveData(type = SensitiveType.PHONE) String phone,
        @SensitiveData(type = SensitiveType.ID_CARD) String idCard,
        @SensitiveData(type = SensitiveType.EMAIL) String email
) {
}
```

最小配置：

```yaml
privacy:
  guard:
    fallback-mask-char: "*"
    audit:
      repository-type: IN_MEMORY
```

建议阅读路径：

- 基础接入：先完成依赖与最小配置，再阅读下方常用配置
- 多租户接入：`docs/MULTI_TENANT_GUIDE.md`
- 租户可观测性与 rollout：`docs/TENANT_OBSERVABILITY_GUIDE.md`
- 可运行 sample 与多实例演练：`samples/privacy-demo/README.md`
- 当前进度与下一步计划：`CHANGELOG.md`、`docs/ROADMAP.md`

## 常用配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `privacy.guard.enabled` | `true` | Starter 总开关 |
| `privacy.guard.logging.enabled` | `true` | 启用日志清洗 |
| `privacy.guard.logging.mdc.enabled` | `false` | 启用 MDC 字段脱敏 |
| `privacy.guard.logging.structured.enabled` | `false` | 启用 structured logging 字段脱敏 |
| `privacy.guard.tenant.enabled` | `false` | 启用多租户策略 |
| `privacy.guard.tenant.header-name` | `X-Privacy-Tenant` | 当前租户请求头 |
| `privacy.guard.tenant.default-tenant` | 空 | 默认租户 |
| `privacy.guard.audit.enabled` | `true` | 启用审计能力 |
| `privacy.guard.audit.repository-type` | `NONE` | 审计仓储类型：`NONE` / `IN_MEMORY` / `JDBC` |
| `privacy.guard.audit.dead-letter.repository-type` | `NONE` | 死信仓储类型 |
| `privacy.guard.audit.dead-letter.observability.health.warning-threshold` | `1` | backlog warning 阈值 |
| `privacy.guard.audit.dead-letter.observability.health.down-threshold` | `100` | backlog down 阈值 |
| `privacy.guard.audit.dead-letter.observability.alert.enabled` | `false` | 启用死信告警 |
| `privacy.guard.audit.dead-letter.observability.alert.tenant.enabled` | `false` | 启用租户级死信告警 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.verification.enabled` | `false` | 启用 receiver 验签配置 |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.namespace` | 空 | replay-store namespace |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.redis.enabled` | `false` | 启用 Redis replay-store |
| `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.enabled` | `false` | 启用 JDBC replay-store |
| `privacy.guard.audit.jdbc.tenant-column-name` | 空 | 审计 JDBC tenant 列 |
| `privacy.guard.audit.dead-letter.jdbc.tenant-column-name` | 空 | 死信 JDBC tenant 列 |

完整属性矩阵和详细说明请参考英文 README 与配置元数据。

## 自定义脱敏

当内置 `SensitiveType` 不够用时，可以实现 `MaskingStrategy`：

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

## 稳定 SPI

当前 minor 版本线内承诺兼容的扩展点使用 `@StableSpi` 标记。

主要稳定接口包括：

- `MaskingStrategy`
- `PrivacyTenantProvider`
- `PrivacyTenantContextScope`
- `PrivacyTenantContextSnapshot`
- `PrivacyTenantPolicyResolver`
- `PrivacyTenantAwareMaskingStrategy`
- `PrivacyTenantAuditPolicyResolver`
- `PrivacyTenantLoggingPolicyResolver`
- `PrivacyTenantDeadLetterObservabilityPolicyResolver`
- `PrivacyTenantAuditReadRepository`
- `PrivacyTenantAuditDeadLetterReadRepository`
- `PrivacyTenantAuditWriteRepository`
- `PrivacyTenantAuditDeadLetterWriteRepository`
- `PrivacyTenantAuditDeadLetterDeleteRepository`
- `PrivacyTenantAuditDeadLetterReplayRepository`
- `PrivacyAuditPublisher`
- `PrivacyAuditRepository`
- `PrivacyAuditQueryRepository`
- `PrivacyAuditStatsRepository`
- `PrivacyAuditDeadLetterRepository`
- `PrivacyAuditDeadLetterStatsRepository`
- `PrivacyAuditDeadLetterHandler`
- `PrivacyAuditDeadLetterAlertCallback`
- `PrivacyAuditDeadLetterWebhookReplayStore`
- `PrivacyAuditDeadLetterWebhookAlertTelemetry`
- `PrivacyAuditDeadLetterWebhookVerificationTelemetry`

直接耦合的 carrier type 也带有稳定标记，例如：

- `PrivacyTenantPolicy`
- `PrivacyTenantAuditPolicy`
- `PrivacyTenantLoggingPolicy`
- `PrivacyTenantDeadLetterObservabilityPolicy`
- `PrivacyAuditEvent`
- `PrivacyAuditQueryCriteria`
- `PrivacyAuditDeadLetterEntry`

## 多租户策略

当前多租户策略覆盖四个面：

- masking / text
- audit detail filtering
- logging selection
- dead-letter observability thresholds / recovery notification

示例：

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
          audit:
            include-detail-keys:
              - phone
              - employeeCode
            attach-tenant-id: true
            tenant-detail-key: tenant
          logging:
            mdc:
              enabled: true
              include-keys:
                - email
                - phone
          observability:
            dead-letter:
              warning-threshold: 1
              down-threshold: 2
              notify-on-recovery: true
```

补充说明：

- `include-detail-keys` 先于 `exclude-detail-keys`
- `attach-tenant-id=true` 时，租户标识会在清洗后附加到 detail 中
- tenant logging override 会覆盖全局 MDC / structured key 选择
- tenant dead-letter observability override 只作用于 tenant backlog snapshot、tenant health summary 和 tenant alert monitor

## 审计与死信

Starter 提供：

- 审计写入与查询
- 死信记录、统计、删除、重放
- JSON / CSV 导入导出
- tenant-scoped query / stats / delete / replay / exchange
- 统一管理入口：`PrivacyTenantAuditManagementService`

典型接口：

- `GET /audit-events`
- `GET /audit-events/stats`
- `GET /audit-dead-letters`
- `GET /audit-dead-letters/stats`
- `DELETE /audit-dead-letters/{id}`
- `POST /audit-dead-letters/{id}/replay`
- `GET /audit-dead-letters/export.json`
- `GET /audit-dead-letters/export.manifest`
- `POST /audit-dead-letters/import.json`

## 可观测性与告警

当前可观测性重点包括：

- 全局 dead-letter health / metrics
- tenant read/write path counters
- tenant alert transitions / delivery outcomes
- receiver route failure counters
- exchange-path telemetry：
  - `dead_letter_export`
  - `dead_letter_manifest`
  - `dead_letter_import`

典型指标：

- `privacy.audit.tenant.read.path{domain=*,path=*}`
- `privacy.audit.tenant.write.path{domain=*,path=*}`
- `privacy.audit.deadletters.alert.tenant.transitions{tenant=*,state=*,recovery=*}`
- `privacy.audit.deadletters.alert.tenant.deliveries{tenant=*,channel=*,outcome=*}`
- `privacy.audit.deadletters.receiver.route.failures{route=*,reason=*}`

receiver 验签失败会返回：

```json
{"error":"Invalid signature","reason":"INVALID_SIGNATURE"}
```

## Sample 矩阵

`samples/privacy-demo` 当前可以验证以下路径：

| Profile | 目的 | 期望路径 |
| --- | --- | --- |
| 默认 profile | built-in in-memory 基线 | native |
| `fallback-tenant` | generic-only 对照 | fallback |
| `custom-tenant-native` | 自定义 tenant SPI 参考 | native |
| `jdbc-tenant` | built-in JDBC 参考 | native |
| `custom-jdbc-tenant` | 自定义 JDBC tenant SPI 参考 | native |
| `custom-jdbc-tenant,custom-jdbc-tenant-node2` | 自定义 JDBC 双节点演练 | native |
| `postgres-redis-tenant,postgres-redis-tenant-node2` | PostgreSQL + Redis 生产近似双节点 | native |

`/demo-tenants/observability` 当前会展示：

- `repositoryImplementations`
- `repositoryCapabilities`
- tenant path counters
- tenant alert metrics
- replay-store backend
- backlog 视图

## 本地开发

- 全量校验：`./mvnw -q verify` 或 `mvnw.cmd -q verify`
- 安装本地构件：`./mvnw -q -DskipTests install`
- 运行 sample：`./mvnw -q -f samples/privacy-demo/pom.xml spring-boot:run`

常用 sample profile：

- `jdbc-tenant`
- `redis-tenant`
- `postgres-redis-tenant`
- `fallback-tenant`
- `custom-tenant-native`
- `custom-jdbc-tenant`

## 维护者快速入口

- 支持与分流：`SUPPORT.md`
- 安全策略：`SECURITY.md`
- 文档索引：`docs/INDEX.md`
- 发布检查清单：`docs/RELEASE_CHECKLIST.md`
- 当前已发布版本发布资料：`docs/releases/RELEASE_NOTES_v0.3.0.md`
- 下一版草案发布资料：`docs/releases/RELEASE_NOTES_v0.4.0.md`

## 项目文档

- 文档索引：`docs/INDEX.md`
- 多租户指南：`docs/MULTI_TENANT_GUIDE.md`
- 租户可观测性指南：`docs/TENANT_OBSERVABILITY_GUIDE.md`
- 租户采纳手册：`docs/TENANT_ADOPTION_PLAYBOOK.md`
- JDBC 生产指南：`docs/JDBC_PRODUCTION_GUIDE.md`
- Receiver 运维指南：`docs/RECEIVER_OPERATIONS.md`
- 维护者指南：`docs/MAINTAINER_GUIDE.md`
- 路线图：`docs/ROADMAP.md`
- 发布检查清单：`docs/RELEASE_CHECKLIST.md`
- 当前已发布 release notes：`docs/releases/RELEASE_NOTES_v0.3.0.md`
- 下一版草案 release notes：`docs/releases/RELEASE_NOTES_v0.4.0.md`

## 发布说明

发布工作流优先读取：

- `docs/releases/RELEASE_NOTES_<tag>.md`

如果不存在，则回退到：

- `docs/releases/RELEASE_NOTES_TEMPLATE.md`
