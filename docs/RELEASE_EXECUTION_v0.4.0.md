# Release Execution Guide: v0.4.0

Draft execution guide for the upcoming `spring-privacy-guard v0.4.0` release.
Update the validation timestamps and final scope before tagging.

## Release Validation Commands

Use these commands before creating the `v0.4.0` tag:

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

Optional profile-matrix checks:

```bash
./mvnw -q -f samples/privacy-demo/pom.xml -Dtest=PrivacyDemoFallbackTenantProfileTest,PrivacyDemoCustomTenantNativeProfileTest,PrivacyDemoCustomJdbcTenantProfileTest,PrivacyDemoCustomJdbcTenantNode2ProfileTest,PrivacyDemoPostgresRedisTenantProfileTest,PrivacyDemoPostgresRedisTenantNode2ProfileTest test
```

## Preconditions

- `CHANGELOG.md` has a finalized `0.4.0` section
- `docs/releases/RELEASE_NOTES_v0.4.0.md` is ready to use as the release body
- `docs/GITHUB_RELEASE_COPY_v0.4.0.md` is ready for manual GitHub release editing if needed
- `mvnw.cmd -q verify` or `./mvnw -q verify` passes locally
- `samples/privacy-demo` still compiles and its tests pass against the local `0.4.0` artifacts
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
git tag v0.4.0
git push origin v0.4.0
```

macOS / Linux:

```bash
git tag v0.4.0
git push origin v0.4.0
```

Annotated tag option:

```bash
git tag -a v0.4.0 -m "spring-privacy-guard v0.4.0"
git push origin v0.4.0
```

## Trigger the GitHub Release Workflow

Workflow: `.github/workflows/release.yml`

Inputs:

- `tag`: `v0.4.0`
- `release_name`: `spring-privacy-guard v0.4.0`
- `prerelease`: `false`

## Post-Release Checks

- Verify the GitHub release contains the expected jars for core and starter modules
- Confirm the release body rendered from `docs/releases/RELEASE_NOTES_v0.4.0.md`
- Confirm the tag points to the intended commit
- Confirm the sample comparison and rollout docs render correctly on GitHub:
  - `samples/privacy-demo/README.md`
  - `docs/TENANT_ADOPTION_PLAYBOOK.md`
  - `docs/TENANT_OBSERVABILITY_GUIDE.md`
- If publishing further artifacts externally, record the published coordinates and checksums

## Rollback Notes

If the tag was pushed incorrectly before the GitHub release is finalized:

```bash
git tag -d v0.4.0
git push origin :refs/tags/v0.4.0
```

Then fix the issue locally, re-run verification, and recreate the tag.
