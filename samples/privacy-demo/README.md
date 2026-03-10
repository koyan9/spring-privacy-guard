# privacy-demo

[English](../../README.md) | [简体中文](../../README.zh-CN.md)

[![Project README](https://img.shields.io/badge/Project-README-0ea5e9)](../../README.md)
[![Docs Index](https://img.shields.io/badge/Docs-Index-6f42c1)](../../docs/INDEX.md)
[![Release Notes](https://img.shields.io/badge/Release%20Notes-v0.2.0-1d4ed8)](../../docs/releases/RELEASE_NOTES_v0.2.0.md)

`samples/privacy-demo` 是 `spring-privacy-guard` 的可运行示例，展示敏感数据脱敏、审计、死信处理、签名告警、receiver 验签和 replay 防护。

## 演示内容

- `@SensitiveData` 字段脱敏
- 自定义 `MaskingStrategy` 示例
- 审计事件查询、统计与管理操作留痕
- 死信查询、清理、重放、JSON/CSV 导入导出
- 内置 webhook / email 告警配置
- 签名 receiver、replay-store 管理与统计
- filter / interceptor 两种 receiver 验签模式

## Receiver 运维

Replay-store 运维说明与指标参考：

- `docs/RECEIVER_OPERATIONS.md`

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
                jdbc:
                  enabled: true
                  initialize-schema: true
                  table-name: privacy_audit_webhook_replay_store
```

常用端点：

- `GET /demo-alert-receiver/replay-store?limit=20&offset=0`
- `GET /demo-alert-receiver/replay-store/stats?expiringWithin=PT5M`
- `DELETE /demo-alert-receiver/replay-store`

## 运行前准备

先在仓库根目录安装本地构件：

- Windows：`mvnw.cmd -q -DskipTests install`
- macOS / Linux：`./mvnw -q -DskipTests install`

## 运行示例

- Windows：`mvnw.cmd spring-boot:run`
- macOS / Linux：`./mvnw spring-boot:run`
- 根目录运行：`./mvnw -q -f samples/privacy-demo/pom.xml spring-boot:run`

默认地址：`http://localhost:8088`

## 常用接口

公开接口：

- `GET /patients/demo`
- `GET /audit-events`
- `GET /audit-events/stats`
- `POST /demo-alert-receiver`

需要 `X-Demo-Admin-Token: demo-admin-token` 的管理接口：

- `GET /audit-dead-letters`
- `GET /audit-dead-letters/stats`
- `GET /audit-dead-letters/export.json`
- `GET /audit-dead-letters/export.csv`
- `GET /audit-dead-letters/export.manifest?format=json`
- `POST /audit-dead-letters/import.json`
- `POST /audit-dead-letters/import.csv`
- `DELETE /audit-dead-letters/{id}`
- `POST /audit-dead-letters/{id}/replay`
- `POST /audit-dead-letters/replay?limit=100`
- `GET /demo-alert-receiver/last`
- `GET /demo-alert-receiver/replay-store?limit=20&offset=0`
- `GET /demo-alert-receiver/replay-store/stats?expiringWithin=PT5M`
- `DELETE /demo-alert-receiver/replay-store`

Actuator 指标示例：

- `GET /actuator/health/privacyAuditDeadLetters`
- `GET /actuator/metrics/privacy.audit.deadletters.total`
- `GET /actuator/metrics/privacy.audit.deadletters.state?tag=state:warning`
- `GET /actuator/metrics/privacy.audit.deadletters.threshold?tag=level:down`
- `GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.count`
- `GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.expiring_soon`
- `GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.expiry_seconds?tag=kind:earliest`

## Receiver 验证脚本

可直接运行：

- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/verify-alert-receiver.ps1`
- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/verify-alert-receiver.ps1 -BaseUrl http://localhost:8088 -BearerToken demo-receiver-token -SignatureSecret demo-receiver-secret -AdminToken demo-admin-token`

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
demo:
  alert:
    receiver:
      bearer-token: demo-receiver-token
      signature-secret: demo-receiver-secret
      signature-algorithm: HmacSHA256
      signature-header: X-Privacy-Alert-Signature
      timestamp-header: X-Privacy-Alert-Timestamp
      nonce-header: X-Privacy-Alert-Nonce
      max-skew: 5m
      # store-file: ./target/demo-alert-replay-store.json
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

## 说明

该示例保留 `DemoDeadLetterAlertCallback` 仅用于测试中观测最近一次告警；webhook / email 发送和 receiver 验签都优先演示 starter 内置能力。
