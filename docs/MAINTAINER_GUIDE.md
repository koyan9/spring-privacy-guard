# Maintainer Guide

This guide is for maintainers responsible for releases, triage, and repository hygiene.

## Quick Links

- `docs/INDEX.md`
- `SECURITY.md`
- `SUPPORT.md`
- `docs/GITHUB_LABELS.md`
- `docs/RELEASE_CHECKLIST.md`
- `docs/RELEASE_EXECUTION_v0.5.0.md`
- `docs/RELEASE_RUNBOOK_v0.5.0.md`
- `docs/releases/RELEASE_NOTES_v0.5.0.md`
- `docs/RELEASE_ANNOUNCEMENT_v0.5.0.md`
- `docs/RELEASE_ANNOUNCEMENT_v0.5.0.zh-CN.md`
- `CHANGELOG.md`
- `docs/ROADMAP.md`

## Current Status

- Latest published release: `v0.5.0`
- Current branch version: `0.5.0`
- Current published implementation history is tracked in `CHANGELOG.md`
- `docs/ROADMAP.md` is retained only as archived planning context after `v0.5.0`
- Current published release materials now live under the `v0.5.0` filenames in `docs/` and `docs/releases/`
- When maintainers change that status, update `README.md`, `README.zh-CN.md`, `docs/INDEX.md`, and sample docs together

## Triage Checklist

- Confirm the module: `privacy-guard-core`, `privacy-guard-spring-boot-starter`, or `samples/privacy-demo`.
- Apply one type label and at least one area label from `docs/GITHUB_LABELS.md`.
- Request a minimal reproduction and sanitized logs if missing.
- For security concerns, redirect to `SECURITY.md` and avoid public details.

## Release Workflow

- Follow `docs/RELEASE_CHECKLIST.md` before tagging.
- Use the latest published versioned execution guide, currently `docs/RELEASE_EXECUTION_v0.5.0.md`, as the step-by-step template until a newer guide is added.
- Validate `docs/releases/RELEASE_NOTES_<tag>.md` before triggering the release workflow.
- Run `python scripts/check_repo_hygiene.py` before publishing.

## Release Ops

- Confirm the GitHub release exists for the tag and the body matches `docs/releases/RELEASE_NOTES_<tag>.md`.
- Verify uploaded artifacts include the core and starter jars.
- Check CI runs are green for the release commit.
- If a release must be pulled, delete the GitHub release first, then follow the rollback steps in the matching `docs/RELEASE_EXECUTION_<tag>.md`.

## Release FAQ

- **How do I create a release tag?** Follow the latest published execution guide, currently `docs/RELEASE_EXECUTION_v0.5.0.md`, and tag `vX.Y.Z`.
- **Which workflow inputs do I use?** Use `tag`, `release_name`, and `prerelease` as documented in `.github/workflows/release.yml`.
- **Where should release notes live?** Put them in `docs/releases/RELEASE_NOTES_<tag>.md` and update `CHANGELOG.md`.
- **How do I roll back a bad release?** Remove the GitHub release, delete the tag, and re-run the release process after the fix.

## Versioning & Tags

- Version bumps must update root `pom.xml`, module `pom.xml`, and `samples/privacy-demo/pom.xml`.
- Tags should follow `vX.Y.Z` (for example `v0.3.0`).
- Keep release notes in `docs/releases/RELEASE_NOTES_<tag>.md` and update `CHANGELOG.md`.

## Documentation Expectations

- Update `README.md`, `README.zh-CN.md`, and `docs/INDEX.md` when behavior or configuration changes.
- Update sample docs in `samples/privacy-demo/README.md` when demo flows change.

## CI and Hygiene

- CI runs `python scripts/check_repo_hygiene.py --ci` before tests.
- Keep GitHub Actions pinned to Node 24-ready versions to avoid runner deprecation failures during CI or release execution.
- Keep `.editorconfig` and `.gitattributes` aligned with repo conventions.
- Fix trailing whitespace before merging (run `git diff --check`).

## Samples and Validation

- Validate the sample app when integration behavior changes:
  - `./mvnw -q -f samples/privacy-demo/pom.xml -DskipTests compile`
  - `./mvnw -q -f samples/privacy-demo/pom.xml spring-boot:run`
- Sample regression slices now expose JUnit 5 tags for the main areas we split out:
  - `sample-default`
  - `sample-tenant`
  - `sample-materialization`
  - `sample-dead-letter`
  - `sample-receiver`
  - `sample-interceptor`
  - `sample-jdbc`
  - `sample-redis`
  - `sample-postgres-redis`
  - `sample-custom`
  - `sample-fallback`
  - `sample-multi-instance`
- Surefire can filter those slices directly, for example:
  - `./mvnw -q -f samples/privacy-demo/pom.xml -Dgroups=sample-receiver test`
  - `./mvnw -q -f samples/privacy-demo/pom.xml -Dgroups=sample-jdbc,sample-materialization test`
  - `./mvnw -q -f samples/privacy-demo/pom.xml -Dgroups=sample-postgres-redis test`
  - `./mvnw -q -f samples/privacy-demo/pom.xml -Dgroups=sample-custom test`
  - `./mvnw -q -f samples/privacy-demo/pom.xml -Dgroups=sample-multi-instance test`
- When tenant-aware write or management auditing changes, run the focused sample HTTP regression tests:
  - `./mvnw -q -f samples/privacy-demo/pom.xml -Dtest=PrivacyDemoTenantWriteMaterializationHttpTest test`
  - `./mvnw -q -f samples/privacy-demo/pom.xml -Dtest=PrivacyDemoJdbcTenantWriteMaterializationHttpTest test`
  - `./mvnw -q -f samples/privacy-demo/pom.xml -Dtest=PrivacyDemoJdbcTenantDeadLetterMaterializationHttpTest test`
- When receiver route or replay-store behavior changes, run the focused sample receiver tests:
  - `./mvnw -q -f samples/privacy-demo/pom.xml -Dtest=PrivacyDemoJdbcTenantReceiverRouteHttpTest test`
  - `./mvnw -q -f samples/privacy-demo/pom.xml -Dtest=PrivacyDemoPostgresRedisTenantReceiverRouteHttpTest test`
- When shared replay-store / multi-instance behavior changes, run the programmatic two-node regression tests:
  - `./mvnw -q -f samples/privacy-demo/pom.xml -Dtest=PrivacyDemoJdbcTenantMultiInstanceReceiverRouteTest test`
  - `./mvnw -q -f samples/privacy-demo/pom.xml -Dtest=PrivacyDemoPostgresRedisTenantMultiInstanceReceiverRouteTest test`
- Keep the scripted rehearsal path aligned with those tests:
  - `samples/privacy-demo/scripts/verify-jdbc-tenant-multi-instance.ps1`
  - `samples/privacy-demo/scripts/verify-jdbc-tenant-multi-instance.sh`
  - `samples/privacy-demo/scripts/verify-redis-tenant-multi-instance.ps1`
  - `samples/privacy-demo/scripts/verify-redis-tenant-multi-instance.sh`
  - `samples/privacy-demo/scripts/verify-postgres-redis-tenant-multi-instance.ps1`
  - `samples/privacy-demo/scripts/verify-postgres-redis-tenant-multi-instance.sh`
