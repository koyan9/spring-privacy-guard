# Release Runbook: v0.4.0

Published runbook reference for the released `spring-privacy-guard v0.4.0` baseline.
Use this as the shortest operator template when preparing the next versioned release.

## 1. Final Local Check

Windows:

```powershell
mvnw.cmd -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter
mvnw.cmd -q verify
mvnw.cmd -q -DskipTests install
mvnw.cmd -q -f samples/privacy-demo/pom.xml test
python scripts/check_repo_hygiene.py
```

macOS / Linux:

```bash
./mvnw -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter
./mvnw -q verify
./mvnw -q -DskipTests install
./mvnw -q -f samples/privacy-demo/pom.xml test
python scripts/check_repo_hygiene.py
```

Expected result: full success with no failing modules, no sample failures, and no hygiene errors.

## 2. Confirm Release Materials

Verify these files are ready:

- `CHANGELOG.md`
- `README.md`
- `README.zh-CN.md`
- `docs/TENANT_ADOPTION_PLAYBOOK.md`
- `docs/TENANT_OBSERVABILITY_GUIDE.md`
- `samples/privacy-demo/README.md`
- `docs/releases/RELEASE_NOTES_v0.4.0.md`
- `docs/GITHUB_RELEASE_COPY_v0.4.0.md`
- `docs/RELEASE_CHECKLIST.md`
- `docs/RELEASE_DRY_RUN_v0.4.0.md`

## 3. Create and Push the Tag

PowerShell:

```powershell
git tag v0.4.0
git push origin v0.4.0
```

Annotated tag option:

```powershell
git tag -a v0.4.0 -m "spring-privacy-guard v0.4.0"
git push origin v0.4.0
```

## 4. Trigger the Release Workflow

GitHub UI inputs:

- `tag`: `v0.4.0`
- `release_name`: `spring-privacy-guard v0.4.0`
- `prerelease`: `false`

## 5. Verify the GitHub Release

- Release title is correct
- Body matches `docs/releases/RELEASE_NOTES_v0.4.0.md`
- Uploaded jars exist for core and starter modules
- Release is marked as a normal release

## 6. Rollout and Comparison Checks

- The sample profile matrix in `samples/privacy-demo/README.md` is consistent with the final scope
- The migration / rollout matrix in `docs/TENANT_ADOPTION_PLAYBOOK.md` matches the release notes
- Native vs fallback observability descriptions match the shipped metrics and sample output

## 7. Rollback If Needed

```bash
git tag -d v0.4.0
git push origin :refs/tags/v0.4.0
```

Then fix the issue, rerun verification, and recreate the tag.
