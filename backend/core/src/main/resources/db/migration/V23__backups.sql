-- Step 12: automated backups — a scheduled encrypted pg_dump, copied to two Google Drive
-- accounts, pruned on a stated retention rule, and proved restorable.
--
-- Brief §3 asks for "automated backups to two Google Drive accounts (restore untested)" and §13
-- lists the restore test as an outstanding risk. This step does the first and closes the second:
-- an untested backup is a belief, not a backup, so the restore check is part of the step rather
-- than a later intention.
--
-- WHY THE DUMP IS ENCRYPTED AND WHERE THE KEY LIVES
--
-- The dump contains everything: the ledger, customers, suppliers, attachment bytes, the audit
-- log, password hashes, and the settings table — which holds the SMTP password and the Drive
-- credentials. It is then uploaded to two consumer Google accounts. Encrypting it at rest is
-- therefore not optional, and AES-256-GCM is applied in the application before a single byte
-- leaves the host.
--
-- **The key is an environment variable and is deliberately NOT in this table.** This is the exact
-- opposite of the decision step 11 made for the SMTP password, and the reason is specific rather
-- than a change of heart: the settings table is INSIDE the dump. A key stored here would be
-- encrypted inside the very artefact it exists to decrypt, so a restore would need the backup to
-- read the key needed to read the backup. There is no arrangement of that which works.
--
-- The operational consequence is stated here because it has teeth: NOVOCORE_BACKUP_ENCRYPTION_KEY
-- must be recorded somewhere OUTSIDE this system — a password manager — or every backup ever taken
-- is unrecoverable the day the host dies. `encryption_key_fingerprint` below exists so a restore
-- that is handed the wrong key is told so, instead of failing as corrupt data.
--
-- WHY DRIVE CREDENTIALS *DO* LIVE IN SETTINGS
--
-- No circularity: losing them costs a repeat of the OAuth consent flow, not access to an existing
-- backup. They are inside the encrypted dump, which means somebody holding both the dump and the
-- key also holds the Drive tokens — accepted, because that person already holds the whole database.

-- ---------------------------------------------------------------------------------------------
-- One backup attempt
-- ---------------------------------------------------------------------------------------------

CREATE TABLE backup_run (
    id                        bigserial    PRIMARY KEY,

    -- The artefact's name, which is also its identity on every destination. Timestamped and
    -- unique, so the local directory and both Drive folders can be compared by name alone and
    -- retention can be reasoned about without downloading anything.
    artefact_name             varchar(200) NOT NULL UNIQUE,

    started_at                timestamptz  NOT NULL,
    finished_at               timestamptz,
    status                    varchar(20)  NOT NULL,

    -- Size and checksum of the ENCRYPTED artefact — what was actually written and uploaded, so a
    -- file fetched back from Drive can be checked against this without decrypting it first.
    size_bytes                bigint,
    checksum_sha256           varchar(64),

    -- First 16 hex characters of SHA-256 over the key. Enough to tell two keys apart, useless for
    -- recovering one. Without it, restoring with a rotated key reports a GCM tag mismatch, which
    -- reads as "the backup is corrupt" — the most alarming possible way to say "wrong key".
    encryption_key_fingerprint varchar(16),

    -- Retained rather than pruned, and why. Recomputed by the retention pass rather than stored
    -- as policy, so changing the rule re-evaluates history instead of grandfathering it.
    monthly_archive           boolean      NOT NULL DEFAULT false,

    pruned_at                 timestamptz,
    error                     text,

    created_at                timestamptz  NOT NULL DEFAULT now(),
    created_by                varchar(100) NOT NULL DEFAULT 'system',
    updated_at                timestamptz  NOT NULL DEFAULT now(),
    updated_by                varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT backup_run_status_known
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),

    -- A finished run says what it produced; a running one has not produced it yet. Same stance as
    -- email_outbox's status/timestamp agreement: an impossible row cannot be stored at all.
    CONSTRAINT backup_run_status_matches_timestamps CHECK (
        (status = 'RUNNING'   AND finished_at IS NULL)
     OR (status <> 'RUNNING'  AND finished_at IS NOT NULL)
    ),
    CONSTRAINT backup_run_success_states_its_artefact CHECK (
        status <> 'SUCCEEDED' OR (size_bytes IS NOT NULL
                              AND checksum_sha256 IS NOT NULL
                              AND encryption_key_fingerprint IS NOT NULL)
    ),
    -- A failure always says why. A FAILED backup with no error is the silent drop this table
    -- exists to make impossible — the same rule email_outbox applies to a failed message.
    CONSTRAINT backup_run_failure_states_a_reason CHECK (
        status <> 'FAILED' OR (error IS NOT NULL AND btrim(error) <> '')
    ),
    CONSTRAINT backup_run_size_positive       CHECK (size_bytes IS NULL OR size_bytes > 0),
    CONSTRAINT backup_run_checksum_is_hex     CHECK (checksum_sha256 IS NULL
                                                     OR checksum_sha256 ~ '^[0-9a-f]{64}$'),
    -- Only a successful backup is worth keeping; a failed one has no artefact to archive.
    CONSTRAINT backup_run_archive_succeeded   CHECK (NOT monthly_archive OR status = 'SUCCEEDED')
);

