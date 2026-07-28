-- Step 11: the shared email service, and the outbox that makes it asynchronous.
--
-- CLAUDE.md names email sending a shared core service: configured once via Settings, exposed
-- through one interface, called by every feature that needs it. Rule 4 then decides the shape:
-- a core operation must never synchronously wait on an outbound call, so EmailSender.send
-- writes a row here and returns, and a scheduled dispatcher does the SMTP conversation.
--
-- WHY A TABLE RATHER THAN AN IN-MEMORY QUEUE
--
-- Because the row is written in the caller's own transaction, a message is queued if and only
-- if the operation that queued it committed. An approved Purchase Order always sends its PDF; a
-- rolled-back one never does, with no compensating logic anywhere. An in-memory queue gets both
-- of those wrong in opposite directions — it sends for work that was rolled back, and loses
-- everything queued when the process restarts.
--
-- It also means a failure is a row somebody can look at hours later, rather than a log line. A
-- message that could not be delivered is left FAILED and queryable, per CLAUDE.md rule 8's
-- fail-loudly stance; nothing is ever dropped.

CREATE TABLE email_outbox (
    id                  bigserial    PRIMARY KEY,

    -- Recipients, one address per array element. A PostgreSQL text[] rather than a child table:
    -- these are never queried individually, never joined to, and are meaningless apart from the
    -- message. cc/bcc default to empty rather than NULL so a reader never has to distinguish
    -- "no recipients" from "not set".
    to_addresses        text[]       NOT NULL,
    cc_addresses        text[]       NOT NULL DEFAULT '{}',
    bcc_addresses       text[]       NOT NULL DEFAULT '{}',

    subject             varchar(500) NOT NULL,
    body                text         NOT NULL,
    body_format         varchar(20)  NOT NULL,

    status              varchar(20)  NOT NULL,
    attempts            integer      NOT NULL DEFAULT 0,
    -- Copied onto the row at queue time rather than read from Settings at dispatch time, so
    -- lowering the limit cannot retroactively fail messages already waiting under the old one.
    max_attempts        integer      NOT NULL,

    -- When this message next becomes due. NULL once it is settled, in either direction.
    next_attempt_at     timestamptz,
    last_attempt_at     timestamptz,
    sent_at             timestamptz,
    last_error          text,

    -- What the message actually went out as, captured at attempt time. Settings say what the
    -- sender is now; this says what this message used, which is the question being asked when
    -- somebody is working out why a reply went to the wrong place.
    sent_from           varchar(320),
    reply_to            varchar(320),

    created_at          timestamptz  NOT NULL DEFAULT now(),
    created_by          varchar(100) NOT NULL DEFAULT 'system',
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    updated_by          varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT email_outbox_status_known
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT email_outbox_body_format_known
        CHECK (body_format IN ('PLAIN_TEXT', 'HTML')),

    CONSTRAINT email_outbox_has_a_recipient
        CHECK (cardinality(to_addresses) > 0),
    -- Belt and braces against header injection. EmailMessage refuses a line break in an address
    -- or a subject before it ever gets here; this refuses one written by a psql session too,
    -- which is the same stance the ledger takes on debits equalling credits.
    CONSTRAINT email_outbox_no_header_injection
        CHECK (subject !~ '[\r\n]'
               AND array_to_string(to_addresses  || cc_addresses || bcc_addresses, ',') !~ '[\r\n]'),

    CONSTRAINT email_outbox_subject_not_blank CHECK (btrim(subject) <> ''),
    CONSTRAINT email_outbox_attempts_not_negative CHECK (attempts >= 0),
    CONSTRAINT email_outbox_max_attempts_positive CHECK (max_attempts >= 1),
    CONSTRAINT email_outbox_attempts_within_limit CHECK (attempts <= max_attempts),

    -- The three statuses each pin down what the timestamps may say, so an impossible row —
    -- sent but still due, or pending with a sent_at — cannot be stored at all.
    CONSTRAINT email_outbox_status_matches_timestamps CHECK (
        (status = 'PENDING' AND next_attempt_at IS NOT NULL AND sent_at IS NULL)
     OR (status = 'SENT'    AND next_attempt_at IS NULL     AND sent_at IS NOT NULL)
     OR (status = 'FAILED'  AND next_attempt_at IS NULL     AND sent_at IS NULL)
    ),
    -- A message that has been attempted has a record of when, and one that has not been
    -- attempted cannot have a sender recorded against it.
    CONSTRAINT email_outbox_attempted_iff_last_attempt CHECK (
        (attempts = 0) = (last_attempt_at IS NULL)
    ),
    CONSTRAINT email_outbox_sender_only_once_attempted CHECK (
        attempts > 0 OR (sent_from IS NULL AND reply_to IS NULL)
    ),
    -- A failure always says why. A FAILED row with no error is the exact thing this table
    -- exists to prevent: a silent drop wearing a status.
    CONSTRAINT email_outbox_failure_states_a_reason CHECK (
        status <> 'FAILED' OR (last_error IS NOT NULL AND btrim(last_error) <> '')
    )
);

COMMENT ON TABLE email_outbox IS
    'Outgoing messages. Written in the caller''s transaction so a message is queued if and only '
    'if the operation that queued it committed; sent by a scheduled dispatcher so no core '
    'operation waits on SMTP (CLAUDE.md rule 4).';

COMMENT ON COLUMN email_outbox.max_attempts IS
    'Copied from settings at queue time, so changing the limit does not retroactively fail '
    'messages already waiting.';

