# Release Dry Run: v0.4.0

Draft dry-run record for the upcoming `v0.4.0` release.
Update the checklist items below as the final release candidate is validated.

## Scope Reviewed

- Root docs: `README.md`, `README.zh-CN.md`, `CHANGELOG.md`
- Multi-tenant rollout docs: `docs/MULTI_TENANT_GUIDE.md`, `docs/TENANT_OBSERVABILITY_GUIDE.md`, `docs/TENANT_ADOPTION_PLAYBOOK.md`
- Sample docs: `samples/privacy-demo/README.md`
- Release materials: `docs/releases/RELEASE_NOTES_v0.4.0.md`, `docs/GITHUB_RELEASE_COPY_v0.4.0.md`
- Release workflow: `.github/workflows/release.yml`

## Checklist Result

### Code & Tests

- [x] `mvnw.cmd -q verify` passes locally for the current draft release candidate
- [x] sample module tests pass locally against the current draft release candidate
- [x] the sample comparison profiles still compile and document the expected native/fallback behavior

### Docs

- [x] `README.md` updated
- [x] `README.zh-CN.md` updated where needed
- [x] `samples/privacy-demo/README.md` updated
- [x] `CHANGELOG.md` updated
- [x] draft release notes prepared in `docs/releases/RELEASE_NOTES_v0.4.0.md`
- [x] draft execution guide, runbook, and GitHub release copy prepared for `v0.4.0`
- [x] draft release announcement prepared in both English and zh-CN for `v0.4.0`

### Release Content

- [x] configuration metadata updated for new tenant policy and observability properties
- [x] sample profile / rollout matrix documented
- [x] publishable module versions aligned to `0.4.0`

## Notes

- The draft `v0.4.0` scope consolidates the post-`v0.3.0` multi-tenant hardening work, tenant dead-letter observability policy overrides, tenant operational telemetry, exchange-path visibility, and the broader sample comparison matrix.
- Final release validation should explicitly confirm the built-in, fallback, custom-native, custom-JDBC, and production-like sample references still match the documented native/fallback expectations.
- Local draft validation was completed on 2026-03-27 with:
  - `mvnw.cmd -q verify`
  - `mvnw.cmd -q -DskipTests install -pl privacy-guard-core,privacy-guard-spring-boot-starter`
  - `mvnw.cmd -q -f samples/privacy-demo/pom.xml test`
  - `python scripts/check_repo_hygiene.py`
