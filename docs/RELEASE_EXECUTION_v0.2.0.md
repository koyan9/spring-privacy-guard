# Release Execution Guide: v0.2.0

Use this guide when you are ready to publish `spring-privacy-guard v0.2.0`.

## Release Validation Commands

Run these before tagging:

```bash
python scripts/check_repo_hygiene.py
```

```bash
./mvnw -q verify
```

## Preconditions

- `CHANGELOG.md` has a finalized `0.2.0` section
- `RELEASE_NOTES_v0.2.0.md` is ready to use as the release body
- `mvnw.cmd -q verify` or `./mvnw -q verify` passes locally
- Working tree is clean and contains only intended release changes

## Local Verification

Windows:

```powershell
mvnw.cmd -q verify
```

macOS / Linux:

```bash
./mvnw -q verify
```

## Create the Release Tag

Windows PowerShell:

```powershell
git tag v0.2.0
git push origin v0.2.0
```

macOS / Linux:

```bash
git tag v0.2.0
git push origin v0.2.0
```

If you want an annotated tag instead:

```bash
git tag -a v0.2.0 -m "spring-privacy-guard v0.2.0"
git push origin v0.2.0
```

## Trigger the GitHub Release Workflow

Workflow: `.github/workflows/release.yml`

Use these inputs in the manual workflow form:

- `tag`: `v0.2.0`
- `release_name`: `spring-privacy-guard v0.2.0`
- `prerelease`: `false`

The workflow automatically:

- builds release artifacts with the `release-artifacts` profile
- collects starter and core jars
- uses `RELEASE_NOTES_v0.2.0.md` as the release body when present
- creates the GitHub release and uploads built artifacts

## Suggested GitHub Release Body

Use `RELEASE_NOTES_v0.2.0.md` directly.

## Post-Release Checks

- Verify the GitHub release contains the expected jars
- Confirm the body rendered from `RELEASE_NOTES_v0.2.0.md`
- Confirm the tag points to the intended commit
- If publishing further artifacts externally, record the published coordinates and checksums

## Rollback Notes

If the tag was pushed incorrectly before the GitHub release is finalized:

```bash
git tag -d v0.2.0
git push origin :refs/tags/v0.2.0
```

Then fix the issue locally, re-run verification, and recreate the tag.
