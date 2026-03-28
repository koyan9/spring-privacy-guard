# Release Execution Guide: v0.5.0

Published execution guide for the released `spring-privacy-guard v0.5.0` baseline.
Reuse this guide as the starting template when preparing the next versioned release.

## Release Validation Commands

Use these commands before creating the `v0.5.0` tag:

```bash
python scripts/check_repo_hygiene.py
```

```bash
./mvnw -q verify
```

```bash
./mvnw -q install
./mvnw -q -f samples/privacy-demo/pom.xml test
```

Focused regression slices for the current draft scope:

```bash
./mvnw -q -pl privacy-guard-spring-boot-starter -Dtest=PrivacyTenantAuditDeadLetterOperationsServiceTest,MicrometerPrivacyTenantAuditTelemetryTest test
./mvnw -q -pl privacy-guard-spring-boot-starter -Dtest=LoggingPrivacyTenantAuditDeadLetterAlertCallbackTest,TenantScopedPrivacyAuditDeadLetterWebhookAlertCallbackTest,TenantScopedPrivacyAuditDeadLetterEmailAlertCallbackTest,PrivacyGuardDeadLetterObservabilityAutoConfigurationTest,PrivacyGuardAutoConfigurationTest test
./mvnw -q -f samples/privacy-demo/pom.xml -Dtest=PrivacyDemoApplicationTest,PrivacyDemoDefaultDeadLetterHttpTest,PrivacyDemoJdbcTenantDeadLetterMaterializationHttpTest,PrivacyDemoDefaultReceiverHttpTest test
```

## Preconditions

- `CHANGELOG.md` has a finalized `0.5.0` section
- `docs/releases/RELEASE_NOTES_v0.5.0.md` is ready to use as the release body
- `docs/GITHUB_RELEASE_COPY_v0.5.0.md` is ready for manual GitHub release editing if needed
- root and sample module versions are bumped to `0.5.0`
- local verification passes for root modules and the sample app
- working tree is clean and contains only intended release changes

## Local Verification

Windows:

```powershell
mvnw.cmd -q verify
mvnw.cmd -q install
mvnw.cmd -q -f samples/privacy-demo/pom.xml test
python scripts/check_repo_hygiene.py
```

macOS / Linux:

```bash
./mvnw -q verify
./mvnw -q install
./mvnw -q -f samples/privacy-demo/pom.xml test
python scripts/check_repo_hygiene.py
```

## Create the Release Tag

Windows PowerShell:

```powershell
git tag v0.5.0
git push origin v0.5.0
```

macOS / Linux:

```bash
git tag v0.5.0
git push origin v0.5.0
```

Annotated tag option:

```bash
git tag -a v0.5.0 -m "spring-privacy-guard v0.5.0"
git push origin v0.5.0
```

## Trigger the GitHub Release Workflow

Workflow: `.github/workflows/release.yml`

Inputs:

- `tag`: `v0.5.0`
- `release_name`: `spring-privacy-guard v0.5.0`
- `prerelease`: `false`

## Post-Release Checks

- verify the GitHub release contains the expected jars for core and starter modules
- confirm the release body rendered from `docs/releases/RELEASE_NOTES_v0.5.0.md`
- confirm the tag points to the intended commit
- confirm the sample policy and observability docs render correctly:
  - `samples/privacy-demo/README.md`
  - `docs/MULTI_TENANT_GUIDE.md`
  - `docs/TENANT_OBSERVABILITY_GUIDE.md`
- confirm the sample policy view and observability view still match the shipped tenant alert policy behavior

## Rollback Notes

If the tag was pushed incorrectly before the GitHub release is finalized:

```bash
git tag -d v0.5.0
git push origin :refs/tags/v0.5.0
```

Then fix the issue locally, re-run verification, and recreate the tag.