-- The dispatcher's claim query: PENDING rows that are due, oldest first. Partial, because the
-- rows it must never look at — SENT ones — are the ones that accumulate forever.
CREATE INDEX email_outbox_due_idx
    ON email_outbox (next_attempt_at, id)
    WHERE status = 'PENDING';

-- What a human looks at: the failures.
CREATE INDEX email_outbox_failed_idx
    ON email_outbox (created_at DESC)
    WHERE status = 'FAILED';

-- Attachments live in their own table rather than in a column on the message, because a message
-- has zero, one or several and their bytes are large enough that loading them alongside every
-- status query would be wasteful. bytea and in-database, for the reason V3 gives for document
-- attachments: a backup stays one atomic artefact.
CREATE TABLE email_outbox_attachment (
    id               bigserial    PRIMARY KEY,
    email_outbox_id  bigint       NOT NULL
        REFERENCES email_outbox (id) ON DELETE CASCADE,

    -- Written into a MIME header and then used to name a file on the recipient's disk, so it is
    -- stripped of any directory component before it gets here.
    filename         varchar(255) NOT NULL,
    content_type     varchar(150) NOT NULL,
    size_bytes       bigint       NOT NULL,
    content          bytea        NOT NULL,
    -- Position in the message, so several attachments arrive in the order the caller listed
    -- them rather than in whatever order the rows come back. Named like step 9's
    -- allocation_order rather than a bare "position", which is a PostgreSQL function name.
    attachment_order integer      NOT NULL,

    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       varchar(100) NOT NULL DEFAULT 'system',
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT email_attachment_filename_not_blank CHECK (btrim(filename) <> ''),
    CONSTRAINT email_attachment_filename_no_path   CHECK (filename !~ '[/\\]'),
    CONSTRAINT email_attachment_filename_no_breaks CHECK (filename !~ '[\r\n]'),
    CONSTRAINT email_attachment_size_positive      CHECK (size_bytes > 0),
    CONSTRAINT email_attachment_order_not_negative CHECK (attachment_order >= 0),
    CONSTRAINT email_attachment_order_unique       UNIQUE (email_outbox_id, attachment_order)
);

CREATE INDEX email_outbox_attachment_message_idx
    ON email_outbox_attachment (email_outbox_id, attachment_order);

-- ON DELETE CASCADE above is the one place a cascade is right here: an attachment has no
-- meaning apart from its message, and nothing else references it. Note that nothing deletes an
-- outbox row today — sent messages are retained. If a retention policy is ever added, it is the
-- messages that get pruned and the attachments follow.

-- ---------------------------------------------------------------------------------------------
-- Configuration
--
-- Novotrade's own mail server. Seeded rather than left to be typed, because these are decided
-- facts and a system that ships unable to send email is one where the first person to need it
-- discovers six settings are missing.
--
-- THE PASSWORD IS DELIBERATELY NOT HERE. A migration is a file in git and a credential in git
-- is in git permanently, readable by anyone who ever clones the repository and present in every
-- CI checkout. It is written into this same settings table once from NOVOCORE_SMTP_PASSWORD, the
-- same route NOVOCORE_BOOTSTRAP_OWNER_PASSWORD takes in step 4, and the variable can be removed
-- afterwards. The decision that the password *lives* in Settings rather than in the environment
-- is unaffected: the environment is how it arrives, not where it is kept.
-- ---------------------------------------------------------------------------------------------

INSERT INTO setting (setting_key, value, description) VALUES
    ('smtp.host',
     'mail.novotrade.gr',
     'SMTP server for outgoing mail.'),
    ('smtp.port',
     '465',
     'SMTP port. 465 is implicit TLS; see smtp.transport-security, which must agree with it.'),
    ('smtp.username',
     'erp@novotrade.gr',
     'SMTP account used to authenticate. Normally the same as smtp.from-address.'),
    ('smtp.transport-security',
     'IMPLICIT_TLS',
     'IMPLICIT_TLS (port 465, TLS from the first byte), STARTTLS (port 587, upgraded and '
     'required), or NONE. A boolean start-tls flag cannot distinguish the first two, and '
     'pointing a STARTTLS client at 465 hangs rather than failing.'),
    ('smtp.from-address',
     'erp@novotrade.gr',
     'Address every outgoing message is sent from. This mailbox is UNMONITORED and used only '
     'for sending, which is why smtp.reply-to exists and is required.'),
    ('smtp.from-name',
     'Java Jives',
     'Display name shown beside the sender address.'),
    ('smtp.reply-to',
     'kostas@novotrade.gr',
     'Reply-To header set on EVERY outgoing message, so replies reach a mailbox somebody reads '
     'rather than the unmonitored sending address. Required, not optional: a missing value here '
     'would route every reply into a black hole with no visible symptom.'),
    ('email.max-attempts',
     '5',
     'Attempts before a message is marked FAILED and left for a human. With the backoff below '
     'that spans roughly eight minutes, which covers a mail server restart without leaving a '
     'genuinely undeliverable message retrying all day.'),
    ('email.retry.backoff-seconds',
     '30',
     'Delay before the second attempt, in seconds. Each further attempt doubles it.'),
    ('email.retry.backoff-max-seconds',
     '900',
     'Ceiling on the exponential backoff, in seconds (15 minutes).'),
    ('email.dispatch.batch-size',
     '20',
     'Messages claimed per dispatch cycle. Bounds how long one cycle can run when a backlog has '
     'built up behind an outage.');
