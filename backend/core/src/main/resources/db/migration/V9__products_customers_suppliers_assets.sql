-- Step 5: the four core entities — Product, Customer, Supplier, Asset.
--
-- ---------------------------------------------------------------------------------------
-- THE FIELD BOUNDARY RULE, APPLIED
-- ---------------------------------------------------------------------------------------
-- CLAUDE.md rules 1 and 2. Not one column here exists because Prosvasis Go or WooCommerce has
-- it. There is no go_product_id, no woo_product_id, no woo_customer_id: each adapter keeps its
-- own external-id-to-core-id mapping table when it is built (phase 3). The core knows its own
-- SKU and its own ids, and nothing else.
--
-- ---------------------------------------------------------------------------------------
-- WHAT IS DELIBERATELY ABSENT
-- ---------------------------------------------------------------------------------------
-- * NO STOCK COLUMN on product. Stock is the sum of a product's lots (brief §5: never stored),
--   lots arrive in step 6, and it is not one number anyway — location lives on the lot and
--   sellability depends on stock at a sellable location.
-- * NO LAST PURCHASE PRICE column. Derivable from lot costs, so it is computed, for the same
--   reason as stock. It still appears on ProductView, because it is one of the three
--   role-restricted fields and the redaction is worth having tested before there is a value.
-- * NO COST OR ACCUMULATED DEPRECIATION on asset. Both fixed-asset control accounts declare
--   ASSET as their sub-ledger, so every posting names its asset and both figures are sums of
--   journal lines. A stored acquisition cost is a second copy of a number the ledger holds,
--   free to drift from it after the first correcting entry. Consequence, stated plainly: until
--   step 7 an asset has no cost, and this is a register rather than a valuation.
-- * NO USEFUL LIFE and NO SALVAGE VALUE on asset. For straight-line depreciation a life is
--   100/rate years, so storing both invites them to disagree — the same argument that keeps
--   normal_balance_side out of the chart of accounts. Greek statutory rates are published as
--   percentages, so the rate is the form the real data arrives in. Salvage value is omitted
--   because Greek tax depreciation writes down to zero; both are recorded as open items rather
--   than closed ones.
-- * NO DEPRECIATION METHOD column. Straight-line only (brief §5). A single-valued column is
--   dead weight; a second method arriving is a migration with a decision attached.
-- * NO BUNDLE FLAG on product. Bundles are in brief §5 but were left out of the agreed Phase 1
--   scope and are still an open question. A flag nothing honours reads as a half-built feature.
-- * NO ADDRESS on customer or supplier. Prosvasis Go issues the invoices until phase 11, so
--   nothing here needs to print one yet. Open item, not an oversight.
--
-- ---------------------------------------------------------------------------------------
-- THE FIRST MONETARY COLUMN IN THE SCHEMA
-- ---------------------------------------------------------------------------------------
-- product.selling_price is numeric(19,2) with a companion selling_price_currency char(3), which
-- makes it the first column to actually exercise V1's monetary convention and ADR 0005's rule
-- that every monetary column carries its currency.
--
-- V1 left the companion's NAMING to be settled by the first such column, expecting that to be
-- the journal in step 7. It turns out to be here instead, so the convention is stated now:
--
--     <name>            numeric(19,2)  the amount
--     <name>_currency   char(3)        its ISO 4217 code, NOT NULL when the amount is
--
-- A biconditional CHECK ties them together, so an amount can never exist without its currency
-- and a currency can never linger after the amount is cleared. SchemaConventionsIT now asserts
-- this pairing across the whole schema, so step 7's monetary columns inherit it rather than
-- re-deciding it.

-- ---------------------------------------------------------------------------------------
-- Suppliers
-- ---------------------------------------------------------------------------------------
-- First, because product references it.

