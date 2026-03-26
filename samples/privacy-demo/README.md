# privacy-demo

[English](../../README.md) | [简体中文](../../README.zh-CN.md)

[![Project README](https://img.shields.io/badge/Project-README-0ea5e9)](../../README.md)
[![Docs Index](https://img.shields.io/badge/Docs-Index-6f42c1)](../../docs/INDEX.md)
[![Release Notes](https://img.shields.io/badge/Release%20Notes-v0.3.0-1d4ed8)](../../docs/releases/RELEASE_NOTES_v0.3.0.md)

`samples/privacy-demo` 是 `spring-privacy-guard` 的可运行示例，展示敏感数据脱敏、审计、死信处理、签名告警、receiver 验签和 replay 防护。

当前示例中的审计与死信管理接口统一通过 starter 提供的 `PrivacyTenantAuditManagementService` 进入多租户管理链路。

示例还提供了专门的租户管理入口：

- `GET /demo-tenants/current`
- `GET /demo-tenants/policies`
- `GET /demo-tenants/observability`

## Current Status

- Latest published project release: `v0.3.0`
- The sample is already aligned with the current unreleased multi-tenant work:
  - tenant-aware management flows go through `PrivacyTenantAuditManagementService`
  - `/demo-tenants/observability` summarizes tenant read/write paths, backlog state, alerting, and receiver replay-store backend
  - `/demo-tenants/observability` now also shows repository tenant capability flags and whether dead-letter import used a native tenant-aware write path
  - `/demo-tenants/observability` also distinguishes exchange-path reads for dead-letter export and manifest generation
  - built-in `IN_MEMORY` and `JDBC` dead-letter repositories now report `deadLetterReplay.native`
  - `fallback-tenant` can be used as a local comparison profile when you want to see `fallback` counters instead of native tenant SPI paths
  - `custom-tenant-native` can be used as a local custom SPI reference when you want native tenant paths without depending on the built-in repositories
  - `custom-jdbc-tenant` can be used as a custom JDBC SPI reference when you want native tenant paths with sample-owned JDBC repository beans
  - `custom-jdbc-tenant-node2` and the matching verification scripts extend that custom JDBC SPI reference into a two-node local rehearsal
  - tenant policies can also override dead-letter warning/down thresholds and recovery notifications per tenant
  - the sample now includes a PostgreSQL + Redis production-like rollout recipe with two-node overlays and verification helpers
- Recommended doc path for this sample:
  - project status and progress: `../../CHANGELOG.md`
  - next development priorities: `../../docs/ROADMAP.md`
  - tenant integration details: `../../docs/MULTI_TENANT_GUIDE.md`
  - tenant observability and rollout checks: `../../docs/TENANT_OBSERVABILITY_GUIDE.md`

## 演示内容

- `@SensitiveData` 字段脱敏
- 自定义 `MaskingStrategy` / `PrivacyTenantAwareMaskingStrategy` 示例
- 审计事件查询、统计与管理操作留痕
- 死信查询、清理、重放、JSON/CSV 导入导出
- 内置 webhook / email 告警配置
- 签名 receiver、replay-store 管理与统计
- filter / interceptor 两种 receiver 验签模式

## Receiver 运维

Replay-store 运维说明与指标参考：

- `docs/RECEIVER_OPERATIONS.md`

单实例文件型 replay-store 示例：
```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            receiver:
              replay-store:
                namespace: tenant-a-receiver
                file:
                  enabled: true
                  path: ./target/demo-alert-replay-store.json
```



JDBC replay-store 示例配置（适合多实例部署）：

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            receiver:
              replay-store:
                namespace: tenant-a-receiver
                jdbc:
                  enabled: true
                  initialize-schema: true
                  table-name: privacy_audit_webhook_replay_store
                  cleanup-interval: 5m
```

`cleanup-interval` 控制全量清理频率，设置为 `0` 时每次请求都会触发清理。
生产环境中的 MySQL / PostgreSQL schema 管理、索引建议和迁移步骤，请参考 `../../docs/JDBC_PRODUCTION_GUIDE.md`。

Redis replay-store 示例配置（适合已有 Redis 基础设施的多实例部署）：

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            receiver:
              replay-store:
                namespace: tenant-a-receiver
                redis:
                  enabled: true
                  key-prefix: privacy:demo:webhook:replay:
                  scan-batch-size: 500
```

常用端点：

- `GET /demo-alert-receiver/replay-store?limit=20&offset=0`
- `GET /demo-alert-receiver/replay-store/stats?expiringWithin=PT5M`
- `DELETE /demo-alert-receiver/replay-store`

示例默认将 replay-store namespace 配置为 `demo-default`，因此管理接口会同时展示逻辑 `nonce` 和底层 `storageKey`。
如果你将同一个 replay-store 后端复用于多个 tenant-specific receiver 部署，建议为每个部署配置不同的 namespace。

## 多租户脱敏演示

示例默认开启基于 `X-Privacy-Tenant` 请求头的租户策略：

- 不传请求头或使用 `public`：保持默认脱敏字符
- `X-Privacy-Tenant: tenant-a`：使用 `#` 作为 fallback mask char，启用 `EMP\\d{4}` 文本规则，在审计 detail 中只保留 `phone`、`employeeCode` 与 `tenant`，并额外启用租户级 MDC 脱敏选择，仅清洗 `email` 和 `phone`
- `X-Privacy-Tenant: tenant-b`：使用 `X` 作为 fallback mask char，在审计 detail 中只保留 `phone` 与 `tenant`，并额外启用租户级 structured logging 脱敏选择，仅清洗 `phone`

完整的请求头约定、稳定 SPI 扩展点和 `PrivacyTenantAuditManagementService` 管理入口说明，请参考 `../../docs/MULTI_TENANT_GUIDE.md`。

示例请求：

- `curl -H "X-Privacy-Tenant: tenant-a" http://localhost:8088/patients/demo`
- `curl -H "X-Privacy-Tenant: tenant-b" http://localhost:8088/patients/demo`
- `curl http://localhost:8088/demo-tenants/current`
- `curl -H "X-Privacy-Tenant: tenant-a" http://localhost:8088/demo-tenants/current`
- 触发后可访问 `GET /audit-events?action=PATIENT_READ&tenant=tenant-a`
- 或 `GET /audit-events/stats?action=PATIENT_READ&tenant=tenant-a`
- 用于查看不同租户落库的审计 detail 与统计差异
- 若存在租户标签的死信，还可访问 `GET /audit-dead-letters?tenant=tenant-a`
- 或 `GET /audit-dead-letters/stats?tenant=tenant-a`
- 批量管理时可使用 `DELETE /audit-dead-letters?tenant=tenant-a`
- 或 `POST /audit-dead-letters/replay?tenant=tenant-b`
- 交换链路可使用 `GET /audit-dead-letters/export.json?tenant=tenant-a`
- `GET /audit-dead-letters/export.manifest?format=json&tenant=tenant-a`
- `POST /audit-dead-letters/import.json?tenant=tenant-b`

## 运行前准备

先在仓库根目录安装本地构件：

- Windows：`mvnw.cmd -q -DskipTests install`
- macOS / Linux：`./mvnw -q -DskipTests install`

## 运行示例

- Windows：`mvnw.cmd spring-boot:run`
- macOS / Linux：`./mvnw spring-boot:run`
- 根目录运行：`./mvnw -q -f samples/privacy-demo/pom.xml spring-boot:run`
- Windows JDBC tenant profile：`mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=jdbc-tenant`
- macOS / Linux JDBC tenant profile：`./mvnw spring-boot:run -Dspring-boot.run.profiles=jdbc-tenant`
- Windows JDBC tenant node 2 overlay：`mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=jdbc-tenant,jdbc-tenant-node2`
- macOS / Linux JDBC tenant node 2 overlay：`./mvnw spring-boot:run -Dspring-boot.run.profiles=jdbc-tenant,jdbc-tenant-node2`
- Windows Redis tenant profile：`mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=redis-tenant`
- macOS / Linux Redis tenant profile：`./mvnw spring-boot:run -Dspring-boot.run.profiles=redis-tenant`
- Windows Redis tenant node 2 overlay：`mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=redis-tenant,redis-tenant-node2`
- macOS / Linux Redis tenant node 2 overlay：`./mvnw spring-boot:run -Dspring-boot.run.profiles=redis-tenant,redis-tenant-node2`
- Windows fallback tenant profile：`mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=fallback-tenant`
- macOS / Linux fallback tenant profile：`./mvnw spring-boot:run -Dspring-boot.run.profiles=fallback-tenant`
- Windows custom tenant-native profile：`mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=custom-tenant-native`
- macOS / Linux custom tenant-native profile：`./mvnw spring-boot:run -Dspring-boot.run.profiles=custom-tenant-native`
- Windows custom JDBC tenant profile：`mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=custom-jdbc-tenant`
- macOS / Linux custom JDBC tenant profile：`./mvnw spring-boot:run -Dspring-boot.run.profiles=custom-jdbc-tenant`
- Windows custom JDBC tenant node 2 overlay：`mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=custom-jdbc-tenant,custom-jdbc-tenant-node2`
- macOS / Linux custom JDBC tenant node 2 overlay：`./mvnw spring-boot:run -Dspring-boot.run.profiles=custom-jdbc-tenant,custom-jdbc-tenant-node2`

默认地址：`http://localhost:8088`

## 常用接口

公开接口：

- `GET /demo-tenants/current`
- `GET /patients/demo`
- `GET /audit-events`
- `GET /audit-events/stats`
- `GET /audit-events?action=PATIENT_READ&tenant=tenant-a`
- `GET /audit-events/stats?action=PATIENT_READ&tenant=tenant-a`
- `POST /demo-alert-receiver`

需要 `X-Demo-Admin-Token: demo-admin-token` 的管理接口：

- `GET /demo-tenants/policies`
- `GET /demo-tenants/observability`
- `GET /audit-dead-letters`
- `GET /audit-dead-letters/stats`
- `GET /audit-dead-letters?tenant=tenant-a`
- `GET /audit-dead-letters/stats?tenant=tenant-a`
- `DELETE /audit-dead-letters?tenant=tenant-a`
- `POST /audit-dead-letters/replay?tenant=tenant-b`
- `GET /audit-dead-letters/export.json`
- `GET /audit-dead-letters/export.csv`
- `GET /audit-dead-letters/export.manifest?format=json`
- `GET /audit-dead-letters/export.json?tenant=tenant-a`
- `GET /audit-dead-letters/export.manifest?format=json&tenant=tenant-a`
- `POST /audit-dead-letters/import.json`
- `POST /audit-dead-letters/import.csv`
- `POST /audit-dead-letters/import.json?tenant=tenant-b`
- `DELETE /audit-dead-letters/{id}`
- `POST /audit-dead-letters/{id}/replay`
- `POST /audit-dead-letters/replay?limit=100`
- `GET /demo-alert-receiver/last`
- `GET /demo-alert-receiver/replay-store?limit=20&offset=0`
- `GET /demo-alert-receiver/replay-store/stats?expiringWithin=PT5M`
- `DELETE /demo-alert-receiver/replay-store`

Actuator 指标示例：

- `GET /actuator/health/privacyAuditDeadLetters`
- `GET /actuator/health`
- `GET /actuator/metrics/privacy.audit.deadletters.total`
- `GET /actuator/metrics/privacy.audit.deadletters.state?tag=state:warning`
- `GET /actuator/metrics/privacy.audit.deadletters.threshold?tag=level:down`
- `GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.count`
- `GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.expiring_soon`
- `GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.expiry_seconds?tag=kind:earliest`
- `GET /actuator/metrics/privacy.audit.tenant.read.path?tag=domain:audit&tag=path:native`
- `GET /actuator/metrics/privacy.audit.tenant.write.path?tag=domain:audit_write&tag=path:native`
- `GET /demo-tenants/observability`

`/demo-tenants/observability` 现在除了 tenant path 指标，还会返回全局、当前 tenant 以及按已配置 tenant 聚合的 dead-letter backlog 状态视图。
示例默认还开启了 tenant-scoped dead-letter alert monitor，并在响应中附带最近一次 tenant alert 的 tenantId 与快照。

## JDBC 双实例本地参考

`jdbc-tenant` profile 现在使用共享的文件型 H2 数据库和内置 JDBC replay-store，适合作为本地双实例 tenant rollout 参考。

推荐验证顺序：

1. 用 `jdbc-tenant` 启动 node 1。
2. 用 `jdbc-tenant,jdbc-tenant-node2` 启动 node 2。
3. 访问两个实例的 `/demo-tenants/current`，确认 `instanceId` 分别是 `node-1` 和 `node-2`。
4. 先向 node 1 发送一次 signed alert，再把完全相同的 nonce 请求发送到 node 2。
5. 确认 node 2 拒绝 replayed nonce，并且两个实例通过 `/demo-tenants/observability` 展示同一份共享 replay-store / backlog 状态。

## Receiver 验证脚本

可直接运行：

## Redis Multi-Instance Local Recipe

The `redis-tenant` profile is the sample's shared external replay-store reference for teams that already operate Redis.

Recommended local flow:

1. Start Redis on `localhost:6379`.
2. Start node 1 with `redis-tenant`.
3. Start node 2 with `redis-tenant,redis-tenant-node2`.
4. Verify `/demo-tenants/current` returns `instanceId=redis-node-1` on port `8088` and `instanceId=redis-node-2` on port `8090`.
5. Send one signed alert to node 1, then replay the exact same nonce against node 2 and confirm replay protection is shared.

If you want a helper to boot Redis locally with Docker first, use:

- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/manage-redis-local.ps1`
- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/manage-redis-local.ps1 -Action down`
- `bash samples/privacy-demo/scripts/manage-redis-local.sh`
- `bash samples/privacy-demo/scripts/manage-redis-local.sh down`
- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/verify-alert-receiver.ps1`
- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/verify-alert-receiver.ps1 -BaseUrl http://localhost:8088 -BearerToken demo-receiver-token -SignatureSecret demo-receiver-secret -AdminToken demo-admin-token`
- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/verify-jdbc-tenant-multi-instance.ps1`
- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/verify-jdbc-tenant-multi-instance.ps1 -Node1BaseUrl http://localhost:8088 -Node2BaseUrl http://localhost:8089 -AdminToken demo-admin-token`
- `bash samples/privacy-demo/scripts/verify-jdbc-tenant-multi-instance.sh`
- `bash samples/privacy-demo/scripts/verify-jdbc-tenant-multi-instance.sh --node1-base-url http://localhost:8088 --node2-base-url http://localhost:8089 --admin-token demo-admin-token`
- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/verify-redis-tenant-multi-instance.ps1`
- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/verify-redis-tenant-multi-instance.ps1 -Node1BaseUrl http://localhost:8088 -Node2BaseUrl http://localhost:8090 -AdminToken demo-admin-token`
- `bash samples/privacy-demo/scripts/verify-redis-tenant-multi-instance.sh`
- `bash samples/privacy-demo/scripts/verify-redis-tenant-multi-instance.sh --node1-base-url http://localhost:8088 --node2-base-url http://localhost:8090 --admin-token demo-admin-token`

验签失败会返回包含原因码的 JSON 响应，例如：`{"error":"Invalid signature","reason":"INVALID_SIGNATURE"}`。

## PostgreSQL + Redis Production-Like Recipe

The `postgres-redis-tenant` profile pair is the sample's closest built-in approximation of a production multi-instance rollout:

1. Boot PostgreSQL, Redis, and both sample nodes together with `samples/privacy-demo/docker-compose.postgres-redis.yml`.
2. Node 1 uses `postgres-redis-tenant`.
3. Node 2 uses `postgres-redis-tenant,postgres-redis-tenant-node2`.
4. Both nodes share PostgreSQL for audit/dead-letter storage and Redis for replay-store protection.

Useful commands:

- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/manage-postgres-redis-local.ps1`
- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/manage-postgres-redis-local.ps1 -Action down`
- `bash samples/privacy-demo/scripts/manage-postgres-redis-local.sh`
- `bash samples/privacy-demo/scripts/manage-postgres-redis-local.sh down`
- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/verify-postgres-redis-tenant-multi-instance.ps1`
- `bash samples/privacy-demo/scripts/verify-postgres-redis-tenant-multi-instance.sh`

The observability summary should report:

- `auditRepositoryType=JDBC`
- `deadLetterRepositoryType=JDBC`
- `receiverReplayStore.backend=REDIS`
- `repositoryCapabilities.deadLetter.tenantImportNative=true`

## Custom JDBC Tenant Multi-Instance Recipe

The `custom-jdbc-tenant` profile pair is the sample's local reference for user-owned JDBC repository beans that still satisfy the tenant SPI:

1. Start node 1 with `custom-jdbc-tenant`.
2. Start node 2 with `custom-jdbc-tenant,custom-jdbc-tenant-node2`.
3. Both nodes share the same file-backed H2 database and JDBC replay-store.
4. `/demo-tenants/observability` should report `repositoryImplementations.audit=CustomJdbcTenantAuditRepository` and `repositoryImplementations.deadLetter=CustomJdbcTenantDeadLetterRepository`.

Useful commands:

- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/verify-custom-jdbc-tenant-multi-instance.ps1`
- `bash samples/privacy-demo/scripts/verify-custom-jdbc-tenant-multi-instance.sh`

## 关键配置

管理接口保护：

```yaml
demo:
  admin:
    token: demo-admin-token
    header-name: X-Demo-Admin-Token
```

receiver 验签：

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
                signature-algorithm: HmacSHA256
                signature-header: X-Privacy-Alert-Signature
                timestamp-header: X-Privacy-Alert-Timestamp
                nonce-header: X-Privacy-Alert-Nonce
                max-skew: 5m
```

内置 webhook 告警：

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            enabled: true
            webhook:
              url: http://localhost:8088/demo-alert-receiver
              bearer-token: demo-receiver-token
              signature-secret: demo-receiver-secret
              signature-algorithm: HmacSHA256
              signature-header: X-Privacy-Alert-Signature
              timestamp-header: X-Privacy-Alert-Timestamp
              nonce-header: X-Privacy-Alert-Nonce
              max-attempts: 3
              backoff: 200ms
              connect-timeout: 2s
              read-timeout: 5s
```

内置 email 告警：

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
              from: privacy-demo@example.com
              to: ops@example.com
              subject-prefix: [privacy-demo]
```

## 模式切换

默认使用 receiver filter 模式。

切换到 interceptor 模式：

- Windows：`mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=interceptor`
- macOS / Linux：`./mvnw spring-boot:run -Dspring-boot.run.profiles=interceptor`

对应 profile 文件：`samples/privacy-demo/src/main/resources/application-interceptor.yml`

拦截器模式配置文件已包含 replay-store JDBC 开关示例，可按需启用。
JDBC tenant recipe 对应 profile 文件：`samples/privacy-demo/src/main/resources/application-jdbc-tenant.yml`

默认 sample profile 使用的 `IN_MEMORY` 仓储已经实现 tenant-aware 读写 SPI，因此 `/demo-tenants/observability` 与上面的 Actuator 查询会累计到 `native` 路径。
`fallback` 路径主要用于仅支持通用查询/持久化、尚未实现 tenant-aware SPI 的自定义仓储；切换到 `jdbc-tenant` profile 时，内置 JDBC tenant-aware 仓储同样会继续累计到 `native` 路径。

## 说明

该示例保留 `DemoDeadLetterAlertCallback` 仅用于测试中观测最近一次告警；webhook / email 发送和 receiver 验签都优先演示 starter 内置能力。
