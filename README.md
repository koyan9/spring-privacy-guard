# spring-privacy-guard

`spring-privacy-guard` is a Spring Boot privacy protection starter.

## Coordinates

- `io.github.koyan9:spring-privacy-guard-parent:0.1.0`
- `io.github.koyan9:spring-privacy-guard-core:0.1.0`
- `io.github.koyan9:spring-privacy-guard-spring-boot-starter:0.1.0`

## Capabilities

- Annotation-based field masking
- Jackson JSON output masking
- Free-text log sanitization
- Logback layout / appender / turbo-filter integration
- Audit publishing, persistence, querying, and stats

## Repo Layout

- `privacy-guard-core/`
- `privacy-guard-spring-boot-starter/`
- `samples/privacy-demo/`
- `CHANGELOG.md`
- `RELEASE_NOTES_TEMPLATE.md`
- `RELEASE_NOTES_v0.1.0.md`

## Commands

- Verify: `./mvnw -q verify`
- Compile sample: `./mvnw -f samples/privacy-demo/pom.xml -DskipTests compile`