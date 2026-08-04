-- =========================================================================================
-- V35 — PAYMENT METHODS GET A ROW EACH (R2b §4)
-- =========================================================================================
--
-- ⚠️ THIS EXISTS BECAUSE OF A SCOPING ERROR, NOT AN IMPLEMENTATION GAP, AND THE DISTINCTION
-- IS THE POINT.
--
-- The owner's original nine-table specification included:
--
--     Τρόποι πληρωμής [ID, abbreviation, description, active/inactive, myDATA code]
--
-- When it was established that SettlementMethod is a Java enum, that was carried into R2's scope
-- as "it lives on an enum, so there is nothing to edit". Delivery methods — a near-identical row
-- in the same specification — got a full CRUD screen, and payment methods got nothing at all.
--
-- The enum decision was RIGHT FOR THE WRONG SCOPE. Adding a payment method genuinely does need
-- code: each value carries an AccountSystemKey, settlesImmediately and subjectToCashLimit, and no
-- form can supply those. So NO CREATE is correct and stays. What it never justified was no
-- SCREEN — abbreviation, description and active are none of them behaviour, and none of them
-- existed anywhere.
--
-- -----------------------------------------------------------------------------------------
-- ⚠️ THE myDATA CODE IS NOT HERE, AND ITS ABSENCE IS THE DESIGN
-- -----------------------------------------------------------------------------------------
-- The brief for this table said to carry the myDATA code as well. It is NOT carried, because the
-- premise was wrong: the codes have been on the enum since it was written —
--
--     CASH → 3   BANK_DEPOSIT → 1   CARD_POS → 7   ON_ACCOUNT → 5   SKROUTZ → 5
--     ACS_COD, PAYPAL, STRIPE → null, and they stay open rather than being invented
--
-- So "none of these fields exists on an enum" was true of abbreviation, description and active,
-- and false of the code. Storing it here would create a second record of one thing — precisely
-- what PaymentMethodIT's drift test exists to prevent — and would then need a drift test of its
-- own. The view reads it from the enum instead. Nothing to disagree.
--
-- -----------------------------------------------------------------------------------------
-- ONE ROW PER ENUM VALUE, AND A TEST HOLDS IT IN BOTH DIRECTIONS
-- -----------------------------------------------------------------------------------------
-- `method` is the enum constant and the primary key: no surrogate id, because the enum name IS
-- the identity and a second identifier would be a second thing to keep in step. PaymentMethodIT
-- asserts every enum value has exactly one row AND every row a value, failing either way —
-- without that this is two records of one thing, which is the failure this table is most likely
-- to produce.
--
-- ⚠️ Adding a value to SettlementMethod therefore requires a migration adding its row. That is
-- deliberate friction: adding one already requires choosing an AccountSystemKey and two behaviour
-- flags, so it is a code change either way, and the build will say so.
--
-- 📌 Worth knowing, because the owner's mental model includes them: "Cheque" and "Foreign bank
-- account" are in his Prosvasis Go list and are NOT values of this enum. Adding either needs an
-- AccountSystemKey and the two flags — which is the no-create argument stated concretely rather
-- than in the abstract.
--
-- sort_code: ordering only, exactly as V34. Eight options in enum-declaration order is not a
-- sensible list for somebody picking a payment method while recording a sale.
-- =========================================================================================

CREATE TABLE payment_method (
    -- The enum constant. The identity, and there is deliberately no surrogate id.
    method       varchar(40)  NOT NULL PRIMARY KEY,

    -- The three fields the specification asked for that do not exist on the enum.
    abbreviation varchar(20)  NOT NULL,
    description  varchar(120) NOT NULL,
    active       boolean      NOT NULL DEFAULT true,

    -- Ordering only. See V34 for the full argument; the same rules apply.
    sort_code    integer      NOT NULL,

    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   varchar(100) NOT NULL DEFAULT 'system',
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT payment_method_abbreviation_not_blank CHECK (btrim(abbreviation) <> ''),
    CONSTRAINT payment_method_description_not_blank  CHECK (btrim(description) <> ''),
    CONSTRAINT payment_method_abbreviation_unique    UNIQUE (abbreviation),
    CONSTRAINT payment_method_sort_code_unique       UNIQUE (sort_code)
);

-- ⚠️ Descriptions are the business's own words and are editable. The abbreviations are short
-- handles for a picker, not codes with meaning elsewhere.
INSERT INTO payment_method (method, abbreviation, description, sort_code) VALUES
    ('CASH',         'ΜΕΤΡ', 'Μετρητά',                        10),
    ('CARD_POS',     'ΚΑΡΤ', 'Κάρτα (POS)',                    20),
    ('BANK_DEPOSIT', 'ΤΡΑΠ', 'Κατάθεση σε τραπεζικό λογαριασμό', 30),
    ('ON_ACCOUNT',   'ΕΠΙΤ', 'Επί πιστώσει',                   40),
    ('ACS_COD',      'ΑΝΤΙΚ', 'Αντικαταβολή ACS',              50),
    ('SKROUTZ',      'SKRZ', 'Skroutz',                         60),
    ('PAYPAL',       'PPAL', 'PayPal',                          70),
    ('STRIPE',       'STRP', 'Stripe',                          80);

-- The seed must match the enum exactly. A count is the cheapest half of that check; the other
-- half — that the NAMES match, in both directions — is PaymentMethodIT's, because SQL cannot see
-- a Java enum.
DO $$
DECLARE seeded integer;
BEGIN
    SELECT count(*) INTO seeded FROM payment_method;
    IF seeded <> 8 THEN
        RAISE EXCEPTION
            'payment_method must have exactly one row per SettlementMethod value (8), found %',
            seeded;
    END IF;
END $$;

COMMENT ON TABLE payment_method IS
    'One row per SettlementMethod value, carrying the presentation fields the enum has no room '
    'for. ⚠️ Behaviour (settlement account, settles-immediately, cash-limit) stays on the enum, '
    'and so does the myDATA payment code — it is NOT duplicated here. See V35.';
COMMENT ON COLUMN payment_method.method IS
    'The SettlementMethod constant. PaymentMethodIT asserts the table and the enum never drift, '
    'in both directions.';
COMMENT ON COLUMN payment_method.active IS
    '⚠️ Refused on the RECORDING path: SalesInvoiceServiceImpl refuses to record an invoice '
    'settled by a deactivated method. Deactivating never breaks documents already settled by it — '
    'setting is refused, holding is not. See V35 and R2b §4.';
