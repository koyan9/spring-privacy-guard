# Support Guide

## Before Opening an Issue

Start with the project docs first:

- `README.md`
- `README.zh-CN.md`
- `docs/INDEX.md`
- `CHANGELOG.md`
- `RELEASE_NOTES_v0.2.0.md`

For release or rollout questions, also review:

- `docs/RELEASE_CHECKLIST.md`
- `docs/RELEASE_EXECUTION_v0.2.0.md`
- `docs/RELEASE_RUNBOOK_v0.2.0.md`

## Where to Ask

- Bug or regression: use the bug report issue template
- Feature request or API proposal: use the feature request issue template
- Security concern: follow `SECURITY.md` and avoid posting sensitive details publicly
- Documentation gap or usage confusion: open a documentation-focused issue or feature request with the affected file path

## What to Include

Include the smallest useful reproduction:

- affected module, version, and Spring Boot version
- configuration sample with secrets and personal data removed
- repository type such as `NONE`, `IN_MEMORY`, or `JDBC`
- exact command used, for example `python scripts/check_repo_hygiene.py` or `mvnw.cmd -q verify`
- expected behavior, current behavior, and any sanitized logs

## Response Expectations

This repository is maintained on a best-effort basis. Clear reproduction steps, sanitized samples, and version details will help reduce turnaround time.