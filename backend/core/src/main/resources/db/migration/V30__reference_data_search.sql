-- =========================================================================================
-- V30 — SUBSTRING SEARCH ON THE TWO REFERENCE-DATA LISTS (VAT CLASSES, UNITS OF MEASURE)
-- =========================================================================================
--
-- Rows 6 and 7 of the search target list in `docs/PROGRESS.md`, adopted by F4 — the step that
-- builds their screens. Both rows say "Name / Code", and both entities and their routes have
-- existed since V5 and V11; only the screen was missing. Adopting search is therefore the
-- one-line service change V28 was built for, plus the indexes below.
--
-- The column each row calls "Name" is spelled differently on the two tables, and they are NOT
-- interchangeable:
--
--     vat_class        code  +  description     -- `description` is the label, e.g. 'ΦΠΑ 24%'
--     unit_of_measure  code  +  name            -- `name` is the label, e.g. 'Kilogram'
--
-- The routes differ accordingly (PATCH .../description vs PATCH .../name), which is why this
-- is worth stating rather than leaving to be inferred from a column list.
--
-- -----------------------------------------------------------------------------------------
-- WHY A GIN INDEX ON A NINE-ROW TABLE
-- -----------------------------------------------------------------------------------------
-- These are the smallest searchable tables in the schema: 9 VAT classes and 8 units of
-- measure today, and neither will ever be large. A sequential scan over nine rows is free,
-- so on today's data these four indexes buy nothing measurable.
--
-- They are here anyway, for two reasons that are about the system rather than the query plan:
--
--   1. `TextSearchIT.everySearchedColumnIsIndexed` asserts that EVERY column any service
--      names as searchable has one. That guard exists because a missing index fails quietly —
--      correct results, sequential scan, discovered months later on a production-sized table.
--      A per-table exemption for "this one is small" is exactly the hole that makes the guard
--      unreliable for the tables where it matters, so the rule stays uniform and the cost of
--      keeping it uniform here is four indexes on 17 rows.
--
--   2. Units of measure grow with the catalogue. The eight seeded ones are the obvious
--      physical units; a business that starts selling by the pallet, the case or the metre-run
--      adds rows, and nothing about this table forbids it. VAT classes genuinely do not grow —
--      they are ΑΑΔΕ's list — but see the note below on why a rate change ADDS a row.
--
-- ⚠️ Every one of these expressions must be EXACTLY `novocore_searchable(column)`. See V28 for
-- why the function has to be IMMUTABLE, why the one-argument `unaccent` cannot be used, and
-- why the collation is named explicitly instead of inherited from the server.
--
-- -----------------------------------------------------------------------------------------
-- WHAT IS NOT SEARCHED, AND WHY
-- -----------------------------------------------------------------------------------------
-- `vat_class.rate_percent` is NOT searchable, and that is not an omission. It is numeric, and
-- the target list asks for name and code. More to the point: nine rows carry EIGHT distinct
-- percentages — 4% appears twice, as `1040` and as `1041` under a different legal basis
-- (αρ.31 ν.5057/2023) with a different meaning. Anything that locates a VAT class by its rate
-- is correct most of the time, which is the worst outcome available. `VatClassService`
-- deliberately offers no `findByRate` for the same reason.
--
-- `unit_of_measure.mydata_code` is NOT searched either. It is NULL on all eight seeded rows —
-- the verified ΑΑΔΕ list has not been supplied (see V11) — so a search box over it would
-- match nothing on every installation that exists today. `GET /api/units-of-measure/
-- without-mydata-code` is the route that answers the question actually being asked about that
-- column, which is "which units are still unmapped".
--
-- ⚠️ A RATE CHANGE ADDS A ROW. There is deliberately no route that edits a rate — editing one
-- would retroactively change what every invoice already issued under that class appears to
-- have charged — so a rate change is a new class plus a deactivation of the old one. That is
-- the one way this table grows, and it means a search over `code` and `description` has to
-- keep working across deactivated rows. It does: `?active=true` is the only value that
-- filters, and search composes with it rather than replacing it.

CREATE INDEX vat_class_code_search
    ON vat_class USING gin (novocore_searchable(code) gin_trgm_ops);

CREATE INDEX vat_class_description_search
    ON vat_class USING gin (novocore_searchable(description) gin_trgm_ops);

CREATE INDEX unit_of_measure_code_search
    ON unit_of_measure USING gin (novocore_searchable(code) gin_trgm_ops);

CREATE INDEX unit_of_measure_name_search
    ON unit_of_measure USING gin (novocore_searchable(name) gin_trgm_ops);
