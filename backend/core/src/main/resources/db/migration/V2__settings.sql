-- Settings: the single place configuration lives that an operator can change without a
-- redeploy.
--
-- CLAUDE.md requires SMTP credentials to be configured once here and reached only through
-- the shared email service, precisely so credentials do not get scattered across modules.
-- Brief §6 also requires the rounding threshold to be configurable.
--
-- Values are stored as text and parsed by typed accessors on SettingsService, rather than
-- being spread across typed columns or one column per type. A settings table is read rarely
-- and written rarely; keeping it a plain key/value store means adding a setting is an INSERT
-- rather than a migration, and the typing lives in Java where the default and the validation
-- already are.

CREATE TABLE setting (
    setting_key  varchar(100) PRIMARY KEY,
    value        text         NOT NULL,
    -- Whether this value is sensitive (an SMTP password, an API credential). Read by the
    -- API layer to redact it in responses and by the audit log to record that a value
    -- changed without recording the value itself.
    secret       boolean      NOT NULL DEFAULT false,
    description  text,

    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   varchar(100) NOT NULL DEFAULT 'system',
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   varchar(100) NOT NULL DEFAULT 'system',

    -- Dotted lower-case keys, so the namespace stays predictable and a typo in casing
    -- cannot create a second setting that shadows the first.
    CONSTRAINT setting_key_format CHECK (setting_key ~ '^[a-z0-9]+(\.[a-z0-9-]+)+$')
);

COMMENT ON COLUMN setting.secret IS
    'True for credentials. Redacted in API responses and never written to the audit log.';

-- Seeded defaults. Only settings with a genuinely safe default belong here: SMTP has none,
-- so it is absent until real credentials are supplied rather than seeded with a placeholder
-- that could be mistaken for configuration.
INSERT INTO setting (setting_key, value, description) VALUES
    ('ledger.rounding.threshold',
     '0.03',
     'Residual differences at or below this absolute amount post to the Rounding account '
     'automatically; anything larger is flagged for review (brief §6).'),
    ('ledger.rounding.mode',
     'HALF_UP',
     'java.math.RoundingMode used when a monetary value must be rounded. HALF_UP matches '
     'the convention used on Greek invoices.'),
    ('cash.payment.limit',
     '500.00',
     'Cash payments at or above this amount are hard-blocked, per the Greek legal cash '
     'limit enforced under N. 5301/2026 (brief §6).');
