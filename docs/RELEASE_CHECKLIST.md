# Release Checklist

Use this checklist before cutting a new release.

## Code & Tests

- Ensure the branch is up to date and changes are scoped for release
- Bump the Maven version to the target release if needed
- Run `./mvnw -q verify` or `mvnw.cmd -q verify`
- Run `./mvnw -q -Prelease-artifacts verify` or `mvnw.cmd -q -Prelease-artifacts verify`
- Run `./mvnw -q -Pcentral-publish -DskipTests verify` or `mvnw.cmd -q -Pcentral-publish -DskipTests verify`
- Confirm sample app still runs from `samples/privacy-demo/`
- Review changelog entries for accuracy

## Docs

- Update `README.md` if behavior or configuration changed
- Update `README.zh-CN.md` if the change affects documented usage
- Update `samples/privacy-demo/README.md` if sample behavior changed
- Confirm `CHANGELOG.md` includes the release contents
- Confirm `docs/RELEASE_PROCESS.md` still matches the intended Maven Central publication path
- Prepare `docs/releases/RELEASE_NOTES_<tag>.md` or update `docs/releases/RELEASE_NOTES_TEMPLATE.md`
- Optionally capture a dry run note such as `docs/RELEASE_DRY_RUN_v0.4.0.md` or `docs/RELEASE_DRY_RUN_<tag>.md`
- Review the sample / rollout comparison matrix and confirm it matches the release scope

## Release Content

- Verify module versions are correct
- Confirm new properties appear in configuration metadata
- Check GitHub About description/topics are still accurate
- Confirm local or CI Maven settings provide a `central` server with Central Portal user-token credentials
- Confirm the release signing key is available to GPG and the passphrase path is configured outside the repository
- Ensure no local/editor/generated artifacts are included

## Final Verification

- Validate release workflow inputs and tag naming
- Create the tag-specific release notes file if needed
- Push the tag and monitor `.github/workflows/release.yml`
- If publishing to Maven Central manually, use `docs/RELEASE_PROCESS.md` instead of retrofitting the existing published tag workflow

## Execution

- Follow the matching `docs/RELEASE_EXECUTION_<tag>.md` guide for the actual tag and release steps
- Use the matching `docs/RELEASE_RUNBOOK_<tag>.md` file for the concise operator checklist
