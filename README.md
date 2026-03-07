# spring-privacy-guard

`spring-privacy-guard` is a Spring Boot privacy protection starter.

## Coordinates

- `io.github.koyan9:spring-privacy-guard-parent`
- `io.github.koyan9:spring-privacy-guard-core`
- `io.github.koyan9:spring-privacy-guard-spring-boot-starter`

## Capabilities

- Annotation-based field masking
- Jackson JSON output masking
- Free-text log sanitization
- Logback layout / appender / turbo-filter integration
- Audit publishing, persistence, querying, and stats
- Built-in `IN_MEMORY` and `JDBC` audit repository modes

## Repo Layout

- `privacy-guard-core/`
- `privacy-guard-spring-boot-starter/`
- `samples/privacy-demo/`
- `CHANGELOG.md`
- `RELEASE_NOTES_TEMPLATE.md`

## Commands

- Verify: `./mvnw -q verify`
- Compile sample: `./mvnw -f samples/privacy-demo/pom.xml -DskipTests compile`