CREATE TABLE supplier (
    id                       bigserial    PRIMARY KEY,
    name                     varchar(200) NOT NULL,

    -- One each, not a one-to-many table (Q8, answered for customers and applied here for
    -- symmetry). One address and one number is what the business has; a multi-value table would
    -- have to be joined, rendered and de-duplicated everywhere for a case that has not arisen.
    email                    varchar(255),
    phone                    varchar(40),

    -- ΑΦΜ or EU VAT number. UNIQUE when present: brief §5 makes it the authoritative identifier
    -- for matching, so two suppliers sharing one would make automatic matching ambiguous exactly
    -- where it is supposed to be certain. Never validated against VIES — that adapter is phase 7,
    -- and this column holding an unverified value is the honest state until then.
    vat_number               varchar(30),

    -- A gr.novotrade.novocore.core.api.tax.VatStatus name. Constrained by CHECK rather than a
    -- lookup table, for V6's reason: the authoritative list is the Java enum, and a second copy
    -- of it in a table is how a value becomes storable but unhandled.
    vat_status               varchar(20)  NOT NULL,

    -- The article supplies from this supplier are outside VAT under. Required when the status is
    -- EXEMPT; see the CHECK below.
    vat_exemption_reason_id  bigint,

    active                   boolean      NOT NULL DEFAULT true,

    created_at               timestamptz  NOT NULL DEFAULT now(),
    created_by               varchar(100) NOT NULL DEFAULT 'system',
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    updated_by               varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT supplier_vat_exemption_reason_fk
        FOREIGN KEY (vat_exemption_reason_id) REFERENCES vat_exemption_reason (id),

    CONSTRAINT supplier_name_unique       UNIQUE (name),
    CONSTRAINT supplier_vat_number_unique UNIQUE (vat_number),

    CONSTRAINT supplier_name_not_blank       CHECK (btrim(name) <> ''),
    -- Same shape as account.code: "no value" has exactly one representation, so '' cannot be
    -- used as a second one and collide on the unique index for a reason nobody would guess.
    CONSTRAINT supplier_vat_number_not_blank CHECK (vat_number IS NULL OR btrim(vat_number) <> ''),
    CONSTRAINT supplier_email_not_blank      CHECK (email IS NULL OR btrim(email) <> ''),
    CONSTRAINT supplier_phone_not_blank      CHECK (phone IS NULL OR btrim(phone) <> ''),

    CONSTRAINT supplier_vat_status_known CHECK (vat_status IN (
        'DOMESTIC', 'INTRA_EU_B2B', 'NON_EU_EXPORT', 'EXEMPT', 'OTHER')),

    -- Definitional, not policy: with no counterparty VAT number there is no reverse charge to
    -- apply, so the status is not true of the party.
    CONSTRAINT supplier_intra_eu_needs_vat_number CHECK (
        vat_status <> 'INTRA_EU_B2B' OR vat_number IS NOT NULL),
    -- "Exempt" with no article named cannot be reported and cannot be told apart from a mistake.
    CONSTRAINT supplier_exempt_needs_reason CHECK (
        vat_status <> 'EXEMPT' OR vat_exemption_reason_id IS NOT NULL)
);

COMMENT ON TABLE supplier IS
    'Sub-ledger behind Accounts payable and GR/IR clearing. No external system ids (rule 2), '
    'no balance (it is the sum of the journal lines).';

CREATE INDEX supplier_active_idx ON supplier (active) WHERE active;
-- Suggestive-only matching (brief §5) reads these; case-insensitive because an imported address
-- capitalised differently is the same address.
CREATE INDEX supplier_email_lower_idx ON supplier (lower(email));

-- ---------------------------------------------------------------------------------------
-- Customers
-- ---------------------------------------------------------------------------------------

CREATE TABLE customer (
    id                       bigserial    PRIMARY KEY,

    -- NOT unique, unlike supplier.name. Two unrelated retail customers genuinely can both be
    -- called "Γιώργος Παπαδόπουλος", and refusing the second would push whoever is serving them
    -- into inventing a distinguishing suffix. The VAT number is the identifier that is unique.
    name                     varchar(200) NOT NULL,

    -- Single email and single phone (Q8, answered). Suggestive-only for matching: a shared
    -- household address or a company switchboard number is evidence, not proof.
    email                    varchar(255),
    phone                    varchar(40),

    vat_number               varchar(30),
    vat_status               varchar(20)  NOT NULL,

    -- The CUSTOMER level of the VAT precedence rule — invoice line beats customer beats product
    -- (VatClassPrecedence, step 3b). Nullable on purpose: an override is the exception, and a
    -- value copied onto every customer would quietly become the level that always wins.
    vat_class_override_id    bigint,
    vat_exemption_reason_id  bigint,

    active                   boolean      NOT NULL DEFAULT true,

    created_at               timestamptz  NOT NULL DEFAULT now(),
    created_by               varchar(100) NOT NULL DEFAULT 'system',
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    updated_by               varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT customer_vat_class_override_fk
        FOREIGN KEY (vat_class_override_id) REFERENCES vat_class (id),
    CONSTRAINT customer_vat_exemption_reason_fk
        FOREIGN KEY (vat_exemption_reason_id) REFERENCES vat_exemption_reason (id),

    CONSTRAINT customer_vat_number_unique UNIQUE (vat_number),

    CONSTRAINT customer_name_not_blank       CHECK (btrim(name) <> ''),
    CONSTRAINT customer_vat_number_not_blank CHECK (vat_number IS NULL OR btrim(vat_number) <> ''),
    CONSTRAINT customer_email_not_blank      CHECK (email IS NULL OR btrim(email) <> ''),
    CONSTRAINT customer_phone_not_blank      CHECK (phone IS NULL OR btrim(phone) <> ''),

    CONSTRAINT customer_vat_status_known CHECK (vat_status IN (
        'DOMESTIC', 'INTRA_EU_B2B', 'NON_EU_EXPORT', 'EXEMPT', 'OTHER')),

    CONSTRAINT customer_intra_eu_needs_vat_number CHECK (
        vat_status <> 'INTRA_EU_B2B' OR vat_number IS NOT NULL),
    CONSTRAINT customer_exempt_needs_reason CHECK (
        vat_status <> 'EXEMPT' OR vat_exemption_reason_id IS NOT NULL)
);

