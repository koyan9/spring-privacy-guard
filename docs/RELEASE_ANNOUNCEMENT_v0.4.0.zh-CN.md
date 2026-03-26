# 发布公告：v0.4.0

本文档可作为 `spring-privacy-guard v0.4.0` 的简版对外发布公告。

## 一句话概述

`spring-privacy-guard v0.4.0` 进一步完善了多租户运维模型，新增租户级 dead-letter observability policy、更加完整的 tenant telemetry，以及更清晰的 native / fallback 对照与 rollout sample 矩阵。

## 版本亮点

- 在不改变现有稳定事件与查询契约的前提下，新增 stable tenant logging policy 和 tenant dead-letter observability policy resolver
- 新增 tenant alert transition、delivery outcome、receiver route failure，以及 export / manifest / import 的 exchange-path telemetry
- 补齐 built-in repository 的 tenant-native dead-letter 路径，覆盖 read、write、delete、replay 和 import
- 扩展 sample 矩阵，覆盖 built-in native、fallback 对照、自定义 native、自定义 JDBC、自定义 JDBC 双节点、PostgreSQL + Redis 生产近似双节点
- sample 的 observability 端点现在直接展示 repository implementation 和 capability flags，便于判断当前运行的是 native 还是 fallback

## 建议用于 GitHub / 社区的发布文案

`spring-privacy-guard v0.4.0` 已正式发布。

这一版重点增强了多租户场景下的可观测性、可验证性和 rollout 参考能力。除了扩展 tenant policy surface，还补上了 dead-letter 与 receiver 流程的 tenant telemetry，并通过更完整的 sample 矩阵，让团队可以在正式上线前对 built-in native、fallback 和 custom repository SPI 实现进行本地对照验证。

如果你的 Spring Boot 应用需要统一处理敏感字段脱敏、日志清洗、隐私审计追踪、死信处理、receiver 验签，以及多租户 rollout 的可观测性验证，`v0.4.0` 是 `v0.3.0` 之后的推荐升级版本。

## 相关发布材料

- 发布说明：`docs/releases/RELEASE_NOTES_v0.4.0.md`
- GitHub Release 文案：`docs/GITHUB_RELEASE_COPY_v0.4.0.md`
- 租户可观测性指南：`docs/TENANT_OBSERVABILITY_GUIDE.md`
- 租户采纳手册：`docs/TENANT_ADOPTION_PLAYBOOK.md`
- 发布运行手册：`docs/RELEASE_RUNBOOK_v0.4.0.md`
