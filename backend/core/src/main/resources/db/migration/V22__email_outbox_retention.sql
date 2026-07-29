-- Q43, answered: the outbox keeps its rows forever and its generated attachments for 90 days.
--
-- One question until V21, two afterwards. Before references, "how long do we keep sent emails?"
-- covered cheap metadata and expensive duplicated bytes together and could not have one right
-- answer. Now that an emailed document is referenced rather than copied, the outbox's growth is
-- exactly the inline attachments — files that exist nowhere else and were never meant to be
-- permanent — so the two halves take different numbers.
--
--   rows              FOREVER   a sent-email history is a business record, and it is small
--   inline bytes      90 days   generated Purchase Order PDFs and reports
--
-- Both live here rather than in application.yml because they are operational policy somebody may
-- want to change without a redeploy, which is the same argument that put SMTP in Settings.
--
-- WHAT IS DELIBERATELY NOT PRUNED
--
-- * Referenced attachments. Their bytes belong to AttachmentService, which has its own lifecycle;
--   the outbox holds a pointer. Pruning here would be one service deleting another's documents.
--   EmailRetention's statement is restricted to content_source = 'INLINE' for that reason, and a
--   test proves a referenced attachment survives a prune that removes an inline one beside it.
--
-- * Anything not SENT. A PENDING message still needs its bytes — a system waiting on a broken SMTP
--   password for months must not have its attachments removed from under it and then fail. A
--   FAILED message keeps them because retrying it is the entire reason it is kept, and a retry
--   that cannot re-send the attachment is not a retry.
--
-- FOREVER is spelled out rather than encoded as blank or 0. A blank setting is indistinguishable
-- from one somebody deleted by accident, and this codebase does not read silence as intent.

INSERT INTO setting (setting_key, value, description) VALUES
    ('email.retention.message-days',
     'FOREVER',
     'How long a sent message''s row is kept: FOREVER, or a number of days. Rows are cheap since '
     'V21 — an emailed document is referenced, not copied — so a year of history is rows, not '
     'megabytes. Pruning a row removes its attachment rows with it (ON DELETE CASCADE).'),
    ('email.retention.inline-attachment-days',
     '90',
     'How long the inline copy of a generated attachment (Purchase Order PDF, report) is kept, in '
     'days after the message was sent, before its bytes are dropped. The history entry keeps the '
     'filename, size and reason. FOREVER disables it. Referenced documents are never pruned here: '
     'their bytes belong to AttachmentService.');
