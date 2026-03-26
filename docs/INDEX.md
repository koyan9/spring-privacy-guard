# Documentation Index

## Entry Points

- Main README: `README.md`
- Chinese README: `README.zh-CN.md`
- Security policy: `SECURITY.md`
- Support guide: `SUPPORT.md`
- JDBC production guide: `docs/JDBC_PRODUCTION_GUIDE.md`
- Multi-tenant guide: `docs/MULTI_TENANT_GUIDE.md`
- Tenant adoption playbook: `docs/TENANT_ADOPTION_PLAYBOOK.md`
- Tenant observability guide: `docs/TENANT_OBSERVABILITY_GUIDE.md`
- Maintainer guide: `docs/MAINTAINER_GUIDE.md`
- Logic walkthrough: `docs/LOGIC_WALKTHROUGH.md`
- Changelog: `CHANGELOG.md`
- Current published release notes: `docs/releases/RELEASE_NOTES_v0.4.0.md`
- Latest GitHub release: `https://github.com/koyan9/spring-privacy-guard/releases/latest`
- CI workflow: `.github/workflows/ci.yml`
- Release workflow: `.github/workflows/release.yml`

## Current Release Docs

- Release ops checklist: `docs/RELEASE_CHECKLIST.md`
- Current published release notes: `docs/releases/RELEASE_NOTES_v0.4.0.md`
- Current published release execution guide: `docs/RELEASE_EXECUTION_v0.4.0.md`
- Current published release runbook: `docs/RELEASE_RUNBOOK_v0.4.0.md`
- Current published release dry-run record: `docs/RELEASE_DRY_RUN_v0.4.0.md`
- Current published GitHub release copy: `docs/GITHUB_RELEASE_COPY_v0.4.0.md`
- Current published release announcement: `docs/RELEASE_ANNOUNCEMENT_v0.4.0.md`
- Current published release announcement (zh-CN): `docs/RELEASE_ANNOUNCEMENT_v0.4.0.zh-CN.md`
- Published GitHub release page: `https://github.com/koyan9/spring-privacy-guard/releases/tag/v0.4.0`

## Receiver Operations

- Receiver operations guide: `docs/RECEIVER_OPERATIONS.md`
- JDBC production rollout guide: `docs/JDBC_PRODUCTION_GUIDE.md`

## Docs Status

- Primary entry point: `README.md`
- Secondary entry point: `docs/INDEX.md`
- Latest published release notes: `docs/releases/RELEASE_NOTES_v0.4.0.md`
- Maintainer reference: `docs/MAINTAINER_GUIDE.md`
- Support and security: `SUPPORT.md` and `SECURITY.md`
- JDBC / database rollout reference: `docs/JDBC_PRODUCTION_GUIDE.md`
- Multi-tenant integration reference: `docs/MULTI_TENANT_GUIDE.md`
- Multi-tenant rollout decision reference: `docs/TENANT_ADOPTION_PLAYBOOK.md`
- Multi-tenant observability reference: `docs/TENANT_OBSERVABILITY_GUIDE.md`

## Current Development Status

- Latest published release: `v0.4.0`
- Current next-work focus now moves to the priorities listed in `docs/ROADMAP.md`
- Newly landed in the current development cycle:
  - tenant-aware dead-letter backlog health / metrics / alert fan-out
  - tenant-specific receiver verification routes
  - tenant-native dead-letter replay for built-in `IN_MEMORY` / `JDBC` repositories
  - stable tenant logging policy resolution over the existing property model
  - tenant-scoped dead-letter observability policy overrides for thresholds and recovery notifications
  - tenant operational telemetry for alert transitions, delivery outcomes, and receiver route failures
  - PowerShell and shell sample verification scripts for JDBC / Redis multi-instance rehearsal
  - PostgreSQL + Redis production-like sample profiles, compose recipe, and verification scripts
- Next planning source: `docs/ROADMAP.md`

## Quick Start Paths

- New to the project: read `README.md`
- Need the runnable demo and sample commands: read `samples/privacy-demo/README.md`
- Need tenant contracts and integration details: read `docs/MULTI_TENANT_GUIDE.md`
- Need rollout and observability guidance: read `docs/TENANT_ADOPTION_PLAYBOOK.md` and `docs/TENANT_OBSERVABILITY_GUIDE.md`
- Need operator guidance for receiver verification and replay-store backends: read `docs/RECEIVER_OPERATIONS.md`
- Need the PostgreSQL + Redis two-node sample recipe: read `samples/privacy-demo/README.md` and `docs/TENANT_ADOPTION_PLAYBOOK.md`

## Published Release Docs

- Roadmap: `docs/ROADMAP.md`
- Release checklist: `docs/RELEASE_CHECKLIST.md`
- Published release notes: `docs/releases/RELEASE_NOTES_v0.4.0.md`
- Published release execution guide: `docs/RELEASE_EXECUTION_v0.4.0.md`
- Published release runbook: `docs/RELEASE_RUNBOOK_v0.4.0.md`
- Published release dry run: `docs/RELEASE_DRY_RUN_v0.4.0.md`
- Published GitHub release copy: `docs/GITHUB_RELEASE_COPY_v0.4.0.md`
- Published release announcement: `docs/RELEASE_ANNOUNCEMENT_v0.4.0.md`
- Published release announcement (zh-CN): `docs/RELEASE_ANNOUNCEMENT_v0.4.0.zh-CN.md`
- GitHub metadata: `docs/GITHUB_METADATA.md`
- GitHub label guide: `docs/GITHUB_LABELS.md`

## Previous Release Archive

- `v0.3.0` release notes: `docs/releases/RELEASE_NOTES_v0.3.0.md`
- `v0.3.0` release execution guide: `docs/RELEASE_EXECUTION_v0.3.0.md`
- `v0.3.0` release runbook: `docs/RELEASE_RUNBOOK_v0.3.0.md`
- `v0.3.0` release dry run: `docs/RELEASE_DRY_RUN_v0.3.0.md`
- `v0.3.0` GitHub release copy: `docs/GITHUB_RELEASE_COPY_v0.3.0.md`
- `v0.3.0` release announcement: `docs/RELEASE_ANNOUNCEMENT_v0.3.0.md`
- `v0.3.0` release announcement (zh-CN): `docs/RELEASE_ANNOUNCEMENT_v0.3.0.zh-CN.md`
- `v0.2.0` release notes: `docs/releases/RELEASE_NOTES_v0.2.0.md`
- `v0.2.0` release execution guide: `docs/RELEASE_EXECUTION_v0.2.0.md`
- `v0.2.0` release runbook: `docs/RELEASE_RUNBOOK_v0.2.0.md`
- `v0.2.0` release dry run: `docs/RELEASE_DRY_RUN_v0.2.0.md`
- `v0.2.0` GitHub release copy: `docs/GITHUB_RELEASE_COPY_v0.2.0.md`
- `v0.2.0` release announcement: `docs/RELEASE_ANNOUNCEMENT_v0.2.0.md`
- `v0.2.0` release announcement (zh-CN): `docs/RELEASE_ANNOUNCEMENT_v0.2.0.zh-CN.md`

## Maintainer Release Order

1. Read `docs/RELEASE_CHECKLIST.md`
2. Use `docs/releases/RELEASE_NOTES_v0.4.0.md` as the latest published release reference
3. Reuse `docs/RELEASE_EXECUTION_v0.4.0.md` and `docs/RELEASE_RUNBOOK_v0.4.0.md` as the latest published reference when preparing the next versioned release
4. Reuse the `v0.4.0` announcement files and `docs/GITHUB_RELEASE_COPY_v0.4.0.md` as the published baseline release copy
