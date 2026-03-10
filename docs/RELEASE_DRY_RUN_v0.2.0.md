# Release Dry Run: v0.2.0

This document records a release-preparation dry run for the planned `v0.2.0` release.

## Scope Reviewed

- Root docs: `README.md`, `README.zh-CN.md`, `CHANGELOG.md`
- Sample docs: `samples/privacy-demo/README.md`
- Release materials: `docs/releases/RELEASE_NOTES_v0.2.0.md`, `docs/releases/RELEASE_NOTES_TEMPLATE.md`
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
- [x] tag-specific release notes drafted in `docs/releases/RELEASE_NOTES_v0.2.0.md`

### Release Content

- [x] Configuration metadata updated for new properties
- [x] GitHub-facing description/topics guidance prepared in README-related docs
- [x] Generated files and editor-only folders removed from the working tree
- [x] Publishable module versions aligned to `0.2.0`

## Follow-up Before Actual Tag

- [x] Bump artifact version from `0.1.0` to `0.2.0` across the project
- [x] Re-run `mvnw.cmd -q verify` after the version bump
- [ ] Push tag `v0.2.0`
- [ ] Monitor `.github/workflows/release.yml`

## Notes

- The project is functionally ready for a `v0.2.0` release candidate based on current local verification.
- The remaining release-cut step is tag publication and workflow monitoring.
