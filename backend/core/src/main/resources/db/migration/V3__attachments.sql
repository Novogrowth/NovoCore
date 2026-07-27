-- Document attachments on any core record: supplier invoice PDFs, delivery notes, and the
-- myDATA case brief §6 calls out, where an invoice arrives with totals but no line detail and
-- the actual document has to be kept.
--
-- CLAUDE.md names attachments a shared core service — one mechanism, not one per module.
--
-- Bytes live in the database rather than on a volume. The deciding factor is backup integrity:
-- a single pg_dump then captures records and documents together, and a restore cannot leave
-- rows pointing at files that are not there. Brief §13 lists backup restore as untested, so
-- reducing a restore to one artefact is worth real money later. The cost is accepted knowingly:
-- dumps grow with every document, so this is right for invoice-sized PDFs and would be wrong
-- for bulk high-resolution scans. The max size below is what keeps that from creeping.
--
-- bytea, not a large object (OID). Large objects live outside the table, need explicit
-- unlinking to avoid orphans, and are awkward with JPA; bytea is a plain column that pg_dump
-- handles without special cases.

CREATE TABLE attachment (
    id               bigserial    PRIMARY KEY,

    -- Polymorphic by (type, id), the same shape as audit_log, so a new core entity becomes
    -- attachable without a schema change or a column per entity type.
    entity_type      varchar(80)  NOT NULL,
    entity_id        varchar(120) NOT NULL,

    -- Already stripped of any directory component by the service before it gets here.
    filename         varchar(255) NOT NULL,
    content_type     varchar(150) NOT NULL,
    size_bytes       bigint       NOT NULL,
    -- Lets a restore be checked against the original, and identical re-uploads be spotted.
    -- varchar, not char(64): CHAR blank-pads its values and ignores trailing spaces when
    -- comparing, which is wrong for a digest, and PostgreSQL reports it as bpchar which does not
    -- match a String mapping. The CHECK below is what actually fixes the length.
    checksum_sha256  varchar(64)  NOT NULL,
    content          bytea        NOT NULL,

    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       varchar(100) NOT NULL DEFAULT 'system',
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT attachment_filename_not_blank    CHECK (btrim(filename) <> ''),
    CONSTRAINT attachment_entity_type_not_blank CHECK (btrim(entity_type) <> ''),
    CONSTRAINT attachment_entity_id_not_blank   CHECK (btrim(entity_id) <> ''),
    CONSTRAINT attachment_size_positive         CHECK (size_bytes > 0),
    -- Belt and braces against a filename that escapes its record on download.
    CONSTRAINT attachment_filename_no_path      CHECK (filename !~ '[/\\]'),
    CONSTRAINT attachment_checksum_is_hex       CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$')
);

COMMENT ON TABLE attachment IS
    'Documents attached to core records. Content is stored in-database so that backup and '
    'restore stay a single atomic artefact.';

CREATE INDEX attachment_entity_idx ON attachment (entity_type, entity_id, created_at DESC);

-- Guards the trade-off above: without a ceiling, one person attaching a folder of 200 MB scans
-- quietly turns every nightly backup into something that no longer finishes.
INSERT INTO setting (setting_key, value, description) VALUES
    ('attachment.max-size-bytes',
     '26214400',
     'Largest single attachment accepted, in bytes (default 25 MiB). Content is stored in the '
     'database, so this is what keeps backup size predictable.');