COMMENT ON TABLE customer IS
    'Sub-ledger behind Accounts receivable. Own internal id only (rule 2); VAT number is the '
    'authoritative match, email and phone are suggestive-only.';

COMMENT ON COLUMN customer.vat_class_override_id IS
    'The customer level of the VAT precedence rule. Null means the product default stands.';

CREATE INDEX customer_active_idx ON customer (active) WHERE active;
CREATE INDEX customer_email_lower_idx ON customer (lower(email));
CREATE INDEX customer_phone_idx ON customer (phone);

-- NO CUSTOMER IS SEEDED. Q10 asks whether the shared generic "Πελάτης Λιανικής" retail record
-- should exist; that is unanswered, and a seeded catch-all customer is the kind of row that
-- quietly absorbs every unmatched sale and then cannot be untangled. Left out pending the answer.

-- ---------------------------------------------------------------------------------------
-- Products
-- ---------------------------------------------------------------------------------------

CREATE TABLE product (
    id                       bigserial    PRIMARY KEY,

    -- NovoCore's OWN SKU, and the handle everything else uses. Not a supplier's code and not
    -- Go's or Woo's identifier.
    sku                      varchar(60)  NOT NULL,

    -- The barcode. Unique when present; null for own-blend coffee bagged in-store and for
    -- services. Brief §5's barcode-first entry point looks products up by this.
    ean                      varchar(20),

    name                     varchar(300) NOT NULL,

    -- A gr.novotrade.novocore.core.api.product.ProductType name. Decides real behaviour: a
    -- service has no lots, credits Services rather than a channel Sales account, and costs
    -- against Cost of service sold — two accounts the seeded chart already distinguishes.
    product_type             varchar(20)  NOT NULL,

    -- A gr.novotrade.novocore.core.api.product.UnitOfMeasure name. Present because a quantity
    -- carries six decimals and 0.250 is only meaningful next to what it is 0.250 of.
    unit_of_measure          varchar(20)  NOT NULL,

    -- The PRODUCT level of the VAT precedence rule, and NOT NULL. There is deliberately no
    -- fallback rate anywhere in NovoCore, so a product without a VAT class is one that cannot be
    -- invoiced — better refused at creation than discovered mid-sale.
    default_vat_class_id     bigint       NOT NULL,

    -- The first monetary column in the schema. See the header for the companion convention.
    -- Nullable: a product imported from an external catalogue or created barcode-first may not
    -- have its price yet, and refusing to record the product would be worse. A null price must be
    -- refused at invoicing rather than treated as zero — a zero price is a silently free sale.
    selling_price            numeric(19,2),
    selling_price_currency   char(3),

    -- Q5, answered: ONE product, ONE supplier. A plain nullable reference, no many-to-many.
    -- Nullable because own-blend products and services have no supplier.
    supplier_id              bigint,

    -- The supplier's own code for this product. This is the field Q5 was about: it means nothing
    -- without knowing whose code it is, so the CHECK below refuses one without the other.
    supplier_sku             varchar(60),

    active                   boolean      NOT NULL DEFAULT true,

    created_at               timestamptz  NOT NULL DEFAULT now(),
    created_by               varchar(100) NOT NULL DEFAULT 'system',
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    updated_by               varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT product_default_vat_class_fk
        FOREIGN KEY (default_vat_class_id) REFERENCES vat_class (id),
    CONSTRAINT product_supplier_fk
        FOREIGN KEY (supplier_id) REFERENCES supplier (id),

    CONSTRAINT product_sku_unique UNIQUE (sku),
    CONSTRAINT product_ean_unique UNIQUE (ean),

    CONSTRAINT product_sku_not_blank          CHECK (btrim(sku) <> ''),
    CONSTRAINT product_name_not_blank         CHECK (btrim(name) <> ''),
    CONSTRAINT product_ean_not_blank          CHECK (ean IS NULL OR btrim(ean) <> ''),
    CONSTRAINT product_supplier_sku_not_blank CHECK (
        supplier_sku IS NULL OR btrim(supplier_sku) <> ''),

    CONSTRAINT product_type_known CHECK (product_type IN ('GOODS', 'SERVICE')),

    CONSTRAINT product_unit_of_measure_known CHECK (unit_of_measure IN (
        'PIECE', 'SET', 'PACK', 'KILOGRAM', 'GRAM', 'LITRE', 'MILLILITRE', 'METRE')),

    -- Q5 made structural. Not biconditional: a supplier with no supplier SKU is perfectly
    -- ordinary — we buy it from them under our own reference. The reverse is the meaningless one.
    CONSTRAINT product_supplier_sku_needs_supplier CHECK (
        supplier_sku IS NULL OR supplier_id IS NOT NULL),

    -- Zero is refused as well as negative. A zero price is indistinguishable from an unset one on
    -- a screen and produces an invoice line worth nothing without anyone choosing to give the
    -- goods away; NULL says "no price yet" unambiguously.
    CONSTRAINT product_selling_price_positive CHECK (
        selling_price IS NULL OR selling_price > 0),

    -- ADR 0005 made structural, and the pattern every future monetary column follows: an amount
    -- can never exist without its currency, and a currency can never linger after the amount is
    -- cleared.
    CONSTRAINT product_selling_price_has_currency CHECK (
        (selling_price IS NULL) = (selling_price_currency IS NULL))
);