COMMENT ON TABLE backup_run IS
    'One backup attempt. The artefact is an AES-256-GCM encrypted pg_dump; the key is an '
    'environment variable and is deliberately absent from the settings table, which is itself '
    'inside the dump.';

COMMENT ON COLUMN backup_run.encryption_key_fingerprint IS
    'SHA-256 of the key, first 16 hex chars. Lets a restore say "wrong key" instead of "corrupt".';

CREATE INDEX backup_run_recent_idx ON backup_run (started_at DESC);

-- The retention rule's working set: successful, not yet pruned, newest first.
CREATE INDEX backup_run_retained_idx
    ON backup_run (started_at DESC)
    WHERE status = 'SUCCEEDED' AND pruned_at IS NULL;

-- ---------------------------------------------------------------------------------------------
-- Where each copy went
-- ---------------------------------------------------------------------------------------------
--
-- A child table rather than two columns on backup_run, because "two destinations" is a
-- configuration fact and not a schema fact. A third Drive account, or an S3 bucket later, is a
-- row here and a settings block — not a migration and not a pair of columns named after Google.

CREATE TABLE backup_upload (
    id               bigserial    PRIMARY KEY,
    backup_run_id    bigint       NOT NULL REFERENCES backup_run (id) ON DELETE CASCADE,

    -- Which configured destination this is: 'primary' / 'secondary'. Matches the settings prefix
    -- backup.drive.<key>.*, so a row and its configuration are findable from each other.
    destination_key  varchar(40)  NOT NULL,
    destination_label varchar(200),

    status           varchar(20)  NOT NULL,
    -- Drive's own file id. The one external identifier this table holds, and it is held HERE
    -- rather than on backup_run precisely because CLAUDE.md rule 2 keeps external ids off core
    -- entities: a backup_upload row IS the adapter's mapping table for this destination.
    remote_file_id   varchar(200),
    uploaded_at      timestamptz,
    attempts         integer      NOT NULL DEFAULT 0,
    error            text,

    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       varchar(100) NOT NULL DEFAULT 'system',
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT backup_upload_status_known
        CHECK (status IN ('PENDING', 'UPLOADED', 'FAILED', 'NOT_CONFIGURED', 'PRUNED')),
    CONSTRAINT backup_upload_one_row_per_destination UNIQUE (backup_run_id, destination_key),
    CONSTRAINT backup_upload_uploaded_states_where CHECK (
        status <> 'UPLOADED' OR (remote_file_id IS NOT NULL AND uploaded_at IS NOT NULL)
    ),
    CONSTRAINT backup_upload_failure_states_a_reason CHECK (
        status <> 'FAILED' OR (error IS NOT NULL AND btrim(error) <> '')
    ),
    CONSTRAINT backup_upload_attempts_not_negative CHECK (attempts >= 0)
);

COMMENT ON TABLE backup_upload IS
    'One copy of one backup at one destination. A backup that ran but reached nowhere off-site '
    'is visible here as such, rather than reported as a success.';

CREATE INDEX backup_upload_run_idx ON backup_upload (backup_run_id);
CREATE INDEX backup_upload_unsent_idx
    ON backup_upload (backup_run_id)
    WHERE status IN ('PENDING', 'FAILED');

-- ---------------------------------------------------------------------------------------------
-- Proof that a backup restores
-- ---------------------------------------------------------------------------------------------
--
-- Brief §13's outstanding risk. A backup nobody has restored is a belief about a file, and the
-- usual way that belief fails is silent: a dump that writes and uploads perfectly and cannot be
-- read back. This restores the artefact into a scratch database and asserts against it.

CREATE TABLE restore_check (
    id                 bigserial    PRIMARY KEY,
    backup_run_id      bigint       NOT NULL REFERENCES backup_run (id) ON DELETE CASCADE,

    started_at         timestamptz  NOT NULL,
    finished_at        timestamptz,
    status             varchar(20)  NOT NULL,

    -- What was actually checked, as text, so a passing check is evidence and not just a green
    -- flag: schema version, row counts, and whether the restored ledger balances.
    findings           text,
    error              text,

    created_at         timestamptz  NOT NULL DEFAULT now(),
    created_by         varchar(100) NOT NULL DEFAULT 'system',
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    updated_by         varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT restore_check_status_known
        CHECK (status IN ('RUNNING', 'PASSED', 'FAILED')),
    CONSTRAINT restore_check_status_matches_timestamps CHECK (
        (status = 'RUNNING'  AND finished_at IS NULL)
     OR (status <> 'RUNNING' AND finished_at IS NOT NULL)
    ),
    CONSTRAINT restore_check_failure_states_a_reason CHECK (
        status <> 'FAILED' OR (error IS NOT NULL AND btrim(error) <> '')
    )
);

