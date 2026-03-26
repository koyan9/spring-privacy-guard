# Release Execution Guide: v0.3.0

This guide records the execution flow used for the published `spring-privacy-guard v0.3.0` release on 2026-03-20.
Reuse it as the latest versioned template until a newer release-specific guide exists.

## Release Validation Commands

These were the validation commands used before tagging:

```bash
python scripts/check_repo_hygiene.py
```

```bash
./mvnw -q verify
```

```bash
./mvnw -q -DskipTests install
./mvnw -q -f samples/privacy-demo/pom.xml test
```

## Preconditions

- `CHANGELOG.md` has a finalized `0.3.0` section
- `docs/releases/RELEASE_NOTES_v0.3.0.md` is ready to use as the release body
- `mvnw.cmd -q verify` or `./mvnw -q verify` passes locally
- `samples/privacy-demo` still compiles and its tests pass against the local `0.3.0` artifacts
- Working tree is clean and contains only intended release changes

## Local Verification

Windows:

```powershell
mvnw.cmd -q verify
mvnw.cmd -q -DskipTests install
mvnw.cmd -q -f samples/privacy-demo/pom.xml test
python scripts/check_repo_hygiene.py
```

macOS / Linux:

```bash
./mvnw -q verify
./mvnw -q -DskipTests install
./mvnw -q -f samples/privacy-demo/pom.xml test
python scripts/check_repo_hygiene.py
```

## Create the Release Tag

Windows PowerShell:

```powershell
git tag v0.3.0
git push origin v0.3.0
```

macOS / Linux:

```bash
git tag v0.3.0
git push origin v0.3.0
```

If you want an annotated tag instead:

```bash
git tag -a v0.3.0 -m "spring-privacy-guard v0.3.0"
git push origin v0.3.0
```

## Trigger the GitHub Release Workflow

Workflow: `.github/workflows/release.yml`

Use these inputs in the manual workflow form:

- `tag`: `v0.3.0`
- `release_name`: `spring-privacy-guard v0.3.0`
- `prerelease`: `false`

The workflow automatically:

- builds release artifacts with the `release-artifacts` profile
- collects starter and core jars
- uses `docs/releases/RELEASE_NOTES_v0.3.0.md` as the release body when present
- creates the GitHub release and uploads built artifacts

## Suggested GitHub Release Body

Use `docs/releases/RELEASE_NOTES_v0.3.0.md` directly.

## Post-Release Checks

- Verify the GitHub release contains the expected jars
- Confirm the body rendered from `docs/releases/RELEASE_NOTES_v0.3.0.md`
- Confirm the tag points to the intended commit
- Confirm the linked docs for JDBC and multi-tenant rollout render correctly on GitHub
- If publishing further artifacts externally, record the published coordinates and checksums

## Rollback Notes

If the tag was pushed incorrectly before the GitHub release is finalized:

```bash
git tag -d v0.3.0
git push origin :refs/tags/v0.3.0
```

Then fix the issue locally, re-run verification, and recreate the tag.
