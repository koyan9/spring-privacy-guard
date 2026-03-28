# 发布公告：v0.5.0

本文档可作为已发布的 `spring-privacy-guard v0.5.0` 对外发布公告简版文案。

## 一句话概述

`spring-privacy-guard v0.5.0` 补齐了多租户场景下单条 dead-letter 管理的原生路径，并把 tenant alert policy 扩展到了 route、delivery 和 monitor membership 三个层面，同时让 sample 中的有效策略状态更容易验证。

## 版本亮点

- 补齐 tenant-aware 单条 dead-letter 的 lookup、delete、replay 路径，并为 `by-id` 管理单独暴露 native capability 与 write-path telemetry
- 扩展 tenant policy surface，新增 stable dead-letter alert route、delivery、monitoring policy resolver
- 新增租户级 logging / webhook / email delivery 开关
- 新增租户级 alert monitor membership 控制，不再只能依赖全局 monitored-tenant allowlist
- sample 的 policy / observability 视图现在可以直接展示 effective tenant alert route、delivery 和 monitoring state

## 建议用于 GitHub / 社区的发布文案

`spring-privacy-guard v0.5.0` 已正式发布。

这一版重点是把多租户 dead-letter 管理路径和 tenant alerting 策略面补齐。在不改变稳定事件与查询契约的前提下，项目现在不仅能区分 criteria 与单条 `by-id` 的 native mutation 路径，还能按租户分别控制告警路由、告警投递渠道，以及是否参与 tenant alert monitor。

如果你正在 shared / multi-tenant 的 Spring Boot 部署中使用 `spring-privacy-guard`，`v0.5.0` 会让 rollout 前的策略验证和上线后的告警行为更清晰、更可控。

## 相关发布材料

- 发布说明：`docs/releases/RELEASE_NOTES_v0.5.0.md`
- GitHub Release 文案：`docs/GITHUB_RELEASE_COPY_v0.5.0.md`
- 多租户指南：`docs/MULTI_TENANT_GUIDE.md`
- 租户可观测性指南：`docs/TENANT_OBSERVABILITY_GUIDE.md`
- Sample 指南：`samples/privacy-demo/README.md`
- 发布运行手册：`docs/RELEASE_RUNBOOK_v0.5.0.md`
