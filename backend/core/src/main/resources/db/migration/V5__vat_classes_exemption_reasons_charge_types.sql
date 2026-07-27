-- VAT classes, AADE VAT exemption reasons, and chargeable fee types.
--
-- ---------------------------------------------------------------------------------------
-- WHY THESE ARE TABLES AND NOT ENUMS
-- ---------------------------------------------------------------------------------------
-- All three are lookups that must be editable at runtime, through the application, without a
-- code change or a migration:
--
--   * VAT rates change by statute. The 3% and island-reduced 4% classes below exist only
--     because of αρ.31 ν.5057/2023. An enum would make the next statute a deployment.
--   * Exemption reasons are AADE's list, and AADE revises it.
--   * More chargeable fee types are expected beyond COD and delivery.
--
-- ---------------------------------------------------------------------------------------
-- NUMERIC CONVENTION — a third meaning for numeric(19,6)
-- ---------------------------------------------------------------------------------------
-- V1 named two numeric shapes: numeric(19,2) for a posted monetary amount and numeric(19,6)
-- for a quantity or unit cost. `vat_class.rate_percent` is a third kind of value — a rate —
-- and it takes numeric(19,6).
--
-- That is not a loophole. The distinction that matters is between a *posted amount*, which is
-- exactly two decimals because that is what a cent is, and a *multiplier*, which is multiplied
-- by an amount and must not itself be the thing that loses precision before the product is
-- rounded once. Quantities, unit costs and rates are all multipliers. The schema-convention
-- test allows scale 6 for precisely this class of value.
--
-- The rate is stored as a PERCENTAGE, not a fraction: 24% is 24.000000, not 0.24. Percent is
-- how AADE, the accountant and every invoice state it, and VatClassView.multiplier() does the
-- one conversion. A CHECK refuses anything outside 0-100, so a rate mistakenly entered as a
-- fraction fails loudly instead of undercharging by a factor of a hundred.

-- ---------------------------------------------------------------------------------------
-- VAT classes
-- ---------------------------------------------------------------------------------------

CREATE TABLE vat_class (
    id                      bigserial     PRIMARY KEY,

    -- The code from the invoicing system (Prosvasis Go), e.g. '1410' for 24%.
    --
    -- THE CODE IS THE IDENTITY, NEVER THE RATE. The seed below contains two distinct classes
    -- both charging 4%: '1040' as a rate in its own right, and '1041' as the island-reduced
    -- counterpart of 6% under αρ.31 ν.5057/2023. Same percentage, different legal basis,
    -- different code. Anything that looks a VAT class up by its rate is ambiguous by
    -- construction and will be correct most of the time, which is the worst outcome available.
    code                    varchar(20)   NOT NULL,
    description             varchar(200)  NOT NULL,
    rate_percent            numeric(19,6) NOT NULL,

    -- The island-reduced equivalent of this class. Held on the mainland rate, pointing at the
    -- reduced one: the 24% row points at the 17% row.
    --
    -- Data only. Nothing in Phase 1 chooses between a rate and its counterpart based on where
    -- the goods are shipping — that logic is explicitly future scope. This column exists so the
    -- relationship is recorded now rather than reconstructed later from memory.
    reduced_counterpart_id  bigint,

    active                  boolean       NOT NULL DEFAULT true,

    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              varchar(100)  NOT NULL DEFAULT 'system',
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT vat_class_reduced_counterpart_fk
        FOREIGN KEY (reduced_counterpart_id) REFERENCES vat_class (id),

    CONSTRAINT vat_class_code_unique        UNIQUE (code),
    -- One-to-one: a reduced rate is the counterpart of exactly one mainland rate. True of the
    -- current regime and of the seed below. If a future statute has two mainland rates sharing
    -- one island rate, drop this constraint — it is an assertion about the data, not a
    -- structural requirement.
    CONSTRAINT vat_class_reduced_counterpart_unique UNIQUE (reduced_counterpart_id),

    CONSTRAINT vat_class_code_not_blank        CHECK (btrim(code) <> ''),
    CONSTRAINT vat_class_description_not_blank CHECK (btrim(description) <> ''),
    CONSTRAINT vat_class_rate_is_a_percentage
        CHECK (rate_percent >= 0 AND rate_percent <= 100),
    CONSTRAINT vat_class_not_own_counterpart
        CHECK (reduced_counterpart_id IS NULL OR reduced_counterpart_id <> id)
);

