# Contributing

## Local Setup

- Use Java `17+`
- Preferred commands:
  - macOS / Linux: `./mvnw -q verify`
  - Windows: `mvnw.cmd -q verify`
- `python scripts/check_repo_hygiene.py`: validate UTF-8, line endings, and documentation links
- `verify` enforces the JaCoCo line-coverage gate for each published module

## Workflow

- Keep changes focused and minimal
- Follow `.editorconfig` for indentation, encoding, and final-newline defaults
- Respect `.gitattributes` line endings: LF for source/docs and CRLF for Windows scripts
- Add or update tests for behavior changes
- Update README / CHANGELOG / release notes when behavior or configuration changes
- Validate project-local samples before release if your change affects integration behavior

## Security

- If you believe you found a vulnerability, follow `SECURITY.md` and avoid posting exploit details publicly.
- Sanitize logs, payloads, tokens, and personal data before sharing samples in issues or pull requests.

## Support Routing

- Use `SUPPORT.md` to choose the right path for bugs, feature requests, docs questions, and release questions.
- Use the GitHub issue templates and keep reports scoped to a single reproducible problem.
- Maintain labels consistently using `docs/GITHUB_LABELS.md` when triaging issues or pull requests.

## Contributor FAQ

- **Where should I report a security issue?** Follow `SECURITY.md` and avoid posting exploit details publicly.
- **Where do I find the release checklist?** Use `docs/RELEASE_CHECKLIST.md` and the latest published execution guide `docs/RELEASE_EXECUTION_v0.5.0.md`.
- **What if I only changed documentation?** Run `python scripts/check_repo_hygiene.py` and update `CHANGELOG.md` if release notes need the change.
- **How do I validate sample changes?** Run `./mvnw -q -f samples/privacy-demo/pom.xml -DskipTests compile` or run the sample app.
- **How do I confirm line endings and encoding?** Follow `.editorconfig`, `.gitattributes`, and rerun `python scripts/check_repo_hygiene.py`.

## Release Readiness

- Follow `docs/RELEASE_CHECKLIST.md` before preparing a release
- Use `docs/releases/RELEASE_NOTES_v0.5.0.md`, `docs/RELEASE_DRY_RUN_v0.5.0.md`, and `docs/RELEASE_EXECUTION_v0.5.0.md` as the latest published release references
- Keep roadmap discussions aligned with `docs/ROADMAP.md`
