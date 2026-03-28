# Release Runbook: v0.5.0

Published runbook reference for the released `spring-privacy-guard v0.5.0` baseline.
Use this as the short operator template when preparing the next versioned release.

## 1. Final Local Check

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

Expected result: full success with no failing modules, no sample failures, and no hygiene errors.

## 2. Confirm Release Materials

Verify these files are ready:

- `CHANGELOG.md`
- `README.md`
- `README.zh-CN.md`
- `docs/MULTI_TENANT_GUIDE.md`
- `docs/TENANT_OBSERVABILITY_GUIDE.md`
- `samples/privacy-demo/README.md`
- `docs/releases/RELEASE_NOTES_v0.5.0.md`
- `docs/GITHUB_RELEASE_COPY_v0.5.0.md`
- `docs/RELEASE_CHECKLIST.md`
- `docs/RELEASE_DRY_RUN_v0.5.0.md`

## 3. Create and Push the Tag

PowerShell:

```powershell
git tag v0.5.0
git push origin v0.5.0
```

Annotated tag option:

```powershell
git tag -a v0.5.0 -m "spring-privacy-guard v0.5.0"
git push origin v0.5.0
```

## 4. Trigger the Release Workflow

GitHub UI inputs:

- `tag`: `v0.5.0`
- `release_name`: `spring-privacy-guard v0.5.0`
- `prerelease`: `false`

## 5. Verify the GitHub Release

- release title is correct
- body matches `docs/releases/RELEASE_NOTES_v0.5.0.md`
- uploaded jars exist for core and starter modules
- release is marked as a normal release

## 6. Rollout and Comparison Checks

- the sample policy summary in `/demo-tenants/policies` matches the documented tenant alert route, delivery, and monitoring overrides
- native vs fallback observability descriptions match the shipped `by-id` telemetry and sample output
- the rollout comparison matrix in `samples/privacy-demo/README.md` still matches the release scope

## 7. Rollback If Needed

```bash
git tag -d v0.5.0
git push origin :refs/tags/v0.5.0
```

Then fix the issue, rerun verification, and recreate the tag.