COMMENT ON TABLE product IS
    'The catalogue, and the product side of the Inventory sub-ledger. Own SKU only (rule 2); '
    'stock and last purchase price are derived from lots, never stored.';

COMMENT ON COLUMN product.selling_price IS
    'First monetary column in the schema. numeric(19,2) with a char(3) currency companion, per '
    'V1 and ADR 0005. Null means no price set, never zero.';

COMMENT ON COLUMN product.supplier_sku IS
    'The supplier''s own code. Refused without a supplier: it identifies nothing on its own (Q5).';

CREATE INDEX product_active_idx ON product (active) WHERE active;
CREATE INDEX product_supplier_idx ON product (supplier_id) WHERE supplier_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------
-- Fixed assets
-- ---------------------------------------------------------------------------------------

CREATE TABLE asset (
    id                          bigserial    PRIMARY KEY,

    -- The operator's own asset tag, if they use one. Same nullable-unique-when-present shape as
    -- account.code.
    code                        varchar(40),
    name                        varchar(200) NOT NULL,

    acquisition_date            date         NOT NULL,

    -- The annual straight-line rate as a PERCENTAGE: 10% is 10.000000, not 0.1. numeric(19,6)
    -- because a rate is a multiplier, the same precision class as a VAT rate or a quantity — it
    -- must not be what loses precision before the product is rounded once.
    --
    -- Bounded 1-100 below, and the lower bound is load-bearing rather than cosmetic — see the
    -- CHECK's comment. NULLABLE, AND THAT IS THE POINT RIGHT NOW: the statutory rates per category
    -- have not
    -- been supplied by the accountant yet, and an invented rate produces a depreciation charge
    -- that looks plausible and is wrong in a filed set of accounts. Null means "not yet known";
    -- AssetService.withoutDepreciationRate() is how that stops being forgotten, and
    -- AssetView.canDepreciate() is what a run must check rather than substituting a default.
    depreciation_rate_percent   numeric(19,6),

    -- When depreciation begins, if that is not the acquisition date — an asset can be bought in
    -- one period and placed in service in another. Null means "same as acquisition".
    depreciation_start_date     date,

    -- A gr.novotrade.novocore.core.api.asset.AssetStatus name. A status rather than the `active`
    -- boolean the lookup tables use, because for an asset those would be two different facts
    -- under one name: no longer in use is not the same event as disposed of.
    status                      varchar(20)  NOT NULL DEFAULT 'IN_USE',
    disposal_date               date,

    created_at                  timestamptz  NOT NULL DEFAULT now(),
    created_by                  varchar(100) NOT NULL DEFAULT 'system',
    updated_at                  timestamptz  NOT NULL DEFAULT now(),
    updated_by                  varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT asset_code_unique UNIQUE (code),

    CONSTRAINT asset_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT asset_code_not_blank CHECK (code IS NULL OR btrim(code) <> ''),

    CONSTRAINT asset_status_known CHECK (status IN ('IN_USE', 'DISPOSED')),

    -- Between 1 and 100, and the LOWER bound is the interesting half. A plain 0-100 range cannot
    -- catch the mistake that matters here: 0.1 written for 10% sits comfortably inside it, and the
    -- depreciation charge would be a hundred times too small every year with nothing complaining.
    -- One percent is a hundred-year useful life, which no Greek statutory category has, so a value
    -- below it is overwhelmingly a fraction typed where a percentage was meant. Refusing it is the
    -- fail-loud direction; if a genuine sub-1% rate ever appears, raising this is a deliberate
    -- migration rather than a silent acceptance. Zero is excluded for a different reason: an asset
    -- that never depreciates is what NULL already says.
    CONSTRAINT asset_depreciation_rate_is_a_percentage CHECK (
        depreciation_rate_percent IS NULL
        OR (depreciation_rate_percent >= 1 AND depreciation_rate_percent <= 100)),

    -- Biconditional, like account_control_iff_sub_ledger. A disposed asset with no date cannot be
    -- reported in the period it left; a date on an asset still in use is one nothing will act on.
    CONSTRAINT asset_disposed_iff_disposal_date CHECK (
        (status = 'DISPOSED') = (disposal_date IS NOT NULL)),

    CONSTRAINT asset_disposal_not_before_acquisition CHECK (
        disposal_date IS NULL OR disposal_date >= acquisition_date),
    CONSTRAINT asset_depreciation_start_not_before_acquisition CHECK (
        depreciation_start_date IS NULL OR depreciation_start_date >= acquisition_date)
);

