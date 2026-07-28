-- Q34: units of measure become a runtime-editable table, replacing the step 5 enum.
--
-- ---------------------------------------------------------------------------------------
-- WHY A TABLE AFTER ALL
-- ---------------------------------------------------------------------------------------
-- Step 5 built this as a Java enum, on the reasoning that the set NovoCore needs is small,
-- known, and physical rather than statutory. Two things outweigh that, and both are the same
-- argument V5 made for VAT classes being a table rather than an enum:
--
--   * Prosvasis Go holds "Μονάδες μέτρησης" as an editable list, so the operator already expects
--     to add one without a deployment;
--   * myDATA has its own unit-of-measure codes that a transmitted invoice line has to carry, and
--     those are AADE's values, not ours.
--
-- The second is the decisive one. A myDATA code has to live somewhere, and hanging AADE's data
-- off a Java enum constant is exactly the shape that made VatExemptionReason a table.
--
-- Converted now rather than after step 6, because lots carry quantities and a quantity is only
-- meaningful next to its unit — the reference gets harder to change with every table that points
-- at it.
--
-- ---------------------------------------------------------------------------------------
-- WHAT IS STILL MISSING, AND IS NULL RATHER THAN GUESSED
-- ---------------------------------------------------------------------------------------
-- `mydata_code` is NULLABLE and every seeded row has NULL, because the verified AADE unit code
-- list has not been supplied. This is the same stance as the OSS/IOSS rows in V8: NULL means "no
-- mapping exists", not "not filled in yet", and phase 7 must refuse to transmit a line whose unit
-- has no code rather than composing one. Guessing at AADE's codes would be worse than an empty
-- column, because a plausible wrong code is invisible and an empty one is not.
--
-- The seeded codes and names below are NovoCore's own, carried over from the step 5 enum so that
-- nothing breaks in the conversion. Go's real code list can replace or extend them at runtime,
-- which is the point of this being a table.

CREATE TABLE unit_of_measure (
    id                            bigserial    PRIMARY KEY,

    -- NovoCore's own stable identifier for the unit. What code refers to when it needs a specific
    -- unit, and what the conversion below joins on. Not AADE's and not Go's.
    code                          varchar(20)  NOT NULL,
    name                          varchar(80)  NOT NULL,

    -- Whether a fractional quantity is meaningful. Three of something sold by the piece is three;
    -- 2.5 pieces is a data-entry error worth catching rather than averaging out later.
    --
    -- Data rather than code because it is a property of the unit, and units are now data. Nothing
    -- enforces it yet — quantities arrive with inventory lots in step 6, which is where the check
    -- belongs, and it is stated here so step 6 reads the rule off the unit instead of keeping a
    -- list of its own.
    fractional_quantity_allowed   boolean      NOT NULL,

    -- AADE's unit code for myDATA. NULL where no mapping is known; see the header.
    mydata_code                   varchar(20),

    active                        boolean      NOT NULL DEFAULT true,

    created_at                    timestamptz  NOT NULL DEFAULT now(),
    created_by                    varchar(100) NOT NULL DEFAULT 'system',
    updated_at                    timestamptz  NOT NULL DEFAULT now(),
    updated_by                    varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT unit_of_measure_code_unique        UNIQUE (code),
    CONSTRAINT unit_of_measure_name_unique        UNIQUE (name),
    CONSTRAINT unit_of_measure_mydata_code_unique UNIQUE (mydata_code),

    CONSTRAINT unit_of_measure_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT unit_of_measure_name_not_blank CHECK (btrim(name) <> ''),
    -- Same shape as account.code and vat_exemption_reason.mydata_code: "no value" has exactly one
    -- representation, so '' cannot become a second one and collide on the unique index.
    CONSTRAINT unit_of_measure_mydata_code_not_blank CHECK (
        mydata_code IS NULL OR btrim(mydata_code) <> '')
);

COMMENT ON TABLE unit_of_measure IS
    'Runtime-editable unit lookup (Q34). Was a Java enum in step 5; became a table because '
    'myDATA unit codes are AADE''s data and Go holds its own editable list.';

COMMENT ON COLUMN unit_of_measure.mydata_code IS
    'AADE''s unit code. NULL where no mapping is known — phase 7 must refuse to transmit such a '
    'line rather than composing a code.';

-- The eight units the step 5 enum carried, with the same fractional-quantity answers.
INSERT INTO unit_of_measure (code, name, fractional_quantity_allowed) VALUES
    ('PIECE',      'Piece',      false),
    ('SET',        'Set',        false),
    ('PACK',       'Pack',       false),
    -- The unit most green and roasted coffee is bought and sold in.
    ('KILOGRAM',   'Kilogram',   true),
    ('GRAM',       'Gram',       true),
    ('LITRE',      'Litre',      true),
    ('MILLILITRE', 'Millilitre', true),
    ('METRE',      'Metre',      true);

-- ---------------------------------------------------------------------------------------
-- product.unit_of_measure becomes a reference
-- ---------------------------------------------------------------------------------------
-- Done as add / backfill / constrain / drop rather than drop-and-add. The product table is
-- expected to be empty everywhere — V9 seeded nothing and no adapter exists yet — but a developer
-- may have created products locally to look at them, and a migration that silently needs an empty
-- table is a migration that fails on exactly the machine where someone has been working.

ALTER TABLE product
    ADD COLUMN unit_of_measure_id bigint;

UPDATE product p
SET    unit_of_measure_id = u.id
FROM   unit_of_measure u
WHERE  u.code = p.unit_of_measure;

-- Fails loudly if any product's old enum value has no matching row, rather than leaving a product
-- with no unit. There is no correct default here: a product's unit is not guessable.
ALTER TABLE product
    ALTER COLUMN unit_of_measure_id SET NOT NULL;

ALTER TABLE product
    DROP CONSTRAINT product_unit_of_measure_known;

ALTER TABLE product
    DROP COLUMN unit_of_measure;

ALTER TABLE product
    ADD CONSTRAINT product_unit_of_measure_fk
        FOREIGN KEY (unit_of_measure_id) REFERENCES unit_of_measure (id);

CREATE INDEX product_unit_of_measure_idx ON product (unit_of_measure_id);
