# Repository Guidelines

## Project Structure & Module Organization
This repository is a Maven multi-module Java project. `privacy-guard-core/src/main/java` contains the masking primitives and annotations under `io.github.koyan9.privacy.core`. `privacy-guard-spring-boot-starter/src/main/java` contains Spring Boot auto-configuration, Jackson masking, Logback integration, and audit components. Keep tests beside each module in `src/test/java`. Use `samples/privacy-demo/` to verify end-to-end behavior and configuration examples.

## Build, Test, and Development Commands
- `mvnw.cmd -q verify` or `./mvnw -q verify`: run the full module test suite.
- `mvnw.cmd -q -DskipTests install`: install local artifacts for sample or downstream testing.
- `mvnw.cmd -q -f samples/privacy-demo/pom.xml -DskipTests compile`: confirm the demo still compiles against local changes.
- `python scripts/check_repo_hygiene.py`: validate UTF-8, line endings, and release-doc references.
- `mvnw.cmd -q -Prelease-artifacts package`: build source and javadoc jars used for releases.

## Coding Style & Naming Conventions
Target Java 17+ and follow the existing code style: 4-space indentation for Java, XML, and Markdown, plus 2-space indentation for YAML and JSON. Use `PascalCase` for classes, `camelCase` for methods and fields, and package names rooted at `io.github.koyan9.privacy`. Basic editor defaults live in `.editorconfig`, and Git line endings are enforced through `.gitattributes` with LF for source/docs and CRLF for Windows scripts.

## Testing Guidelines
Tests use JUnit 5, with Spring Boot Test in the starter module where framework wiring is involved. Name test classes `*Test` and prefer behavior-focused methods such as `publishesSanitizedAuditEvent()`. Add or update tests for every behavior change, especially for masking rules, logging sanitization, schema initialization, and audit querying. Run `mvnw.cmd -q verify` before opening a PR.

## Commit & Pull Request Guidelines
Recent commits use short imperative subjects, for example `Fix CI sample dependency installation` and `Harden GitHub Actions workflows`. Keep commits focused and descriptive. PRs should explain the behavior change, call out affected modules, and mention any README, changelog, or release-note updates. If integration behavior changes, note that `samples/privacy-demo/` was revalidated.

## Security & Configuration Tips
Do not commit real personal data, audit records, or secrets in sample configs. When changing audit schema files under `META-INF/privacy-guard/`, keep SQL variants aligned across supported databases.
