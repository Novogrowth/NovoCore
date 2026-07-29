# ADR 0013 — Backups: encrypted before they leave, off-site reported separately from success, and proven restorable

**Date:** 2026-07-29
**Status:** Accepted — implemented in build step 12 (migration V23)

Brief §3 asks for "automated backups to two Google Drive accounts (restore untested)". Brief §13
lists the restore test as an outstanding risk. This step does the first and closes the second,
because an untested backup is a belief about a file rather than a backup.

## 1. The encryption key is an environment variable, and cannot be a setting

Step 11 put the SMTP password in `Settings` and argued for it. This is the opposite decision, and
it is not a reversal — one fact decides it:

> **The `setting` table is inside the dump.**

A backup key kept there would be encrypted inside the artefact it exists to decrypt. Reading it
would require the backup it is needed to read. There is no ordering of those steps that terminates.

So the key comes from `NOVOCORE_BACKUP_ENCRYPTION_KEY`, is never written to the database, the audit
log, or any log line, and a **16-hex-character fingerprint** is recorded on each artefact instead.

**The obligation this creates is stated rather than assumed:** the key must be recorded outside this
system, in a password manager. `docker/.env` is gitignored and machine-local, so if that is the only
copy then losing the host loses the database *and* every backup of it simultaneously — the one
occasion both were meant to help. The application logs this on every start where a key is present.

A missing key refuses to back up rather than generating one. Generating would require storing it
somewhere to survive a restart, and the only place available is the database.

**Drive credentials do live in Settings**, and the distinction is not arbitrary: losing them costs a
repeat of the OAuth consent flow, not access to an existing backup. They are inside the encrypted
dump, so somebody holding both the dump and the key also holds the Drive tokens — accepted, because
that person already holds the whole database.

## 2. Off-site is reported separately from success

`SUCCEEDED` means the artefact was written and checksummed. Whether a copy reached Drive is
per-destination on `backup_upload`, summarised by `BackupView.isOffsite()`.

A dump that wrote perfectly to local disk and reached no destination has protected against a dropped
table and against nothing else — not a failed disk, not a lost machine, not a ransomed host.
Collapsing the two would let "backup succeeded" mean a file that exists only on the machine the
backup protects against losing. The service logs an **error** for that state specifically.

Consequences:

- **An upload failure does not fail the backup.** The artefact is already safe on disk; recording
  the run as failed over a network error would discard a good backup and make "when did we last
  dump successfully?" unanswerable.
- **`NOT_CONFIGURED` is its own status**, not a failure. A destination nobody has set up needs a
  different response from one that was tried and rejected, and recording it as a failure would bury
  it in a list of transient errors — while recording it as nothing at all would let the system
  believe it has two off-site copies when it has none. Every run records a row per destination.
- **One destination failing never stops the other.** Two accounts exist precisely so that one being
  unreachable is survivable.

## 3. OAuth refresh tokens, not a service account

A service account has no Drive storage quota of its own. Sharing a folder from an ordinary Google
account with one does not help: files it creates are owned by the service account and counted
against its zero quota, so every upload fails with a storage error that reads like a permissions
problem. Shared Drives solve it and require Google Workspace, which `novotrade.gr` is not — its mail
is self-hosted on `mail.novotrade.gr`.

So each destination carries its own client id, client secret, refresh token and folder id.
**All four or none**: a destination with three of them is not slightly misconfigured, it is one that
fails every night while appearing to be set up.

Scope is `drive.file`, not full Drive access — it grants NovoCore access only to files it creates
itself, so a compromised token cannot read the rest of that account's Drive.

## 4. Plain `HttpClient`, not the Google client library

`google-api-services-drive` pulls a large transitive tree to wrap four calls. What it costs in
exchange is testability: the failures that matter are protocol-level — an expired refresh token, a
deleted folder, a 403 for quota — and reproducing those through that library means mocking the
library rather than the protocol.

