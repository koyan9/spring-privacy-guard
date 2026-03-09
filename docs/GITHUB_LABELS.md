# GitHub Labels Guide

Recommended labels for `spring-privacy-guard` issue and pull request triage.

## Type Labels

| Label | Purpose |
| --- | --- |
| `bug` | Reproducible defect or regression |
| `enhancement` | New feature, improvement, or API extension |
| `documentation` | README, release docs, examples, or contributor guidance |
| `security` | Security-related hardening or coordinated disclosure tracking |
| `question` | Usage or integration question |

## Area Labels

| Label | Purpose |
| --- | --- |
| `area:core` | `privacy-guard-core` masking and core contracts |
| `area:starter` | Spring Boot starter, auto-configuration, Jackson, logging |
| `area:audit` | Audit persistence, dead letters, replay, import/export |
| `area:alerts` | Webhook/email callbacks, signatures, replay protection |
| `area:sample` | `samples/privacy-demo` behavior or example configuration |
| `area:docs` | Documentation, release notes, checklists, templates |
| `area:ci` | GitHub Actions, release workflow, repository hygiene checks |

## Status Labels

| Label | Purpose |
| --- | --- |
| `needs-repro` | More detail or a reliable reproduction is required |
| `needs-docs` | Documentation follow-up is required before merge |
| `blocked` | Waiting on another issue, decision, or external dependency |
| `good first issue` | Safe onboarding task for new contributors |
| `help wanted` | Maintainer would welcome community help |

## Suggested Triage Rules

- Apply one type label and at least one area label
- Add `security` only after reviewing `SECURITY.md`
- Add `needs-docs` when behavior changes but README, changelog, or release notes are missing
- Use `good first issue` only when scope is small and verification steps are clear