COMMENT ON TABLE vat_class IS
    'Runtime-editable VAT rate lookup. Code is the identity; two classes may share a rate.';
COMMENT ON COLUMN vat_class.rate_percent IS
    'A percentage: 24% is 24.000000, not 0.24. numeric(19,6) as a multiplier, not an amount.';
COMMENT ON COLUMN vat_class.reduced_counterpart_id IS
    'The island-reduced equivalent. Recorded as data; no automatic rate switching exists.';

-- Seed: the currently active rate list from Prosvasis Go.
--
-- Nine rows, eight distinct percentages — 4% appears twice, as '1040' and as '1041'.
-- All nine are seeded, not just the four mainland ones, because we do ship to islands under
-- the special reduced-VAT regime, so the reduced classes are in real use.
INSERT INTO vat_class (code, description, rate_percent) VALUES
    ('0',    'Μηδενικός Συντελεστής ΦΠΑ 0%',   0),
    ('1030', 'ΦΠΑ 3% (αρ.31 ν.5057/2023)',     3),
    ('1040', 'ΦΠΑ 4%',                         4),
    ('1041', 'ΦΠΑ 4% (αρ.31 ν.5057/2023)',     4),
    ('1060', 'ΦΠΑ 6%',                         6),
    ('1091', 'ΦΠΑ 9%',                         9),
    ('1131', 'ΦΠΑ 13%',                       13),
    ('1170', 'ΦΠΑ 17%',                       17),
    ('1410', 'ΦΠΑ 24%',                       24);

-- The island-reduced mappings, applied as an UPDATE because the rows have to exist before they
-- can reference each other. Joined by code rather than by generated id.
--
--   mainland  ->  island-reduced
--       24%   ->      17%
--       13%   ->       9%
--        6%   ->       4%  ('1041', not '1040')
--        4%   ->       3%
--
-- The 0% class has no counterpart: there is nothing below zero to reduce it to.
UPDATE vat_class mainland
SET    reduced_counterpart_id = reduced.id
FROM   vat_class reduced,
       (VALUES ('1410', '1170'),
               ('1131', '1091'),
               ('1060', '1041'),
               ('1040', '1030')) AS mapping (mainland_code, reduced_code)
WHERE  mainland.code = mapping.mainland_code
  AND  reduced.code  = mapping.reduced_code;

-- ---------------------------------------------------------------------------------------
-- AADE VAT exemption reasons
-- ---------------------------------------------------------------------------------------
-- DELIBERATELY UNSEEDED. The structure is here; the ~29 rows are supplied separately.
--
-- These are not display text. Each entry names an article of the Κώδικας ΦΠΑ, and the myDATA
-- code is transmitted to AADE, so a transcription error is a compliance defect rather than a
-- cosmetic one. Guessing at them from memory to "fill in the table" would be worse than an
-- empty table, because an empty table is visibly incomplete and a plausible wrong list is not.
--
-- NOT the same thing as a 0% VAT class, and not merged into vat_class for that reason. A
-- zero-rated line charges 0% under a rate that exists; an exempt line is outside VAT because a
-- named article says so. They are reported differently to myDATA.