With the base URLs as properties, `StubDriveServer` produces all of them over a real socket with no
credentials and no network, and asserts that the bytes which arrived are byte-for-byte the artefact
on disk. Same reasoning as ADR 0002 taking ArchUnit as a plain library.

## 5. The plaintext dump never reaches disk — except once, deliberately

`pg_dump`'s stdout is piped straight through AES-256-GCM into the artefact. No intermediate file
exists to be read by anything else on the host, or left behind by a crash.

The one exception is the restore check: `pg_restore` reads a file, not a pipe. The decrypted copy is
written owner-only into the backup directory and deleted in a `finally`. Stated as a trade-off
rather than left to be discovered, and it is why verification runs on a schedule rather than
continuously.

**GCM, and `CipherInputStream` is deliberately not used** — it swallows `AEADBadTagException` on
close and reports end-of-stream, so a truncated backup would decrypt to a short plaintext with no
error and hand `pg_restore` a partial dump. The read path loops over `update` and calls `doFinal`
itself. Both failures have tests.

## 6. Retention: 7 rolling, plus every month's last, forever

As specified. Stated positively it needs no month-end special case:

> A backup is its month's archive **if and only if no later successful backup exists in the same
> calendar month.**

That is automatically right when a month's run failed on the 31st (the 30th's is archived), and a
month with no successful backup designates nothing rather than reaching back to re-designate an
earlier month's artefact.

- **The calendar zone is load-bearing.** A 01:30 Athens backup on the 1st is the previous month in
  UTC; deciding the month without a zone would archive the wrong artefact twelve times a year,
  silently.
- **Only successful runs are candidates.** Letting failures count towards the rolling seven would
  mean a week of failures evicting the last good backups — the one week you would most want them.
- **Applied identically to local disk and to every destination**, so "is this backup still
  available?" has one answer.
- **The rule is pure logic in its own class**, tested against explicit dates. Every other component
  here can be fixed and re-run; a retention rule that deleted the wrong artefact has already
  deleted it.
- **The `backup_run` row outlives the artefact.** Deleting rows would make the history a list of
  surviving files rather than of attempts, and "we have backed up every night since March" would
  become unanswerable the moment retention started working.

## 7. The restore check asserts the books, not the file

A `pg_restore` that exits zero proves the archive was well-formed. It does not prove the database
means anything. So the restored copy is queried:

- the migration history came back, at the version the live database is on;
- `account`, `setting`, `journal_entry` and `journal_line` have the same row counts as live;
- **the restored ledger balances** — `sum(debits) - sum(credits) = 0`.

That last one is what turns "the file restored" into "the books restored". `CLAUDE.md` rule 6 makes
debits equal credits structural in the live database; a backup that restores a ledger which no
longer balances has restored a file and lost the business.

Findings are kept on a **passing** check too. A green flag with nothing behind it is the same
unverified claim brief §13 already objected to, wearing a tick.

The scratch database name is whitelisted to `[a-z0-9_]+` and **refused if it equals the live
database** — the check begins by dropping it.

## Defects found while building this, both by running it rather than reasoning about it

1. **`RestoreVerifier` called its own `@Transactional` methods.** A self-invocation never goes
   through the proxy, so those annotations would have done nothing at all, silently, until a
   failure needed recording and was not. Split into `RestoreCheckJournal` — the same lesson step 11
   recorded for `EmailOutbox`, rediscovered because it was written the obvious way.
2. **Artefact names collided within one second.** Dismissed while designing as pathological; the
   test suite hit it immediately, and so would a manual backup taken in the same second the
   scheduler fires. Fixed in production code with a `-2`, `-3` suffix rather than by spacing the
   tests out, and rather than putting milliseconds into every name to accommodate a rare case.

A third was avoided rather than fixed: reading `spring.datasource.url` to find the database. Under
Testcontainers' `@ServiceConnection` that property does not exist, which failed the whole context —
and it is the general case of the same mistake, since a dump driven by a property that had drifted
from the pool would faithfully back up the wrong database while looking healthy.
`DatabaseConnectionProvider` reads the connection pool instead.