COMMENT ON TABLE restore_check IS
    'A backup restored into a scratch database and asserted against — including that the '
    'restored ledger still balances. Brief §13 lists this as the risk it closes.';

CREATE INDEX restore_check_run_idx ON restore_check (backup_run_id, started_at DESC);

-- ---------------------------------------------------------------------------------------------
-- Configuration
-- ---------------------------------------------------------------------------------------------
--
-- RETENTION, exactly as specified:
--
--   * the 7 most recent successful backups, rolling; AND
--   * the last successful backup of each calendar month, kept forever, uncapped.
--
-- Where "the last of the month" means the most recent successful backup at or before month-end —
-- so a month whose scheduled run failed on the 31st archives the 30th's instead, and a month with
-- no successful backup at all archives nothing rather than reaching back and re-designating an
-- earlier month's artefact. That needs no month-end special case: a backup is a monthly archive
-- if and only if no later successful backup exists in the same calendar month.
--
-- Applied identically to local staging and to every Drive destination, so the three places agree
-- and "is it still on Drive?" has the same answer as "is it still on disk?".
--
-- The calendar month needs a zone, or a backup taken at 01:30 on the 1st of a month in Athens
-- lands in the previous month in UTC and archives the wrong artefact.

INSERT INTO setting (setting_key, value, description) VALUES
    ('backup.local-directory',
     '/var/lib/novocore/backups',
     'Where the encrypted dump is written before upload, and where the retained copies stay. '
     'Must be a volume that survives container replacement; compose mounts one.'),

    ('backup.retention.daily-count',
     '7',
     'How many of the most recent successful backups are kept regardless of date.'),

    ('backup.retention.monthly',
     'FOREVER',
     'How many calendar-month archives are kept: FOREVER, or a number of months. A monthly '
     'archive is the last successful backup of its calendar month.'),

    ('backup.calendar-zone',
     'Europe/Athens',
     'Zone deciding which calendar month a backup belongs to. Without it a 01:30 Athens backup '
     'on the 1st would count as the previous month in UTC and archive the wrong artefact.'),

    ('backup.restore-check.database',
     'novocore_restore_check',
     'Scratch database the restore verification creates, restores into, asserts against and '
     'drops. Must not be the live database name; the service refuses if it is.'),

-- The two Drive destinations. Seeded BLANK, not with placeholders: a placeholder that looks like
-- configuration is how a system ends up believing it has off-site copies it does not have. Each
-- destination is skipped with a loud warning and recorded NOT_CONFIGURED until all four values
-- are present.
--
-- OAuth refresh tokens rather than a service account: a service account has no Drive storage
-- quota of its own, so uploading into a folder shared from an ordinary Google account fails on
-- quota. Shared Drives would solve it and require Workspace, which novotrade.gr is not — its mail
-- is self-hosted on mail.novotrade.gr.

    ('backup.drive.primary.label',
     'Primary Google Drive',
     'Human-readable name for this destination, shown against each upload.'),
    ('backup.drive.primary.folder-id',
     '',
     'Drive folder id that receives the artefacts. The trailing path segment of the folder URL.'),
    ('backup.drive.primary.client-id',
     '',
     'OAuth client id for this destination.'),

    ('backup.drive.secondary.label',
     'Secondary Google Drive',
     'Human-readable name for this destination, shown against each upload.'),
    ('backup.drive.secondary.folder-id',
     '',
     'Drive folder id that receives the artefacts. The trailing path segment of the folder URL.'),
    ('backup.drive.secondary.client-id',
     '',
     'OAuth client id for this destination.');

-- The two credentials per destination, marked secret so they are redacted from API responses and
-- never written to the audit log. Blank until the OAuth consent flow has been completed for each
-- account; they arrive by environment variable on one start, exactly as the SMTP password does.
INSERT INTO setting (setting_key, value, secret, description) VALUES
    ('backup.drive.primary.client-secret',
     '', true,
     'OAuth client secret. Supplied once via NOVOCORE_BACKUP_DRIVE_PRIMARY_CLIENT_SECRET.'),
    ('backup.drive.primary.refresh-token',
     '', true,
     'OAuth refresh token for the primary account. Supplied once via '
     'NOVOCORE_BACKUP_DRIVE_PRIMARY_REFRESH_TOKEN.'),
    ('backup.drive.secondary.client-secret',
     '', true,
     'OAuth client secret. Supplied once via NOVOCORE_BACKUP_DRIVE_SECONDARY_CLIENT_SECRET.'),
    ('backup.drive.secondary.refresh-token',
     '', true,
     'OAuth refresh token for the secondary account. Supplied once via '
     'NOVOCORE_BACKUP_DRIVE_SECONDARY_REFRESH_TOKEN.');
