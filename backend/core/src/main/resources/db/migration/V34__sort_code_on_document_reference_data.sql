-- =========================================================================================
-- V34 — A SORT CODE ON THE FOUR DOCUMENT REFERENCE TABLES (R2b §3)
-- =========================================================================================
--
-- ⚠️ ITS PURPOSE IS ORDERING. IT IS NOT AN IDENTIFIER, AND THE NAME SAYS SO.
--
-- The owner's words: "I need the codes for sorting purposes. When an employee is about to issue
-- a sales document, they need to see a list that makes sense." That is the whole requirement.
-- The column is called sort_code and not code precisely because a field called `code` on a
-- document type attracts identifier rules within a year — uniqueness-as-identity, immutability,
-- printing it on documents, matching against it from an adapter. None of those apply here.
--
-- What follows from "it is an ordering key, not an identity":
--
--   * FREELY EDITABLE. Reordering a list is a normal act, not a correction. This is deliberately
--     NOT the editable-while-unused freeze that R2 put on series.abbreviation — an abbreviation
--     appears on a document and a sort code appears on nothing.
--   * CARRIES NO LEGAL MEANING and is transmitted nowhere. Not a ΜΑΡΚ, not an AADE code, not a
--     document number.
--   * NEVER DERIVED FROM PROSVASIS GO. Go's type and series numbers are Go's internal ids and
--     belong in an adapter mapping table under architecture rule 2. The owner assigns these.
--
-- -----------------------------------------------------------------------------------------
-- ⚠️ WHY integer AND NOT text
-- -----------------------------------------------------------------------------------------
-- A text sort is lexical: '1000' orders before '900', and '10' before '9'. For a column whose
-- entire purpose is ordering that is not a quirk, it is the column failing at its job.
--
-- The owner's own series scheme is numeric with deliberate gaps — 1xxx no channel, 2xxx store
-- and phone, 3xxx self-supply, 4xxx web, 5xxx Skroutz — and gaps are the point: 1500 can be
-- inserted between 1000 and 2000 without renumbering anything. An integer supports that
-- directly. (His document-type numbers are not an ordered scheme at all — 7092 precedes 7063 in
-- his own list — which is exactly why he is assigning new ones here rather than importing them.)
--
-- -----------------------------------------------------------------------------------------
-- ⚠️ WHY NOT NULL AND BACKFILLED, WHERE sales_invoice.series_id IS NULLABLE
-- -----------------------------------------------------------------------------------------
-- V33 left series_id nullable and had the service require it, deliberately departing from
-- "a constraint the database holds cannot be bypassed". THAT ARGUMENT DOES NOT TRANSFER HERE,
-- and the difference is worth stating because the two look alike.
--
-- series_id stayed nullable because backfilling it would INVENT A SERIES NOBODY AUTHORED, and a
-- wrong series is a false statement about a legal document — it changes what the document claims
-- to be. A sort code has no truth value. Sort order is arbitrary until somebody chooses it, so an
-- initial backfill fabricates nothing: it states no fact, and the owner reorders freely the
-- moment he wants to.
--
-- So this column is NOT NULL from the start. That buys three things:
--   * no NULLS FIRST/LAST decision to carry forever in every ORDER BY;
--   * no service-requires-but-column-permits divergence to explain (A.7);
--   * every row is orderable, which is the only reason the column exists.
--
-- The backfill is `id * 10` — ten-spaced so the owner can insert between existing rows without
-- renumbering, and derived from insertion order because that is the only order that exists
-- before he chooses one.
--
-- -----------------------------------------------------------------------------------------
-- UNIQUENESS
-- -----------------------------------------------------------------------------------------
-- Unique within each table, so the ordering is deterministic. Two rows sharing a sort code would
-- order by whatever the plan happened to produce, which is the one thing a sort key must not do.
-- Not unique ACROSS tables: a sales type and a purchase type are different lists.
-- =========================================================================================

ALTER TABLE sales_document_type     ADD COLUMN sort_code integer;
ALTER TABLE purchase_document_type  ADD COLUMN sort_code integer;
ALTER TABLE sales_document_series   ADD COLUMN sort_code integer;
ALTER TABLE purchase_document_series ADD COLUMN sort_code integer;

-- Insertion order is the only order that exists before the owner chooses one. Ten-spaced.
UPDATE sales_document_type      SET sort_code = id * 10 WHERE sort_code IS NULL;
UPDATE purchase_document_type   SET sort_code = id * 10 WHERE sort_code IS NULL;
UPDATE sales_document_series    SET sort_code = id * 10 WHERE sort_code IS NULL;
UPDATE purchase_document_series SET sort_code = id * 10 WHERE sort_code IS NULL;

ALTER TABLE sales_document_type      ALTER COLUMN sort_code SET NOT NULL;
ALTER TABLE purchase_document_type   ALTER COLUMN sort_code SET NOT NULL;
ALTER TABLE sales_document_series    ALTER COLUMN sort_code SET NOT NULL;
ALTER TABLE purchase_document_series ALTER COLUMN sort_code SET NOT NULL;

ALTER TABLE sales_document_type
    ADD CONSTRAINT sales_document_type_sort_code_unique UNIQUE (sort_code);
ALTER TABLE purchase_document_type
    ADD CONSTRAINT purchase_document_type_sort_code_unique UNIQUE (sort_code);
ALTER TABLE sales_document_series
    ADD CONSTRAINT sales_document_series_sort_code_unique UNIQUE (sort_code);
ALTER TABLE purchase_document_series
    ADD CONSTRAINT purchase_document_series_sort_code_unique UNIQUE (sort_code);

COMMENT ON COLUMN sales_document_type.sort_code IS
    'Ordering only, assigned by the business. Freely editable, no legal meaning, never derived '
    'from Prosvasis Go. Integer because a text sort puts 1000 before 900. See V34.';
COMMENT ON COLUMN purchase_document_type.sort_code IS
    'Ordering only, assigned by the business. See V34.';
COMMENT ON COLUMN sales_document_series.sort_code IS
    'Ordering only. ⚠️ The series picker an employee uses when recording a sale orders by this — '
    'abbreviation sorts by Greek alphabet, which puts ΑΕΛ before ΑΛΠ and scatters the channel '
    'variants of one document type. See V34.';
COMMENT ON COLUMN purchase_document_series.sort_code IS
    'Ordering only. See V34.';
