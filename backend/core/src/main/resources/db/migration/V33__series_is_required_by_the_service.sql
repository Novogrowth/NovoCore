-- =========================================================================================
-- V33 — THE SERIES IS REQUIRED BY THE SERVICE, NOT BY THE COLUMN (R1b)
-- =========================================================================================
--
-- ⚠️ COMMENT ONLY. No column changes, no data changes, no constraints. R1b's behaviour lives
-- entirely in SalesInvoiceServiceImpl; this migration exists so that the reason is readable
-- from `psql` and not only from Java, because the column's V32 comment is now out of date in a
-- way that would mislead.
--
-- V32 said: "Nullable through R1a because every existing invoice predates series; R1b makes it
-- the source of the invoice's channel." That reads as though R1b would make it NOT NULL. It
-- deliberately does not, and the reason is worth having at the column.
--
-- -----------------------------------------------------------------------------------------
-- WHY THE COLUMN STAYS NULLABLE WHILE THE SERVICE REFUSES A REQUEST WITHOUT ONE
-- -----------------------------------------------------------------------------------------
-- This is a deliberate departure from the principle V31 states for
-- sales_document_type_active_has_stock_behaviour — that a constraint the database holds cannot
-- be bypassed by a second write path. Making series_id NOT NULL would require backfilling every
-- invoice recorded before R1b with a series, and there is no series to give them: the series
-- table shipped EMPTY precisely so that the owner authors it, and inventing one to satisfy a
-- constraint is the same fabrication that rule exists to prevent.
--
-- ⚠️ WHETHER MIGRATED HISTORY CARRIES A SERIES IS STEP 24'S QUESTION. M0b imports a real year;
-- what those documents' series are — whether Go's series map onto the owner's, and what to do
-- with documents that predate a series entirely — is that step's decision and is deliberately
-- not pre-empted here. If step 24 answers it, tightening this column becomes possible then.
--
-- Until then: NewSalesInvoice.seriesId is @Mandatory and guarded with Required.field, so no
-- caller can omit it; the column permits a null only for rows that already exist.
--
-- -----------------------------------------------------------------------------------------
-- WHAT THE SERIES NOW DECIDES, SO THE COLUMN IS NOT MERELY A REFERENCE
-- -----------------------------------------------------------------------------------------
--   * CHANNEL. sales_invoice.channel is written from sales_document_series.channel and is NOT
--     independently settable. ⚠️ sales_invoice.channel stays NOT NULL and is NOT relaxed: a
--     series whose channel is null is not a sales channel (self-supply — the customer is the
--     issuer), and recording against one is REFUSED. Self-supply has no posting rule yet
--     (blocked on the accountant, and the revenue leg has no candidate account), so the
--     constraint is what holds that question open instead of papering over it. R3 resolves both.
--   * THE DOCUMENT TYPE, through sales_document_series.document_type_id, which is NOT NULL.
--     ⚠️ There is deliberately NO document_type_id column on sales_invoice: two independently
--     settable references could disagree about which type a document is, which is the same
--     defect the channel rule above exists to prevent. sales_document_type.affects_stock is
--     read from there, and decides whether recording the document consumes stock at all.

COMMENT ON COLUMN sales_invoice.series_id IS
    'The numbering series, and since R1b the source of this invoice''s channel and document '
    'type. ⚠️ NULLABLE DELIBERATELY: the service refuses a request without one, but rows '
    'recorded before R1b have no series and backfilling would invent one nobody authored. '
    'Whether migrated history carries a series is step 24''s question. The document type is '
    'reached THROUGH this reference — there is no document_type_id column, so the two cannot '
    'disagree.';

COMMENT ON COLUMN sales_invoice.channel IS
    'Which of the three channel Sales accounts this invoice credits. ⚠️ Since R1b this is '
    'DERIVED from sales_document_series.channel and is not independently settable — ΑΛΠW is '
    'the web series, so an invoice in it is a web sale by definition. NOT NULL, and not to be '
    'relaxed: a channel-less series is refused rather than accommodated, because self-supply '
    'has no posting rule yet and the constraint is what holds that open (R3).';
