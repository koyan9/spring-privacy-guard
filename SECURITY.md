# Security Policy

## Supported Versions

| Version | Supported |
| --- | --- |
| `0.2.x` | Yes |
| `0.1.x` | No |

Only the latest released minor line should be considered for security fixes.

## Reporting a Vulnerability

- Do not open a public GitHub issue with exploit details, proof-of-concept payloads, secrets, or production data.
- Use GitHub private vulnerability reporting for this repository when it is available.
- If private reporting is not available, open a minimal public issue requesting a private contact path and omit all sensitive details.

Include the following when possible:

- affected version and module, for example `privacy-guard-core` or `privacy-guard-spring-boot-starter`
- impact summary and expected risk level
- reproduction steps, sample configuration, and stack traces with sensitive data removed
- whether a workaround, mitigation, or candidate fix already exists

## Response Expectations

- Best-effort acknowledgement target: within 5 business days
- Best-effort remediation plan: once impact and scope are confirmed
- Coordinated disclosure is preferred after a fix or mitigation is available

## Scope Notes

Security-sensitive areas in this repository include masking behavior, audit persistence, dead-letter import/export, webhook signing and verification, replay protection, and sample admin protections.
