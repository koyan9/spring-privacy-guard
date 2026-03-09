# Release Checklist

Use this checklist before cutting a new release.

## Code & Tests

- Ensure the branch is up to date and changes are scoped for release
- Bump the Maven version to the target release if needed
- Run `./mvnw -q verify` or `mvnw.cmd -q verify`
- Confirm sample app still runs from `samples/privacy-demo/`
- Review changelog entries for accuracy

## Docs

- Update `README.md` if behavior or configuration changed
- Update `README.zh-CN.md` if the change affects documented usage
- Update `samples/privacy-demo/README.md` if sample behavior changed
- Confirm `CHANGELOG.md` includes the release contents
- Prepare `RELEASE_NOTES_<tag>.md` or update `RELEASE_NOTES_TEMPLATE.md`
- Optionally capture a dry run note such as `docs/RELEASE_DRY_RUN_v0.2.0.md`

## Release Content

- Verify module versions are correct
- Confirm new properties appear in configuration metadata
- Check GitHub About description/topics are still accurate
- Ensure no local/editor/generated artifacts are included

## Final Verification

- Validate release workflow inputs and tag naming
- Create the tag-specific release notes file if needed
- Push the tag and monitor `.github/workflows/release.yml`

## Execution

- Follow `docs/RELEASE_EXECUTION_v0.2.0.md` for the actual tag and release steps
- Use `docs/RELEASE_RUNBOOK_v0.2.0.md` for the concise operator checklist
