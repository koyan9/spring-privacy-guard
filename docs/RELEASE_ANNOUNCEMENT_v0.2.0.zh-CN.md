# 发布公告：v0.2.0

本文档可作为 `spring-privacy-guard v0.2.0` 的对外发布公告简版文案。

## 一句话介绍

`spring-privacy-guard v0.2.0` 新增稳定 SPI 标记、增强审计与死信处理能力、补齐 JDBC 生产落地指引，并提供签名告警投递、接收端验签与更完整的可观测性支持。

## 版本亮点

- 为 `MaskingStrategy`、告警回调、仓储接口、replay-store 契约等公开扩展点补充 `@StableSpi` 标记
- 新增可配置文本模式脱敏，并补齐 MDC / 结构化日志字段清洗支持
- 新增异步与批量审计投递能力，支持失败重试、死信回退和可配置执行器并发
- 增强死信处理链路，支持查询、重放、清理、导入导出和校验
- 补充 audit、dead-letter、replay-store 的 JDBC 生产指引与迁移说明
- 内置 webhook / email 死信阈值告警回调，并支持 filter / interceptor 两种 receiver 验签接入方式
- 增强 Actuator 与 Micrometer 指标覆盖，补齐 webhook 失败分类、replay-store 清理诊断和验签失败原因指标

## 建议用于 GitHub / 社区的发布文案

`spring-privacy-guard v0.2.0` 已正式发布。

本次版本重点增强了可扩展性与生产可运维能力：通过 `@StableSpi` 明确了主要扩展面，补充了 JDBC 生产部署与迁移说明，完善了异步 / 批量审计投递和死信处理链路，内置 webhook / email 告警回调，同时支持接收端签名校验与更完整的健康检查、指标观测能力。

如果你的 Spring Boot 应用需要统一处理敏感字段脱敏、日志清洗、隐私审计追踪，以及安全可运维的死信流程，`v0.2.0` 是推荐升级版本。

## 相关发布材料

- 发布说明：`docs/releases/RELEASE_NOTES_v0.2.0.md`
- GitHub Release 文案：`docs/GITHUB_RELEASE_COPY_v0.2.0.md`
- JDBC 生产指引：`docs/JDBC_PRODUCTION_GUIDE.md`
- 发布运行手册：`docs/RELEASE_RUNBOOK_v0.2.0.md`
