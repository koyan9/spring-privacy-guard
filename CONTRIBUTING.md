# Contributing

## Local Setup

- Use Java `17+`
- Preferred commands:
  - macOS / Linux: `./mvnw -q verify`
  - Windows: `mvnw.cmd -q verify`
- `verify` enforces the JaCoCo line-coverage gate for each published module

## Workflow

- Keep changes focused and minimal
- Follow `.editorconfig` for indentation, encoding, and final-newline defaults
- Respect `.gitattributes` line endings: LF for source/docs and CRLF for Windows scripts
- Add or update tests for behavior changes
- Update README / CHANGELOG / release notes when behavior or configuration changes
- Validate project-local samples before release if your change affects integration behavior

## Release Readiness

- Follow `docs/RELEASE_CHECKLIST.md` before preparing a release
- For `v0.2.0`, see `RELEASE_NOTES_v0.2.0.md`, `docs/RELEASE_DRY_RUN_v0.2.0.md`, and `docs/RELEASE_EXECUTION_v0.2.0.md`
- Keep roadmap discussions aligned with `docs/ROADMAP.md`



