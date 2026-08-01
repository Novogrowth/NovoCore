-- ---------------------------------------------------------------------------------------
-- V29 — Product.brand, and the supplier VAT number search V28 should have included
-- ---------------------------------------------------------------------------------------
--
-- One of the fields brief §5's Product list has always named and step 5 did not build. It is
-- added now because it is one of the columns an operator searches by: "which Rocket machines
-- do we stock" is a question the catalogue could not answer at all, since the brand only ever
-- appeared inside the product's title if somebody happened to type it there.
--
-- ---------------------------------------------------------------------------------------
-- A PLAIN COLUMN, NOT A BRAND TABLE — AND THAT IS A DECISION, NOT A SHORTCUT
-- ---------------------------------------------------------------------------------------
-- One brand per product, stored as text, with no uniqueness constraint on the value and no
-- reference table behind it. Two products spelling the same brand differently is therefore
-- possible, and is accepted.
--
-- The alternative — a `brand` table with a foreign key — buys referential consistency and
-- costs a second screen, a second create flow, and a decision about what happens when a brand
-- is renamed or merged. Nothing in the roadmap asks for brand-level reporting, and a brand is
-- not an accounting object: no journal line refers to one, no report groups by one, and
-- nothing computes from one. When one of those becomes true, this column is what gets
-- migrated into a reference — which is a smaller change than unpicking a table nobody needed.
--
-- Contrast `unit_of_measure`, which IS a table: a unit constrains what quantities may be
-- expressed, and a product's lots are denominated in it. That is a rule. A brand is a label.
--
-- ---------------------------------------------------------------------------------------
-- NULLABLE, AND '' IS NOT A SECOND WAY TO SAY NULL
-- ---------------------------------------------------------------------------------------
-- Most of this catalogue is own-blend coffee bagged in-store, which has no brand at all, so
-- NULL is an ordinary value here rather than an unfilled field. The CHECK is the same shape
-- this schema applies to every optional text column (see `account.code` and `supplier_sku`):
-- "no value" gets exactly one representation, so a blank string cannot be stored and a search
-- for a brand cannot half-match a row that has none.
--
-- ---------------------------------------------------------------------------------------
-- CATEGORY IS DELIBERATELY NOT IN THIS MIGRATION
-- ---------------------------------------------------------------------------------------
-- Brief §5 names Category alongside Brand and it is NOT built here, in any form — not even the
-- column. It is not the same kind of field: the requirement is three levels deep
-- (category / subcategory / sub-subcategory) and a product belongs to SEVERAL categories at
-- once, WooCommerce-style. That is a self-referencing category table plus a join table, and it
-- is its own proposal. See `docs/PROGRESS.md`, which records the requirement so it does not get
-- rebuilt as two flat columns by somebody reading only the brief's one-line summary.

ALTER TABLE product
    ADD COLUMN brand varchar(120);

ALTER TABLE product
    ADD CONSTRAINT product_brand_not_blank
        CHECK (brand IS NULL OR btrim(brand) <> '');

COMMENT ON COLUMN product.brand IS
    'The manufacturer or brand name, as free text. One per product, not a reference to a brand '
    'table, and not unique: see the V29 migration for why. NULL means the product has no brand, '
    'which is ordinary for own-blend coffee bagged in-store.';

-- Searchable on the same terms as SKU, title, barcode and the supplier''s code — see V28 for why
-- this expression has to be exactly this expression, and why the function must stay IMMUTABLE.
CREATE INDEX product_brand_search ON product USING gin (novocore_searchable(brand) gin_trgm_ops);

-- ---------------------------------------------------------------------------------------
-- SUPPLIER.VAT_NUMBER, SEARCHABLE — RECONCILING V28 AGAINST THE FULL TARGET LIST
-- ---------------------------------------------------------------------------------------
-- Not a new column: `supplier.vat_number` has existed since V9. V28 simply did not search it,
-- while `customer.vat_number` was searched from the start — an inconsistency with no argument
-- behind it, found by reconciling against the complete per-screen field list rather than
-- against the shorter one the step was scoped from. The list now lives in `docs/PROGRESS.md`
-- precisely so this kind of gap is visible.
--
-- The reasoning is the customer one verbatim: an operator reading a partial ΑΦΜ off a document
-- is the case a filter box exists for. It does NOT weaken `GET /api/suppliers/by-vat-number`,
-- which stays an exact lookup because it is the authoritative auto-link (brief §5) and the
-- whole reason it may be applied without asking anybody is that it cannot match approximately.
CREATE INDEX supplier_vat_number_search
    ON supplier USING gin (novocore_searchable(vat_number) gin_trgm_ops);
