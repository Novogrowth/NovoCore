-- ===========================================================================================
-- F5 — SEARCH ON THE TWO SALES DOCUMENT LISTS (target list row 8)
-- ===========================================================================================
--
-- Row 8 of the search target list in PROGRESS.md reads:
--
--     Sales Invoices / Credit Notes — Document number, Customer name, Customer VAT,
--     Customer Code, Date, Document type-series
--
-- Four of those are reachable now and the indexes below are what makes each of them a lookup
-- rather than a sequential scan. `Date` is already a filter (`from`/`to`) rather than a search
-- field, and `Customer Code` DOES NOT EXIST — `customer.code` is still not a column, and is D1's
-- to add. That gap is recorded rather than worked around; see PROGRESS.md.
--
-- ⚠️ WHY THE COUNTERPARTY COLUMNS ARE NOT LISTED HERE. Customer name and VAT number are searched
-- by these lists, through a subquery on `customer_id` (TextSearch.matchingRelated). Their trigram
-- indexes already exist — V28 built them for the Customers screen — and the subquery uses those.
-- Indexing them again here would be a second index on one expression, which costs writes and buys
-- nothing.
--
-- ⚠️ AND WHY A SUBQUERY RATHER THAN A JOIN, because a reader will wonder. `sales_invoice` holds
-- `customer_id` and `series_id` as plain bigints, not as mapped associations — every
-- cross-aggregate reference in this core does. A dotted JPA path would need an association it
-- does not have, and would produce an INNER JOIN, which would silently drop every invoice whose
-- `series_id` is null. That is every invoice recorded before R1b. The full argument is at
-- TextSearch.matchingRelated.


-- The document numbers themselves. `upper(document_number)` is already indexed for uniqueness,
-- but that is a btree on an exact expression and cannot serve a substring match.
CREATE INDEX sales_invoice_document_number_search
    ON sales_invoice USING gin (novocore_searchable(document_number) gin_trgm_ops);

CREATE INDEX credit_note_document_number_search
    ON credit_note USING gin (novocore_searchable(document_number) gin_trgm_ops);


-- The series, which is what "document type-series" means on a sales document: ΑΛΠW identifies the
-- series and, through it, the type. Both of its human-readable columns are searched, because an
-- operator may type either the short form they see on the document or the words they remember.
--
-- 📌 A note on proportion, so nobody removes these thinking they are free to: `sales_document_series`
-- holds tens of rows, so the subquery would be fast without them. They exist because
-- TextSearchIT asserts that every searched column is indexed, and that assertion is worth more
-- than the two indexes cost — it is what stops a searched column on a LARGE table shipping
-- unindexed, which fails quietly and only on production-sized data.
CREATE INDEX sales_document_series_abbreviation_search
    ON sales_document_series USING gin (novocore_searchable(abbreviation) gin_trgm_ops);

CREATE INDEX sales_document_series_description_search
    ON sales_document_series USING gin (novocore_searchable(description) gin_trgm_ops);


COMMENT ON INDEX sales_invoice_document_number_search IS
    'Substring search on the document number (F5, target list row 8). Distinct from '
    'sales_invoice_number_idx, which is a UNIQUE btree enforcing per-series uniqueness on the '
    'exact upper-cased value and cannot answer a contains-query.';
