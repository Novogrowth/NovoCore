# NovoCore

Internal financial and operational system of record for Novotrade S.A. (Java Jives).
Replaces Manager.io, and eventually Prosvasis Go.

- **What to build:** [docs/novocore-product-brief-v4.md](docs/novocore-product-brief-v4.md)
- **How to build it:** [CLAUDE.md](CLAUDE.md) — architecture rules, read every session
- **Decisions and their reasoning:** [docs/decisions/](docs/decisions/)

## Layout

| Path | What it is |
|---|---|
| [backend/](backend/) | Java 25 + Spring Boot 4, Maven multi-module |
| [frontend/](frontend/) | Vite + React + TypeScript + Tailwind + shadcn/ui |
| [docker/](docker/) | Local and production stack: PostgreSQL, app, Caddy (HTTPS) |
| [docs/](docs/) | Product brief and architecture decision records |

The backend's module layout exists to enforce the ports-and-adapters boundary at compile
time rather than by convention — see
[ADR 0003](docs/decisions/0003-module-layout-core-api-boundary.md).

| Module | Purpose |
|---|---|
| `core-api` | The core's published surface. The only thing adapters and modules may depend on |
| `core` | Entities, repositories, service implementations, migrations, REST controllers |
| `adapters` | Reserved, empty until phase 3. External-system integrations |
| `modules` | Reserved, empty until phase 4. Internally-driven functionality |
| `app` | Spring Boot entry point and cross-cutting configuration |
| `architecture-tests` | ArchUnit rules that fail the build on a boundary violation |

## Getting started

Requires JDK 25 and a running Docker daemon. Maven itself is not needed — use the wrapper.

```sh
cd backend
./mvnw verify          # build and run the full test suite
```

```sh
cd docker
cp .env.example .env   # then set NOVOCORE_DB_PASSWORD
docker compose -f compose.yml -f compose.dev.yml up --build
```

Then open <https://localhost>. See [docker/README.md](docker/README.md) for the certificate
warning on first run and for day-to-day commands.

## Current status

**Phase 1 — the core.** Ledger, chart of accounts, journal entries, Goods Receipt,
users/auth/permissions, backups, tests. No adapters or modules yet; that is deliberate, see
`CLAUDE.md`'s scope discipline section.

## Note on where this repository lives

It must sit on local disk on every machine it is developed from, never inside a cloud-sync
folder (Google Drive, OneDrive, Dropbox). Cross-machine access happens exclusively through
git. Automated backups do target Google Drive — that is a backup destination, not a sync
dependency for the working tree.
