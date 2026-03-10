# Maintainer Guide

This guide is for maintainers responsible for releases, triage, and repository hygiene.

## Quick Links

- `docs/INDEX.md`
- `SECURITY.md`
- `SUPPORT.md`
- `docs/GITHUB_LABELS.md`
- `docs/RELEASE_CHECKLIST.md`
- `docs/RELEASE_EXECUTION_v0.2.0.md`
- `docs/releases/RELEASE_NOTES_v0.2.0.md`

## Triage Checklist

- Confirm the module: `privacy-guard-core`, `privacy-guard-spring-boot-starter`, or `samples/privacy-demo`.
- Apply one type label and at least one area label from `docs/GITHUB_LABELS.md`.
- Request a minimal reproduction and sanitized logs if missing.
- For security concerns, redirect to `SECURITY.md` and avoid public details.

## Release Workflow

- Follow `docs/RELEASE_CHECKLIST.md` before tagging.
- Use `docs/RELEASE_EXECUTION_v0.2.0.md` as the step-by-step release guide.
- Validate `docs/releases/RELEASE_NOTES_<tag>.md` before triggering the release workflow.
- Run `python scripts/check_repo_hygiene.py` before publishing.

## Release Ops

- Confirm the GitHub release exists for the tag and the body matches `docs/releases/RELEASE_NOTES_<tag>.md`.
- Verify uploaded artifacts include the core and starter jars.
- Check CI runs are green for the release commit.
- If a release must be pulled, delete the GitHub release first, then follow the rollback steps in `docs/RELEASE_EXECUTION_v0.2.0.md`.

## Release FAQ

- **How do I create a release tag?** Follow `docs/RELEASE_EXECUTION_v0.2.0.md` and tag `vX.Y.Z`.
- **Which workflow inputs do I use?** Use `tag`, `release_name`, and `prerelease` as documented in `.github/workflows/release.yml`.
- **Where should release notes live?** Put them in `docs/releases/RELEASE_NOTES_<tag>.md` and update `CHANGELOG.md`.
- **How do I roll back a bad release?** Remove the GitHub release, delete the tag, and re-run the release process after the fix.

## Versioning & Tags

- Version bumps must update root `pom.xml`, module `pom.xml`, and `samples/privacy-demo/pom.xml`.
- Tags should follow `vX.Y.Z` (for example `v0.2.0`).
- Keep release notes in `docs/releases/RELEASE_NOTES_<tag>.md` and update `CHANGELOG.md`.

## Documentation Expectations

- Update `README.md`, `README.zh-CN.md`, and `docs/INDEX.md` when behavior or configuration changes.
- Update sample docs in `samples/privacy-demo/README.md` when demo flows change.

## CI and Hygiene

- CI runs `python scripts/check_repo_hygiene.py --ci` before tests.
- Keep `.editorconfig` and `.gitattributes` aligned with repo conventions.
- Fix trailing whitespace before merging (run `git diff --check`).

## Samples and Validation

- Validate the sample app when integration behavior changes:
  - `./mvnw -q -f samples/privacy-demo/pom.xml -DskipTests compile`
  - `./mvnw -q -f samples/privacy-demo/pom.xml spring-boot:run`
