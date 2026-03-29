# Release Process

This document describes the current Maven Central publication path for `spring-privacy-guard`.

## Current Scope

- The repository is maintained through `v0.5.0`.
- The GitHub release for `v0.5.0` already exists.
- Maven Central publication is prepared as an explicit maintainer step through the root `central-publish` Maven profile.
- The existing GitHub release workflow is intentionally left as the GitHub-release path; we do not retag or retrofit the historical `v0.5.0` tag just to add Central-specific automation.

## Current Prerequisites

Before a real Central deploy, confirm all of the following:

- the `io.github.koyan9` namespace is verified in Sonatype Central Portal
- a Central Portal user token exists
- local Maven settings contain a `server` with id `central`
- local GPG signing is available
- the release signing key is available in the local keyring
- credentials and passphrases are sourced from environment variables or encrypted Maven settings, not plaintext repository files

## Recommended Local Settings Pattern

Use a local-only `settings.xml` pattern similar to:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>${env.MAVEN_CENTRAL_USERNAME}</username>
      <password>${env.MAVEN_CENTRAL_PASSWORD}</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>central</id>
      <properties>
        <gpg.passphrase>${env.MAVEN_GPG_PASSPHRASE}</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>central</activeProfile>
  </activeProfiles>
</settings>
```

If `gpg` is not on `PATH`, define `gpg.executable` in a local-only Maven profile instead of committing it into this repository.

## Available Publish Profiles

The root `pom.xml` provides:

- `release-artifacts`: attaches sources and javadocs for release preparation
- `central-publish`: attaches sources and javadocs, signs artifacts with GPG, and publishes through `org.sonatype.central:central-publishing-maven-plugin`

The `central-publish` profile defaults to:

- `central.publish.auto=false`
- `central.publish.waitUntil=validated`

That keeps local publication in explicit maintainer control while still allowing CI overrides if needed later.

## Recommended Validation Commands

Run these from the repository root before a real deploy:

- Windows:
  - `mvnw.cmd -q verify`
  - `mvnw.cmd -q -Prelease-artifacts verify`
  - `mvnw.cmd -q -Pcentral-publish -DskipTests verify`
- macOS / Linux:
  - `./mvnw -q verify`
  - `./mvnw -q -Prelease-artifacts verify`
  - `./mvnw -q -Pcentral-publish -DskipTests verify`

## Recommended Local Publish Command

- Windows:
  - `mvnw.cmd -q -Pcentral-publish -DskipTests deploy`
- macOS / Linux:
  - `./mvnw -q -Pcentral-publish -DskipTests deploy`

If your local GPG setup requires loopback passphrase handling, provide the passphrase through the path your local setup expects before running the command.

## Post-Publish Verification

Prefer checking direct repository URLs first:

- Core:
  - `https://repo1.maven.org/maven2/io/github/koyan9/spring-privacy-guard-core/0.5.0/`
- Starter:
  - `https://repo1.maven.org/maven2/io/github/koyan9/spring-privacy-guard-spring-boot-starter/0.5.0/`

Practical rule:

- if the direct repository URL returns `200 OK` but search indexing still lags, do not republish the same version
- treat partial visibility between `core` and `starter` as an ambiguous state and investigate before rerunning deploy

## Why This Stays Manual

Because the repository is intentionally capped at `v0.5.0` and the GitHub tag / release already exist, the lowest-risk Central path is:

1. keep the historical GitHub release state unchanged
2. add an explicit local `central-publish` path for maintainers
3. avoid retagging `v0.5.0` or silently republishing from a different release automation contract