COMMENT ON TABLE asset IS
    'The fixed asset register — sub-ledger behind Fixed assets at cost and accumulated '
    'depreciation. Deliberately holds NO cost: both figures are sums of journal lines.';

COMMENT ON COLUMN asset.depreciation_rate_percent IS
    'Annual straight-line rate as a percentage (10% is 10.000000). NULL where the statutory rate '
    'is not yet confirmed — never guess one, a depreciation run must skip and report instead.';

CREATE INDEX asset_status_idx ON asset (status);

-- NO ASSET IS SEEDED, and no rate reference table exists. The statutory rates per asset category
-- are pending the accountant, the same way the VAT class list was, and the category taxonomy they
-- attach to is pending with them. Deriving a default rate from an asset's ΕΛΠ account mapping was
-- considered and deferred; the per-asset rate has to exist regardless, since a specific asset can
-- legitimately differ from its category.

-- ---------------------------------------------------------------------------------------
-- Permission sections for the four new areas
-- ---------------------------------------------------------------------------------------
-- PRODUCTS and CUSTOMERS were already grantable (V6 seeded Remote/Order Staff against them
-- before the entities existed, which is exactly what the reserved-section mechanism is for).
-- SUPPLIERS and FIXED_ASSETS are new Section values, so the CHECK has to learn them — the
-- authoritative list is the Java enum, and this constraint is what keeps the two in step.
--
-- NO NEW GRANTS ARE SEEDED. Access is default-deny, so the two new sections are invisible to
-- Remote/Order Staff without saying so, and visible to Owner and Admin at once because those use
-- the full_access flag rather than stored grants per section.

ALTER TABLE role_section_grant
    DROP CONSTRAINT role_section_grant_section_known;

ALTER TABLE role_section_grant
    ADD CONSTRAINT role_section_grant_section_known CHECK (section IN (
        'CHART_OF_ACCOUNTS',
        'TAX_AND_CHARGES',
        'SETTINGS',
        'AUDIT_LOG',
        'USERS_AND_ROLES',
        'PRODUCTS',
        'CUSTOMERS',
        'SUPPLIERS',
        'FIXED_ASSETS',
        'SALES_ORDER_FULFILLMENT',
        'BACK_IN_STOCK_REMINDERS'));
