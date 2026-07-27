# ADR 0001 — Toolchain: Java 25 LTS, Maven, Spring Boot 4.1

**Date:** 2026-07-27
**Status:** Accepted

## Decision

- **JDK:** Eclipse Temurin 25.0.3+9 (LTS).
- **Build tool:** Apache Maven 3.9.16.
- **Framework:** Spring Boot 4.1.0.

## Context

`CLAUDE.md` fixes the stack as Java + Spring Boot + PostgreSQL, but not the versions.
Java 21 and Java 25 were both offered; Java 25 was chosen for the longer support runway.

Java 25 has a direct consequence that was flagged before committing to it: Spring Boot
3.5.x does not support Java 25, so **Java 25 forces the Spring Boot 4.x line**. This was
accepted knowingly — the trade is a newer LTS JDK against a less battle-tested framework
line.

Spring Boot 4.1.0 was chosen over the more mature 4.0.7 patch line because it is GA, it
is the newest release line (longest support runway, consistent with the Java 25 choice),
and Java 25 support is more settled in 4.1 than in 4.0, which predates it.

## Consequences

Spring Boot 4.1.0's dependency management pulls in several major versions that differ from
what a Boot 3.5 project would use. These are the ones that affect us:

| Dependency | Version managed by Boot 4.1.0 | Note |
|---|---|---|
| Spring Framework | 7.0.8 | |
| Spring Security | 7.1.0 | Affects step 4 (auth/permissions) |
| Hibernate | 7.4.1.Final | |
| JUnit Jupiter | **6.0.3** | Major bump — see ADR 0002 |
| Testcontainers | **2.0.5** | Major bump from the 1.21.x line |
| Flyway | 12.4.0 | Boot-managed; do not override |
| PostgreSQL JDBC | 42.7.11 | |

We take Boot's managed versions rather than pinning our own, so that upgrades stay
coherent. ArchUnit is the one exception, because Boot does not manage it (ADR 0002).

Maven is installed per-user at `C:\Users\kosta\tools\apache-maven-3.9.16` alongside the
JDK at `C:\Users\kosta\tools\jdk-25.0.3+9`, rather than machine-wide via `winget`, because
the development shell is not elevated and a machine-wide install would stall on a UAC
prompt. `JAVA_HOME` and `PATH` are set at user scope. A committed Maven Wrapper means
contributors and CI do not depend on this specific layout.

## Verification

Version facts here were read from `maven-metadata.xml` and the published POMs on
`repo1.maven.org`, not from memory — the `search.maven.org` index was found to be stale
(returning nothing newer than mid-2025) and should not be trusted for version checks.
