# Release Dry Run: v0.3.0

This document records a release-preparation dry run for the planned `v0.3.0` release.

## Scope Reviewed

- Root docs: `README.md`, `README.zh-CN.md`, `CHANGELOG.md`
- Integration guides: `docs/JDBC_PRODUCTION_GUIDE.md`, `docs/MULTI_TENANT_GUIDE.md`
- Sample docs: `samples/privacy-demo/README.md`
- Release materials: `docs/releases/RELEASE_NOTES_v0.3.0.md`, `docs/GITHUB_RELEASE_COPY_v0.3.0.md`
- Release workflow: `.github/workflows/release.yml`

## Checklist Result

### Code & Tests

- [x] `mvnw.cmd -q verify` passes locally
- [x] sample module tests pass locally
- [x] JaCoCo coverage gate remains active

### Docs

- [x] `README.md` updated
- [x] `README.zh-CN.md` updated
- [x] `samples/privacy-demo/README.md` updated
- [x] `CHANGELOG.md` updated
- [x] tag-specific release notes drafted in `docs/releases/RELEASE_NOTES_v0.3.0.md`
- [x] GitHub release copy and announcement drafts prepared for `v0.3.0`

### Release Content

- [x] Configuration metadata updated for new properties
- [x] GitHub-facing description/topics guidance prepared in release docs
- [x] Publishable module versions aligned to `0.3.0`

## Follow-up Before Actual Tag

- [x] Re-run `mvnw.cmd -q verify` after the `0.3.0` version alignment
- [x] Re-install local artifacts and validate `samples/privacy-demo`
- [ ] Push tag `v0.3.0`
- [ ] Monitor `.github/workflows/release.yml`

## Notes

- `v0.3.0` packages the current Redis replay-store and baseline multi-tenant work without changing the stable event and query contracts.
- Local dry-run validation was completed on 2026-03-19 with `mvnw.cmd -q verify`, `mvnw.cmd -q -DskipTests install`, `mvnw.cmd -q -f samples/privacy-demo/pom.xml test`, and `python scripts/check_repo_hygiene.py`.
- The remaining release-cut blocker is operational rather than technical: commit the intended release state on a clean worktree, then create and push tag `v0.3.0`.
