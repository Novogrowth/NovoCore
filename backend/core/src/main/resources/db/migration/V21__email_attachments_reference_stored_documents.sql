-- Step 11, revisited: an emailed file that is already a stored document is referenced, not copied.
--
-- V20 gave email_outbox_attachment its own `content bytea NOT NULL`, and EmailAttachment's javadoc
-- said so deliberately: a caller wanting to email a stored document should download it and wrap
-- the bytes. That reasoning was half right. It is correct for a Purchase Order PDF generated at
-- approval time and for a monthly report — those exist nowhere else, and there is nothing for
-- them to reference. It is wrong for a document that is ALSO attached to a core record, which is
-- most of what this service will send once Reports and the Accountant Monthly Package exist: the
-- same file then sits in `attachment` and in `email_outbox_attachment`, in every backup, forever.
--
-- The same principle already applied to EmailSender itself — one door, no duplicate
-- implementations — applies to the bytes behind it. AttachmentService owns stored documents; the
-- outbox is not a second store.
--
-- WHAT THIS DOES NOT CHANGE
--
-- Nothing about the mail a recipient receives. SMTP transmits real bytes either way; the
-- dispatcher resolves a reference into content at send time. This is only about what NovoCore
-- keeps for itself.
--
-- THE FOUR STATES OF A ROW
--
--   INLINE,     content NOT NULL   the file exists only here (generated PDF, report)
--   INLINE,     content NULL       that copy has been pruned by a retention policy
--   ATTACHMENT, attachment_id set  the document is in `attachment`, referenced once
--   ATTACHMENT, attachment_id NULL that document has since been deleted
--
-- The last two rows of that table are the same outcome by two routes, and both are ORDINARY. A
-- sent-email record whose file is gone still names it, still states its size and checksum, and
-- says which of the two happened. The alternative — refusing to delete a document because a mail
-- from 2026 mentions it — would let email history pin every document forever, which is worse than
-- a history entry that reads "no longer available".
--
-- RETENTION (Q43), which this change splits in two
--
-- After this, the outbox's unbounded growth is exactly the INLINE bytes and nothing else. Rows
-- are cheap and worth keeping for years; inline copies of files nobody meant to keep are not.
-- Those want different numbers, and the second is now expressible without touching this schema
-- again: `UPDATE email_outbox_attachment SET content = NULL WHERE content_source = 'INLINE' AND
-- ...` leaves precisely the second state above, which the history already renders gracefully.
-- Nothing prunes anything today — the policy is Q43's to set, alongside step 12's backups.

ALTER TABLE email_outbox_attachment
    -- Recorded rather than inferred from which columns are populated: once the bytes are gone,
    -- both shapes are a row with nothing in it, and the history could not otherwise say which of
    -- the two things happened. Same reasoning as step 9 storing vat_class_source.
    ADD COLUMN content_source  varchar(20) NOT NULL DEFAULT 'INLINE',

    -- ON DELETE SET NULL, deliberately, and it is the whole deletion policy in one clause.
    -- CASCADE would delete the outbox's record that the message ever had an attachment, which is
    -- history being rewritten by an unrelated action. RESTRICT would make an old email block the
    -- deletion of a document. SET NULL keeps the entry, loses only the pointer, and leaves the
    -- snapshot below as what remains knowable.
    ADD COLUMN attachment_id   bigint REFERENCES attachment (id) ON DELETE SET NULL,

    -- Snapshotted from the document at queue time. Not decoration: with the row gone, this plus
    -- filename and size_bytes is what still identifies which file was sent, and it matches the
    -- checksum AttachmentServiceImpl.delete writes into the audit log — so a deletion recorded
    -- there and a history entry here can still be tied together after the bytes no longer exist.
    ADD COLUMN checksum_sha256 varchar(64);

-- Every row that exists today predates references and is therefore inline. The default did that
-- backfill; new rows must state which shape they are rather than inheriting one.
ALTER TABLE email_outbox_attachment ALTER COLUMN content_source DROP DEFAULT;

-- The point of the exercise: a referenced attachment stores no bytes.
ALTER TABLE email_outbox_attachment ALTER COLUMN content DROP NOT NULL;

ALTER TABLE email_outbox_attachment
    ADD CONSTRAINT email_attachment_source_known
        CHECK (content_source IN ('INLINE', 'ATTACHMENT')),

    -- The two shapes cannot bleed into each other: bytes only ever belong to an inline
    -- attachment, and a reference only ever to a referenced one. Together these are what make
    -- "stored once" a property of the data rather than of the Java that writes it.
    ADD CONSTRAINT email_attachment_bytes_only_when_inline
        CHECK (content IS NULL OR content_source = 'INLINE'),
    ADD CONSTRAINT email_attachment_reference_only_when_referenced
        CHECK (attachment_id IS NULL OR content_source = 'ATTACHMENT'),

    -- A referenced attachment always states which file it was, so the deleted case stays
    -- answerable. An inline one has no stored checksum to copy, and computing one here would be
    -- a fact about bytes that are about to be prunable anyway.
    ADD CONSTRAINT email_attachment_reference_states_its_checksum
        CHECK (content_source <> 'ATTACHMENT' OR checksum_sha256 IS NOT NULL),
    ADD CONSTRAINT email_attachment_inline_states_no_checksum
        CHECK (content_source <> 'INLINE' OR checksum_sha256 IS NULL),
    ADD CONSTRAINT email_attachment_checksum_is_hex
        CHECK (checksum_sha256 IS NULL OR checksum_sha256 ~ '^[0-9a-f]{64}$');

COMMENT ON COLUMN email_outbox_attachment.content IS
    'Bytes, for an attachment that exists nowhere else. NULL for a referenced document, and also '
    'NULL once an inline copy has been pruned — content_source tells the two apart.';

COMMENT ON COLUMN email_outbox_attachment.attachment_id IS
    'The AttachmentService document this message sent. NULL once that document is deleted, which '
    'leaves the history entry naming the file and reporting it as no longer available.';

-- Without this, deleting one document seq-scans the whole attachment table's worth of outbox
-- rows to apply SET NULL. Partial, because inline rows are never the target of that scan.
CREATE INDEX email_outbox_attachment_document_idx
    ON email_outbox_attachment (attachment_id)
    WHERE attachment_id IS NOT NULL;