CREATE TABLE vat_exemption_reason (
    id                    bigserial    PRIMARY KEY,

    -- AADE's reason number, roughly 1-31 with gaps where numbers are retired.
    --
    -- An integer, not text: myDATA's own field is numeric, and text would sort '10' before '2'
    -- in a picker of ~29 entries. If AADE ever issues a letter-suffixed code this becomes a
    -- migration — an explicit one, rather than carrying a speculative varchar plus a separate
    -- sort column from the start for a case that may never arrive.
    code                  integer      NOT NULL,
    description           varchar(400) NOT NULL,

    -- The exact string myDATA expects, e.g. '6-Χωρίς ΦΠΑ - άρθρο 24 του Κώδικα ΦΠΑ'.
    --
    -- Stored verbatim rather than composed from code and description at use time. It is what
    -- actually goes on the wire, and reproducing AADE's exact punctuation and spacing by string
    -- concatenation is a bet that costs nothing to avoid. A test can compare the two once the
    -- real rows are loaded, which is the right time to find out whether the composition holds.
    mydata_code           varchar(500) NOT NULL,

    -- AADE's "Δικαίωμα έκπτωσης Φ.Π.Α. εισροών". Every entry seen so far is "Όχι", but this is
    -- a genuine per-reason distinction in AADE's own table rather than descriptive text, so the
    -- column exists even though it may start out uniformly false.
    input_vat_deductible  boolean      NOT NULL,

    active                boolean      NOT NULL DEFAULT true,

    created_at            timestamptz  NOT NULL DEFAULT now(),
    created_by            varchar(100) NOT NULL DEFAULT 'system',
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    updated_by            varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT vat_exemption_reason_code_unique        UNIQUE (code),
    CONSTRAINT vat_exemption_reason_mydata_code_unique UNIQUE (mydata_code),

    CONSTRAINT vat_exemption_reason_code_positive      CHECK (code > 0),
    CONSTRAINT vat_exemption_reason_description_not_blank CHECK (btrim(description) <> ''),
    CONSTRAINT vat_exemption_reason_mydata_code_not_blank CHECK (btrim(mydata_code) <> '')
);

COMMENT ON TABLE vat_exemption_reason IS
    'Official AADE VAT exemption reasons, each tied to an article of the Κώδικας ΦΠΑ. '
    'Unseeded pending the verified list. Not the same as a 0% VAT class.';

-- ---------------------------------------------------------------------------------------
-- Chargeable fee types
-- ---------------------------------------------------------------------------------------
-- Fees charged TO the customer as revenue: a cash-on-delivery charge, a delivery cost.
--
-- The income side only. The courier fee we ourselves incur is an unrelated expense posting
-- against Transportation costs, and the two being near-homonyms is exactly why the revenue side
-- is modelled explicitly: netting one against the other understates revenue and cost together
-- and leaves a gross margin that looks plausible while being wrong.
--
-- UNSEEDED for now: which income account each fee posts to is an open decision (existing
-- 'Other income' versus dedicated 'Delivery income' / 'COD fee income' accounts), and seeding
-- against the wrong one would mean migrating posted history later.
--
-- Nothing consumes this table yet. Sales Invoice line items are step 9.

CREATE TABLE charge_type (
    id                    bigserial    PRIMARY KEY,
    name                  varchar(120) NOT NULL,

    -- Fees are taxable. Overridable per line by the ordinary VAT precedence rule
    -- (line beats customer beats product — see VatClassPrecedence).
    default_vat_class_id  bigint       NOT NULL,

    -- Constrained to an INCOME-type account by ChargeTypeService. That check cannot be a CHECK
    -- constraint, because the account's type lives in another table; it is enforced in the
    -- service and covered by a test.
    income_account_id     bigint       NOT NULL,

    active                boolean      NOT NULL DEFAULT true,

    created_at            timestamptz  NOT NULL DEFAULT now(),
    created_by            varchar(100) NOT NULL DEFAULT 'system',
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    updated_by            varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT charge_type_vat_class_fk
        FOREIGN KEY (default_vat_class_id) REFERENCES vat_class (id),
    CONSTRAINT charge_type_income_account_fk
        FOREIGN KEY (income_account_id) REFERENCES account (id),

    CONSTRAINT charge_type_name_unique    UNIQUE (name),
    CONSTRAINT charge_type_name_not_blank CHECK (btrim(name) <> '')
);

COMMENT ON TABLE charge_type IS
    'Chargeable fees appearing as sales invoice lines (COD, delivery). Income side only. '
    'Unseeded pending the income-account decision; nothing consumes it until step 9.';
