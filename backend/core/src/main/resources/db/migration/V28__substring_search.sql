-- ---------------------------------------------------------------------------------------
-- V28 — substring search: pg_trgm, unaccent, and one normalisation function
-- ---------------------------------------------------------------------------------------
--
-- Until now every text lookup on this schema was an EXACT match. `GET /api/products?sku=`
-- finds a product only when the operator types the whole SKU; typing `TEST` against eight
-- `TEST-PRODUCT-*` rows matched nothing at all, which is what the frontend roadmap recorded
-- and declined to work around. This migration is the storage half of the answer: match
-- anywhere in the string, ignoring case and accents.
--
-- ---------------------------------------------------------------------------------------
-- WHY BOTH EXTENSIONS, AND WHY THEY ARE SAFE TO CREATE HERE
-- ---------------------------------------------------------------------------------------
-- `pg_trgm` is what makes `LIKE '%cof%'` indexable. Without it a leading-wildcard LIKE cannot
-- use a B-tree at all — a B-tree can only seek on a known prefix — so every search would be
-- a sequential scan. With a GIN trigram index the planner extracts the trigrams from the
-- pattern and probes the index instead.
--
-- `unaccent` is what makes the match accent-insensitive. It is the only one of the two that
-- is a real judgement call, and the case for it is Greek: an operator typing `αγγελος` should
-- find `Άγγελος`, and one typing `Cafe` should find `Café`.
--
-- Both are marked TRUSTED as of PostgreSQL 13, so the database owner creates them without
-- superuser. Flyway runs as the owner on this stack; on a host where it does not, this
-- migration is the thing that fails, loudly, at startup — which is the correct place for that
-- to surface rather than as a query error months later.
--
-- ---------------------------------------------------------------------------------------
-- ⚠️ WHY A WRAPPER FUNCTION EXISTS AT ALL — unaccent(text) CANNOT BE INDEXED
-- ---------------------------------------------------------------------------------------
-- This is the trap, and it is worth stating precisely because the error it produces names the
-- symptom rather than the cause.
--
-- The one-argument `unaccent(text)` is declared STABLE, not IMMUTABLE: it looks its dictionary
-- up through the current search_path, so the same input could in principle give a different
-- answer. PostgreSQL refuses to build an index on a STABLE expression — an index whose entries
-- could silently stop matching their own rows is worse than no index — and answers
-- `functions in index expression must be marked IMMUTABLE`.
--
-- The two-argument form `unaccent(regdictionary, text)` names the dictionary explicitly and IS
-- IMMUTABLE. So the wrapper below pins the dictionary, does the rest of the normalisation, and
-- is itself declared IMMUTABLE. That is what the indexes are built on and what every query
-- calls. **Do not "simplify" it to the one-argument form** — the indexes stop being creatable
-- and, worse, an existing index would silently stop being used.
--
-- ---------------------------------------------------------------------------------------
-- WHAT NORMALISATION MEANS HERE — THREE STEPS, AND THE THIRD IS NOT OBVIOUS
-- ---------------------------------------------------------------------------------------
-- 1. unaccent                    — Άγγελος → Αγγελος, Café → Cafe.
-- 2. lower COLLATE pg_c_utf8     — case folding. See below: the collation is load-bearing.
-- 3. ς → σ                       — Greek FINAL sigma folded onto medial sigma.
--
-- Step 3 is the one that would be left out by anyone who has not hit it. Greek writes a
-- different letter for a word-final sigma, and `unaccent` does not touch it because it is not
-- an accent — it is a genuinely different character. So `Πελάτης` normalises to `πελατης` and
-- a search for `πελατησ` finds nothing, while `πελατη` finds it. The distinction is invisible
-- to anyone reading the results and depends on where the operator stopped typing.
--
-- Folding must come AFTER lower(), or it would miss the uppercase input it exists for.
--
-- ---------------------------------------------------------------------------------------
-- ⚠️⚠️ WHY `COLLATE pg_c_utf8` AND NOT A PLAIN lower() — THIS DATABASE IS LOCALE C
-- ---------------------------------------------------------------------------------------
-- This is the second trap, it is worse than the first because nothing errors, and it was
-- found by running the migration against the real stack rather than by reasoning.
--
-- `docker/compose.yml` initialises this database with `--encoding=UTF8 --locale=C`, on
-- purpose: it makes sort order deterministic across machines, so a report does not order
-- Greek names differently on someone else's laptop. But under the C locale `lower()` folds
-- ASCII AND NOTHING ELSE. Greek capitals pass through untouched:
--
--     lower('ΠΕΛΑΤΗΣ')  →  'ΠΕΛΑΤΗΣ'
--
-- So with a bare lower(), `Πελάτης Λιανικής` normalised to `Πελατησ Λιανικησ` — accents
-- stripped, sigma folded, and STILL CAPITALISED — and searching `πελατησ` found nothing. No
-- error, no warning; the index built happily and the query ran happily and returned no rows.
--
-- `PG_C_UTF8` is PostgreSQL 17's builtin collation provider. It does full Unicode case
-- mapping and is platform-independent — the same answer on Alpine, Debian, and a developer's
-- laptop, which plain libc case mapping is not. It is chosen over ICU (`und-x-icu`)
-- deliberately: ICU's behaviour is tied to the bundled ICU version, and an index expression
-- whose meaning changes when a library is upgraded is exactly what must not be indexed.
--
-- ⚠️ It is not eternal either. Unicode itself is updated in new PostgreSQL major versions, so
-- case mapping CAN change across a major upgrade — the same caveat the unaccent rules carry.
-- **REINDEX these fifteen indexes after a major version upgrade.** That is a known, bounded
-- maintenance step, and it is the price of the function being IMMUTABLE at all.
--
-- ⚠️ THE TEST SUITE COULD NOT SEE THIS UNTIL IT WAS MADE TO. Testcontainers took the image's
-- default locale (`en_US.utf8`), where a bare lower() folds Greek correctly, so every
-- integration test passed against a database configured unlike the real one.
-- `PostgresTestContainerConfiguration` now passes the same POSTGRES_INITDB_ARGS as
-- `compose.yml`. Keep those two in step; the divergence, not the collation, is the real defect
-- here.
--
-- ---------------------------------------------------------------------------------------
-- SEARCH_PATH IS PINNED ON THE FUNCTION, DELIBERATELY
-- ---------------------------------------------------------------------------------------
-- An IMMUTABLE function used in an index must not be able to resolve differently later. `SET
-- search_path` on the function body is what guarantees that: without it, a session that
-- prepends a schema containing its own `unaccent` would compute different values from the ones
-- already stored in the index, and the index would quietly return wrong rows.
--
-- ---------------------------------------------------------------------------------------
-- ONE INDEX PER COLUMN, NOT ONE PER TABLE OVER A CONCATENATION
-- ---------------------------------------------------------------------------------------
-- The compact alternative is a single index over `col_a || ' ' || col_b || ...`. It was
-- rejected: a query can then only ever search ALL of those columns at once, in exactly that
-- order, and the moment one screen wants to search a subset the index is useless and the
-- expression has to be rebuilt. Per-column indexes compose — the planner ORs the bitmaps —
-- and each one is independently droppable.
--
-- ADDING A NEW SEARCHABLE COLUMN LATER IS ONE LINE HERE (in a NEW migration, never this one)
-- plus one string at the call site. That is the whole cost, and it is the criterion the shared
-- `TextSearch` mechanism was built against.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- The name is deliberately not `normalise`/`normalize`: this is not general-purpose text
-- normalisation, it is specifically "what this system considers the same string for the
-- purpose of searching", and a name that promised more would be reached for by code that
-- wanted something else.
CREATE OR REPLACE FUNCTION novocore_searchable(value text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    STRICT
    PARALLEL SAFE
    SET search_path = pg_catalog, public
AS $$
    SELECT translate(
               lower(unaccent('public.unaccent'::regdictionary, value) COLLATE pg_c_utf8),
               'ς', 'σ')
$$;

COMMENT ON FUNCTION novocore_searchable(text) IS
    'Case- and accent-insensitive search key, with Greek final sigma folded onto medial sigma. '
    'IMMUTABLE and search_path-pinned because every trigram index below is built on it: see the '
    'V28 migration for why the one-argument unaccent() cannot be used here.';

-- ---------------------------------------------------------------------------------------
-- The indexes. Every column named here is one the shared TextSearch mechanism is pointed at
-- from a service; the two lists must agree, and SearchIndexIT asserts that they do.
-- ---------------------------------------------------------------------------------------

-- Product — SKU, title, barcode, and the supplier's own code for it.
CREATE INDEX product_sku_search ON product USING gin (novocore_searchable(sku) gin_trgm_ops);
CREATE INDEX product_name_search ON product USING gin (novocore_searchable(name) gin_trgm_ops);
CREATE INDEX product_ean_search ON product USING gin (novocore_searchable(ean) gin_trgm_ops);
CREATE INDEX product_supplier_sku_search
    ON product USING gin (novocore_searchable(supplier_sku) gin_trgm_ops);

-- Supplier. The brief's field list also names Code and Alias; neither is a column yet, and
-- the brief marks that whole list (draft). They are queued as their own item — a column
-- nobody can populate would make this index a lie about what search covers.
CREATE INDEX supplier_name_search ON supplier USING gin (novocore_searchable(name) gin_trgm_ops);
CREATE INDEX supplier_email_search ON supplier USING gin (novocore_searchable(email) gin_trgm_ops);
CREATE INDEX supplier_phone_search ON supplier USING gin (novocore_searchable(phone) gin_trgm_ops);

-- Customer. Same note about Code. The VAT number is searchable as a substring on purpose:
-- an operator reading a partial ΑΦΜ off a document is the case this is for, and it does NOT
-- replace the exact by-vat-number lookup, which is the authoritative auto-link (brief §5) and
-- must stay exact precisely because it is applied without asking.
CREATE INDEX customer_name_search ON customer USING gin (novocore_searchable(name) gin_trgm_ops);
CREATE INDEX customer_vat_number_search
    ON customer USING gin (novocore_searchable(vat_number) gin_trgm_ops);
CREATE INDEX customer_email_search ON customer USING gin (novocore_searchable(email) gin_trgm_ops);
CREATE INDEX customer_phone_search ON customer USING gin (novocore_searchable(phone) gin_trgm_ops);

-- Users and roles. Small tables where an index earns nothing today; they are here so that the
-- shape is uniform and nobody has to work out which entities are "big enough". A GIN index on
-- a twenty-row table costs a few kilobytes.
CREATE INDEX app_user_username_search
    ON app_user USING gin (novocore_searchable(username) gin_trgm_ops);
CREATE INDEX app_user_display_name_search
    ON app_user USING gin (novocore_searchable(display_name) gin_trgm_ops);

CREATE INDEX app_role_name_search ON app_role USING gin (novocore_searchable(name) gin_trgm_ops);
CREATE INDEX app_role_description_search
    ON app_role USING gin (novocore_searchable(description) gin_trgm_ops);
