# Running NovoCore locally

Three containers: PostgreSQL, the application, and a Caddy reverse proxy terminating TLS.
HTTPS is present from the first commit rather than retrofitted, per `CLAUDE.md`'s stack
section. The application speaks plain HTTP on the internal network and is never published to
the host — only Caddy binds a port.

## First run

```sh
cd docker
cp .env.example .env
# set NOVOCORE_DB_PASSWORD in .env, e.g.  openssl rand -base64 36
docker compose -f compose.yml -f compose.dev.yml up --build
```

Then open <https://localhost>.

Your browser will warn about the certificate on first use. That is expected: for a local
hostname Caddy issues from its own internal CA rather than over ACME, and the CA is not
trusted by your system yet. Either accept the warning, or trust the CA properly — the root
certificate is inside the `caddy-data` volume at
`/data/caddy/pki/authorities/local/root.crt`:

```sh
docker compose cp caddy:/data/caddy/pki/authorities/local/root.crt ./caddy-local-ca.crt
```

On Windows, import that file into *Trusted Root Certification Authorities* for the current
user, then restart the browser.

### Why plain `localhost` and not `novocore.localhost`

A `*.localhost` subdomain looks tidier but does not resolve on Windows. Browsers resolve
those names internally per RFC 6761, so a browser would work fine — but the OS resolver does
not, so `curl`, PowerShell, health checks and any script would fail against it. That is a bad
trade: it breaks exactly the automated checks you want, and only in a way you notice late.

If you prefer the subdomain, add a hosts entry (needs administrator rights) and set
`NOVOCORE_SITE_ADDRESS=novocore.localhost` in `.env`:

```
127.0.0.1  novocore.localhost
```

## Useful commands

```sh
# Follow application logs
docker compose logs -f app

# psql inside the database container
docker compose exec postgres psql -U novocore -d novocore

# Rebuild just the app after a code change
docker compose -f compose.yml -f compose.dev.yml up -d --build app

# Stop, keeping data
docker compose down

# Stop and DESTROY the database volume. This deletes all data.
docker compose down -v
```

In dev, PostgreSQL is also published on `127.0.0.1:5432` for psql, an IDE database tool, or a
JDBC client. It is bound to loopback deliberately, so a laptop on an untrusted network is not
serving the database to it.

## The image build is not the test gate

`docker/Dockerfile` builds with `-DskipTests`. This is not an oversight: the test suite runs
against real PostgreSQL through Testcontainers, which would require a Docker daemon inside
the image build. Correctness is enforced by `mvn verify` locally and in CI.

```sh
cd backend
./mvnw verify        # requires a running Docker daemon for the Testcontainers tests
```

## Production differences

Set `NOVOCORE_SITE_ADDRESS` to the real hostname and omit `compose.dev.yml`:

```sh
docker compose up -d --build
```

Caddy then obtains a publicly trusted certificate over ACME automatically, which requires
that the hostname resolves to the host and that ports 80 and 443 are reachable from the
internet. Without `compose.dev.yml`, PostgreSQL is not published to the host at all and
logging stays at `INFO` with error details suppressed in responses.

## Still open

- **The physical hosting machine is undecided** (brief §13), so nothing here is tuned to
  real hardware — no memory limits, no connection-pool sizing, no PostgreSQL tuning.
- **Backups are not part of this stack yet.** They arrive in build step 12 and need the two
  Google Drive destinations and credentials. Note brief §13 lists backup *restore* as
  untested — the plan is to close that with an automated restore-verification job rather than
  leaving it as an assumption.
- **PostgreSQL 17** is pinned here and in `backend/pom.xml`. Both must move together; the
  test suite and production must not run different major versions, because the ledger's
  guarantees are database constraints.
