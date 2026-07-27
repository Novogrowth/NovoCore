# ADR 0003 — Multi-module layout with a separate `core-api` artifact

**Date:** 2026-07-27
**Status:** Accepted

## Decision

The backend is a Maven multi-module build:

| Module | Contains | Depends on |
|---|---|---|
| `core-api` | Service interfaces, DTOs, value objects (`Money`, `Quantity`, `SubLedgerRef`), enums | nothing in this repo |
| `core` | JPA entities, repositories, service implementations, Flyway migrations, REST controllers | `core-api` |
| `adapters` | *reserved, empty* — one submodule per external system, from phase 3 on | `core-api` **only** |
| `modules` | *reserved, empty* — one submodule per internally-driven module, from phase 4 on | `core-api` **only** |
| `app` | Spring Boot entry point, security wiring, cross-cutting config | `core`, and later `adapters`/`modules` |
| `architecture-tests` | ArchUnit rules | everything; nothing depends on it |

## Context

`CLAUDE.md` rule 3 forbids adapters and modules from touching the database directly or
importing core-internal classes, and requires an automated check that fails the build.

The alternative considered was a single module with package-level boundaries enforced by
ArchUnit alone. That was rejected because it leaves rule 3 enforced *only* by a test —
one that can be disabled, skipped, or (as ADR 0002 shows) broken by an unrelated
dependency upgrade. It also means IDE autocomplete actively suggests core internals while
writing adapter code, so the wrong thing is the path of least resistance.

## Consequences

- A future adapter declaring a dependency on `core-api` **cannot** import `Account`
  (the JPA entity), any Spring Data repository, or any `*ServiceImpl`, because those
  classes are not on its compile classpath. Rule 3 becomes a compile error, not a test
  failure.
- ArchUnit is now a *second* line of defence covering what the module graph cannot:
  the money-type rules, and intra-module boundaries such as REST controllers in
  `core` being forbidden from reaching past `core-api` into repositories.
- `core-api` must stay free of persistence and framework types, or the boundary leaks
  by another route. An ArchUnit rule enforces that `core-api` does not reference
  `jakarta.persistence` or Spring Data.
- Cost: more POM ceremony, and adding a core capability means editing two modules
  (interface, then implementation). This is the intended friction — rule 3 says that when
  an adapter needs something the core does not expose, the fix is to add the interface
  method, not to reach around it.

## Note on REST controllers

Controllers for core entities live in `core` (package `..core.web..`), not in `app`.
The web UI is the core's own front door, not an "adapter" in the ports-and-adapters sense —
adapters here mean external-system integrations (Go, WooCommerce, myDATA). `app` is only
the Spring Boot bootstrap and cross-cutting configuration.

Because controllers sit in the same module as the implementations, an ArchUnit rule
restricts `..core.web..` to depending on `..core.api..` only, so controllers cannot
bypass the service interfaces and hit repositories directly.
