# Maintainer Guide

This guide is for maintainers responsible for releases, triage, and repository hygiene.

## Quick Links

- `docs/INDEX.md`
- `SECURITY.md`
- `SUPPORT.md`
- `docs/GITHUB_LABELS.md`
- `docs/RELEASE_CHECKLIST.md`
- `docs/RELEASE_EXECUTION_v0.2.0.md`
- `RELEASE_NOTES_v0.2.0.md`

## Triage Checklist

- Confirm the module: `privacy-guard-core`, `privacy-guard-spring-boot-starter`, or `samples/privacy-demo`.
- Apply one type label and at least one area label from `docs/GITHUB_LABELS.md`.
- Request a minimal reproduction and sanitized logs if missing.
- For security concerns, redirect to `SECURITY.md` and avoid public details.

## Release Workflow

- Follow `docs/RELEASE_CHECKLIST.md` before tagging.
- Use `docs/RELEASE_EXECUTION_v0.2.0.md` as the step-by-step release guide.
- Validate `RELEASE_NOTES_<tag>.md` before triggering the release workflow.
- Run `python scripts/check_repo_hygiene.py` before publishing.

## Versioning & Tags

- Version bumps must update root `pom.xml`, module `pom.xml`, and `samples/privacy-demo/pom.xml`.
- Tags should follow `vX.Y.Z` (for example `v0.2.0`).
- Keep release notes in `RELEASE_NOTES_<tag>.md` and update `CHANGELOG.md`.

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
