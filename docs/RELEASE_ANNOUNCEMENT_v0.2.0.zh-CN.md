# 发布公告：v0.2.0

本文档可作为 `spring-privacy-guard v0.2.0` 的对外发布公告简版文案。

## 一句话介绍

`spring-privacy-guard v0.2.0` 新增可扩展脱敏 SPI、增强审计与死信处理能力、内置签名告警投递与接收端验签能力，并补齐更完整的可观测性支持。

## 版本亮点

- 新增 `MaskingStrategy` 与 `MaskingContext`，支持自定义脱敏策略扩展
- 新增异步与批量审计投递能力，支持失败重试和死信回退
- 增强死信处理链路，支持查询、重放、清理、导入导出和校验
- 内置 webhook / email 死信阈值告警回调
- 新增签名 receiver 验签能力，支持 filter 与 interceptor 两种接入方式
- 增强 Actuator 与 Micrometer 指标覆盖，提升发送端与接收端可观测性

## 建议用于 GitHub / 社区的发布文案

`spring-privacy-guard v0.2.0` 已正式发布。

本次版本重点增强了可扩展性与生产可运维能力：新增自定义脱敏 SPI，补强异步/批量审计投递与死信处理链路，内置 webhook 和 email 告警回调，同时支持接收端签名校验与更完整的健康检查、指标观测能力。

如果你的 Spring Boot 应用需要统一处理敏感字段脱敏、日志清洗、隐私审计追踪，以及安全可运维的死信流程，`v0.2.0` 是推荐升级版本。

## 相关发布材料

- 发布说明：`docs/releases/RELEASE_NOTES_v0.2.0.md`
- GitHub Release 文案：`docs/GITHUB_RELEASE_COPY_v0.2.0.md`
- 发布运行手册：`docs/RELEASE_RUNBOOK_v0.2.0.md`
