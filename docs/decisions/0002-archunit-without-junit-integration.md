# ADR 0002 — ArchUnit used as a plain library, not via `archunit-junit5`

**Date:** 2026-07-27
**Status:** Accepted

## Decision

Depend on `com.tngtech.archunit:archunit` (the core library) and write the architecture
rules as ordinary JUnit tests that call `rule.check(classes)` directly. Do **not** depend
on `archunit-junit5`.

## Context

Architecture rule 3 in `CLAUDE.md` requires an ArchUnit check that fails the build on
violation, so this check must be dependable — it is load-bearing for the whole
ports-and-adapters boundary.

Spring Boot 4.1.0 manages JUnit Jupiter 6.0.3, which sits on JUnit Platform 2.x.
ArchUnit's JUnit integration artifact is `archunit-junit5`, whose engine binds to JUnit
Platform 1.x. At the time of writing there is no `archunit-junit6` artifact published to
Maven Central (verified: 404), and the latest `archunit-junit5` is 1.4.2.

Relying on `archunit-junit5` would therefore mean betting the build-breaking architecture
check on cross-major-version compatibility between two independently released test
frameworks — and it would block us from upgrading either one until the other catches up.

What `archunit-junit5` actually provides is convenience: `@AnalyzeClasses`, `@ArchTest`
fields, and class-graph caching between test classes. None of that is required to express
or enforce a rule.

## Consequences

- Rules are written as plain `@Test` methods calling `rule.check(importedClasses)`.
- We import the class graph once in a small holder with a static cached `JavaClasses`,
  which recovers the caching benefit that `@AnalyzeClasses` would have given us.
- ArchUnit and JUnit can now be upgraded independently.
- Slightly more boilerplate per rule. Accepted.

## Open

ArchUnit 1.4.2 must be able to read Java 25 class files (major version 69). This is
verified empirically by the architecture test suite actually running under Java 25 — if
ArchUnit cannot parse the bytecode, the suite fails loudly rather than silently importing
zero classes. See the guard test that asserts a non-trivial class count was imported;
that guard exists specifically so a parsing failure can never masquerade as "all rules
pass".
