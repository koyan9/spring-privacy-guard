# 发布公告：v0.3.0

本文档可作为 `spring-privacy-guard v0.3.0` 的对外发布公告简版文案。

## 一句话介绍

`spring-privacy-guard v0.3.0` 新增基础多租户隐私策略、Redis replay-store、租户感知的审计与死信管理辅助服务，并进一步收紧了可选 JDBC / Redis 集成的自动配置边界。

## 版本亮点

- 新增基于请求头的租户透传，以及按租户生效的脱敏字符与文本规则
- 新增 `PrivacyTenantAwareMaskingStrategy` 与租户感知的审计 detail 策略，同时保持稳定事件与查询契约不变
- 为内置 in-memory / JDBC 仓储新增 tenant-native read SPI，使租户辅助服务可以优先走仓储预过滤路径
- 新增按租户范围工作的审计查询、死信查询、重放、删除、导入导出和统一管理 facade
- 新增 Redis-backed replay-store，适合需要共享 nonce 防重状态、但不想维护 JDBC schema 的 receiver 场景
- 修复了 receiver 自动配置边界，未引入 JDBC / Redis 依赖时不会因为可选能力而启动失败
- 补充了多租户接入文档与 JDBC 生产落地指引

## 建议用于 GitHub / 社区的发布文案

`spring-privacy-guard v0.3.0` 已正式发布。

本次版本重点增强了多租户场景下的可用性与可运维性。它为脱敏、文本清洗、审计和死信管理补上了基础租户能力，新增了适合多实例 receiver 的 Redis replay-store，并进一步修正了 JDBC / Redis 可选依赖的自动配置边界。

如果你的 Spring Boot 应用需要统一处理敏感字段脱敏、日志清洗、隐私审计追踪，以及安全可运维的死信和 receiver 验签流程，`v0.3.0` 是相对 `v0.2.0` 的推荐升级版本。

## 相关发布材料

- 发布说明：`docs/releases/RELEASE_NOTES_v0.3.0.md`
- GitHub Release 文案：`docs/GITHUB_RELEASE_COPY_v0.3.0.md`
- 多租户接入指南：`docs/MULTI_TENANT_GUIDE.md`
- JDBC 生产指引：`docs/JDBC_PRODUCTION_GUIDE.md`
- 发布运行手册：`docs/RELEASE_RUNBOOK_v0.3.0.md`
