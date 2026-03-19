# Release Runbook: v0.2.0

This runbook is the shortest path to publishing `spring-privacy-guard v0.2.0`.

## 1. Final Local Check

Windows:

```powershell
mvnw.cmd -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter
mvnw.cmd -q verify
python scripts/check_repo_hygiene.py
```

macOS / Linux:

```bash
./mvnw -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter
./mvnw -q verify
python scripts/check_repo_hygiene.py
```

Expected result: full success with no failing modules and no hygiene errors.

## 2. Confirm Release Materials

Verify these files are ready:

- `CHANGELOG.md`
- `README.md`
- `README.zh-CN.md`
- `docs/JDBC_PRODUCTION_GUIDE.md`
- `docs/releases/RELEASE_NOTES_v0.2.0.md`
- `docs/GITHUB_RELEASE_COPY_v0.2.0.md`
- `docs/RELEASE_CHECKLIST.md`
- `docs/RELEASE_DRY_RUN_v0.2.0.md`

## 3. Create and Push the Tag

PowerShell:

```powershell
git tag v0.2.0
git push origin v0.2.0
```

Annotated tag option:

```powershell
git tag -a v0.2.0 -m "spring-privacy-guard v0.2.0"
git push origin v0.2.0
```

## 4. Trigger the Release Workflow

GitHub UI path:

- Open the repository on GitHub
- Open the `Actions` tab
- Select workflow: `Release`
- Click `Run workflow`

Input values:

- `tag`: `v0.2.0`
- `release_name`: `spring-privacy-guard v0.2.0`
- `prerelease`: `false`

## 5. Verify the GitHub Release

After the workflow finishes, check:

- the release title is correct
- the body matches `docs/releases/RELEASE_NOTES_v0.2.0.md`
- uploaded jars exist for core and starter modules
- the release is marked as a normal release, not prerelease

## 6. Post-Release Checks

- Open the generated GitHub Release page
- Check that the tag points to the expected commit
- Confirm the workflow log shows successful artifact collection and upload
- Check that `docs/JDBC_PRODUCTION_GUIDE.md` and the README links render correctly in GitHub
- Update any external documentation pages if needed

## 7. Rollback If Needed

If the tag was wrong and the release should be cancelled before broader communication:

```bash
git tag -d v0.2.0
git push origin :refs/tags/v0.2.0
```

Then fix the issue, rerun verification, and recreate the tag.

## 8. Recommended Announcement Summary

Use this short summary if you need one for release notes, chat, or an internal update:

`spring-privacy-guard v0.2.0 adds stable SPI markers, stronger audit and dead-letter operations, JDBC rollout guidance, signed alert delivery, receiver verification, and richer observability across sender and receiver flows.`
