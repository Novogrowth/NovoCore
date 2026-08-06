-- =========================================================================================
-- V37 — PAYMENT METHODS BECOME A BUSINESS LIST THAT REFERENCES AN AADE CODIFICATION (R4)
-- =========================================================================================
--
-- ⚠️ THIS IS A REQUIREMENT CORRECTION, NOT A DEFECT FIX, AND THE DISTINCTION IS THE POINT.
-- V35 built `payment_method` as a SEED-ONLY STATUTORY LIST — one row per SettlementMethod enum
-- value, no create, ever. It does exactly what R2b's rows asked for. What those rows asked for
-- is the wrong model.
--
-- It is R1a's two-layer correction repeating ONE ENTITY OVER. What CLAUDE.md §5 says about
-- `aade_invoice_type` versus `sales_document_type` is the same sentence about payment methods:
--
--     the STATUTORY CODIFICATION is AADE's and we may not author a row in it;
--     the BUSINESS LIST is the owner's and he authors every row of it;
--     the business row REFERENCES the codification, and the reference is NULLABLE.
--
-- ⭐ THE TELL, recorded because it is what generalises: a Java enum standing in for a list the
-- business owns. `SettlementMethod` supplied the myDATA codes, and a user-created row CANNOT
-- INHERIT A CODE FROM AN ENUM IT IS NOT A MEMBER OF. That single sentence is why the code moves
-- onto a row, why the enum is deleted, and why the sales invoice request contract changes.
--
-- -----------------------------------------------------------------------------------------
-- WHY THE OWNER NEEDS THIS, IN HIS OWN EXAMPLE
-- -----------------------------------------------------------------------------------------
-- TWO POS TERMINALS CAN SHARE AADE CODE 7 AND LAND IN DIFFERENT BANK ACCOUNTS, AND THE AADE
-- ARTICLE CANNOT TELL YOU WHICH. That is the whole argument for `account_id`: the codification
-- says what the document DECLARES to the tax authority, and the account says where the money
-- WENT. They are different questions and only one of them has a statutory answer.
--
-- =========================================================================================


-- =========================================================================================
-- 1. THE STATUTORY LAYER — annex 8.12, and we may not author a row in it
-- =========================================================================================
--
-- ⚠️ READ THIS BEFORE ADDING OR CHANGING A ROW HERE. The codes below did NOT come from a text
-- dump, and they could not have come from the XSD either — which is a genuine exception to the
-- rule CLAUDE.md states, and the reason it is written out here rather than referenced.
--
-- `paymentMethods-v2.0.1.xsd` defines NO code list. The type lives in InvoicesDoc-v2.0.1.xsd and
-- it is a RANGE, not an enumeration:
--
--     <xs:element name="type">                        <!-- Τύπος Πληρωμής -->
--       <xs:simpleType><xs:restriction base="xs:int">
--         <xs:minInclusive value="1"/><xs:maxInclusive value="8"/>
--       </xs:restriction></xs:simpleType>
--     </xs:element>
--
-- It says how many codes exist and nothing about what any of them means. So "take the codes from
-- the XSD, the descriptions from the annex" — the standing rule, which exists because
-- `pdftotext -layout` silently drifts the two columns of these annex tables apart — HAS NO SAFE
-- SIDE HERE. Both halves can only come from annex 8.12.
--
-- ✅ HOW THEY WERE OBTAINED, 2026-08-06: PDF page 105 (document page 104) of
-- `myDATA API Documentation v2.0.1_official_erp.pdf` rendered at 200 dpi and READ BY EYE. They
-- agree character for character with `SettlementMethod`'s javadoc, which recorded the same eight
-- from an INDEPENDENT rasterised read in R1a — two readings of the artefact agreeing, which is
-- the closest thing to two sources this annex admits of. Full note in docs/aade/v2.0.1/README.md.
-- =========================================================================================

CREATE TABLE aade_payment_method (
    id          bigserial    PRIMARY KEY,

    -- AADE's own code. An INTEGER and not text, unlike `aade_invoice_type.code`: invoice types
    -- carry '13.30', which a numeric column cannot distinguish from '13.3'. Payment-method codes
    -- are plain integers and the XSD says so (`xs:int`), so text here would invite '03'.
    code        integer      NOT NULL,

    -- The Greek description from annex 8.12, read visually. Editable through the codification
    -- contract's `describe`, because a description is a LABEL and the code is the IDENTITY. This
    -- migration stays the record of what AADE published — a future spec-version diff compares
    -- against THIS, never against the live row.
    description varchar(200) NOT NULL,

    active      boolean      NOT NULL DEFAULT true,

    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  varchar(100) NOT NULL DEFAULT 'system',
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT aade_payment_method_code_unique UNIQUE (code),
    CONSTRAINT aade_payment_method_description_not_blank CHECK (btrim(description) <> ''),

    -- ⚠️ The bound is v2.0.1's, taken from the XSD restriction quoted above rather than invented.
    -- When AADE publishes a ninth code, the migration that seeds it REPLACES this constraint —
    -- which is the point: a code list must never widen itself silently. See CLAUDE.md on the
    -- diff check that alerts a human and never auto-applies.
    CONSTRAINT aade_payment_method_code_in_spec_range CHECK (code BETWEEN 1 AND 8)
);

INSERT INTO aade_payment_method (code, description) VALUES
    (1, 'Επαγ. Λογαριασμός Πληρωμών Ημεδαπής'),
    (2, 'Επαγ. Λογαριασμός Πληρωμών Αλλοδαπής'),
    (3, 'Μετρητά'),
    (4, 'Επιταγή'),
    (5, 'Επί Πιστώσει'),
    (6, 'Web Banking'),
    (7, 'POS / e-POS'),
    (8, 'Άμεσες Πληρωμές IRIS');

-- The count is the cheapest half of "the seed matches the artefact". The other half — that the
-- DESCRIPTIONS are right — no SQL can check, which is why the provenance is written above at
-- length instead.
DO $$
DECLARE seeded integer;
BEGIN
    SELECT count(*) INTO seeded FROM aade_payment_method;
    IF seeded <> 8 THEN
        RAISE EXCEPTION 'aade_payment_method must seed exactly annex 8.12''s eight codes, found %',
            seeded;
    END IF;
END $$;

COMMENT ON TABLE aade_payment_method IS
    'Annex 8.12 of the myDATA v2.0.1 ERP specification. A StatutoryCodification: AADE authors the '
    'rows, Flyway writes them, and there is no create path on any installation, ever. ⚠️ Its codes '
    'are the ONE annex the XSDs do not carry as an enumeration — see this migration''s header.';
COMMENT ON COLUMN aade_payment_method.active IS
    '⚠️ Means "AADE still publishes this code", NEVER "this business uses it". The second question '
    'belongs to payment_method.active, one table over. Conflating the two is what made the earlier '
    'document-type model unbuildable.';


-- =========================================================================================
-- 2. THE BUSINESS LAYER — the owner's own list, and it SHIPS EMPTY
-- =========================================================================================
--
-- DROP and recreate rather than eight ALTERs, and that is safe rather than casual: measured on
-- 2026-08-06, NOTHING IN THE DATABASE REFERENCES `payment_method` — zero foreign keys point at
-- it. `sales_invoice` carried the enum NAME in a varchar with a CHECK, not a reference. So the
-- table has no dependents to break, V35 stays the historical record of the shape it replaced,
-- and the new shape is legible in one statement instead of reconstructed from a diff.
--
-- ⚠️ IT SHIPS EMPTY, and that is the requirement rather than an omission. V35's eight rows
-- carried abbreviations — ΜΕΤΡ, ΚΑΡΤ, ΤΡΑΠ, ΕΠΙΤ, ΑΝΤΙΚ, SKRZ, PPAL, STRP — that were INVENTED
-- HERE and never chosen by the owner. An empty list REMOVES that problem rather than making the
-- fabrications editable, which is the same argument that shipped R1a's six tables empty.
-- =========================================================================================

DROP TABLE payment_method;

CREATE TABLE payment_method (
    -- ⚠️ A SURROGATE ID, where V35 deliberately had none. V35's argument was sound and its
    -- premise is gone: the enum constant WAS the identity because the rows were a fixed set. A
    -- user-created row is a member of no enum, so it needs an id of its own.
    id                     bigserial    PRIMARY KEY,

    abbreviation           varchar(20)  NOT NULL,
    description            varchar(120) NOT NULL,

    -- ⚠️ NULLABLE, AND ITS NULLABILITY IS A DECISION (R4 G.2).
    -- Three of the eight methods this list replaces had NO myDATA code at all — a courier's cash
    -- on delivery, PayPal and Stripe map to nothing in an eight-value statutory list. That is a
    -- finding about AADE's list, not a gap in our knowledge of it, and it is R1a's finding again:
    -- six of the owner's nineteen document types carry no AADE invoice type either.
    -- A method with no article is legitimate. What it is NOT is transmittable — step 29 must
    -- REFUSE to transmit a document settled by one rather than substituting a plausible code.
    aade_payment_method_id bigint       REFERENCES aade_payment_method (id),

    -- ⚠️ NULLABLE, AND ITS PRESENCE CARRIES A MEANING (R4 A.5/A.6).
    -- The account this method settles INTO: the cash box, a bank account, a partner clearing
    -- account. Present ⇒ the money is already there ⇒ the invoice is BORN SETTLED. Absent ⇒ the
    -- invoice debits Accounts receivable and stays an open item until a Receipt allocates.
    --
    -- ⭐ There is deliberately NO `settles_immediately` COLUMN. It is not a second fact: a method
    -- settles immediately BECAUSE there is an account the money is already in, which is what
    -- SettlementMethod's own javadoc said before this table existed. Two columns would be one
    -- fact said twice and able to contradict itself.
    --
    -- ⚠️ @ConditionallyMandatory ON THIS FIELD WAS CONSIDERED AND REJECTED — do not add it. The
    -- condition would be "required iff the method settles immediately", and since settling
    -- immediately MEANS having an account, that tests the field against itself and can never
    -- fail. A guard that cannot fire reads as enforcement and is decoration.
    account_id             bigint       REFERENCES account (id),

    active                 boolean      NOT NULL DEFAULT true,

    -- Ordering only. Same rules as V34; a text sort puts 1000 before 900.
    sort_code              integer      NOT NULL,

    created_at             timestamptz  NOT NULL DEFAULT now(),
    created_by             varchar(100) NOT NULL DEFAULT 'system',
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    updated_by             varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT payment_method_abbreviation_not_blank CHECK (btrim(abbreviation) <> ''),
    CONSTRAINT payment_method_description_not_blank  CHECK (btrim(description) <> ''),
    CONSTRAINT payment_method_abbreviation_unique    UNIQUE (abbreviation),
    CONSTRAINT payment_method_sort_code_unique       UNIQUE (sort_code)
);

COMMENT ON TABLE payment_method IS
    'The business''s own payment methods. ⚠️ USER-AUTHORED and SHIPS EMPTY — full CRUD, unlike '
    'V35''s seed-only shape. References aade_payment_method for what a document declares, and '
    'account for where the money went; both references are nullable and for different reasons.';
COMMENT ON COLUMN payment_method.account_id IS
    '⚠️ Presence IS "settles immediately" — there is no separate flag, deliberately. Absent means '
    'the invoice debits Accounts receivable and stays open. See this migration''s header.';
COMMENT ON COLUMN payment_method.aade_payment_method_id IS
    '⚠️ Nullable: a method may legitimately have no statutory article (R4 G.2). A document settled '
    'by one must be REFUSED at transmission (step 29), never sent under a substituted code.';
COMMENT ON COLUMN payment_method.active IS
    '⚠️ Refused on the RECORDING path only. Deactivating never breaks documents already settled by '
    'this method — setting is refused, holding is not.';


-- =========================================================================================
-- 3. THE SALES INVOICE STOPS NAMING AN ENUM
-- =========================================================================================
--
-- ⚠️ THE CHECK CONSTRAINT CANNOT SURVIVE, AND THIS IS THE HEART OF THE MIGRATION.
-- `sales_invoice_settlement_method_known` listed the eight enum names AS STRING LITERALS. Every
-- payment method the owner creates from tomorrow is a name that is not in that list, so the
-- constraint would refuse every single one. It is not "tidied away" — it is the enforcement that
-- was correct for a fixed set and is wrong for an authored one, replaced by a foreign key, which
-- says the same thing and keeps saying it as rows are added.
-- =========================================================================================

ALTER TABLE sales_invoice DROP CONSTRAINT sales_invoice_settlement_method_known;

-- ⚠️ NULLABLE, AND THE SERVICE — NOT THE COLUMN — IS WHAT REQUIRES IT.
-- A deliberate departure from "a constraint the database holds cannot be bypassed", taken for
-- exactly the reason V33 took it for `series_id` and recorded at the column there too:
-- NOT NULL would mean BACKFILLING EVERY EXISTING INVOICE WITH A PAYMENT METHOD NOBODY AUTHORED,
-- which is the fabrication this whole step exists to prevent. There is no honest value to write.
--
-- The dev database's legacy rows are dealt with by RESETTING the trading data
-- (docker/reset-trading-data.sql), not by inventing rows here. Whether migrated history carries
-- a payment method is step 24's question, exactly as it is for the series.
ALTER TABLE sales_invoice ADD COLUMN payment_method_id bigint REFERENCES payment_method (id);

-- ⚠️ DROPPED rather than kept as a dead column. The enum it encoded no longer exists after this
-- step, so the value would be an orphan string naming nothing — not history, just a token whose
-- vocabulary has been deleted. Keeping it would also give a future reader two settlement columns
-- and no way to tell which one is live.
ALTER TABLE sales_invoice DROP COLUMN settlement_method;

COMMENT ON COLUMN sales_invoice.payment_method_id IS
    '⚠️ NULLABLE in the schema and REQUIRED by SalesInvoiceServiceImpl, for the same reason '
    'series_id is (V33): backfilling pre-R4 invoices would invent a payment method nobody '
    'authored. Step 24 decides whether migrated history carries one.';

-- ⚠️ credit_note is deliberately untouched and has no column of its own. A credit note takes its
-- method from the invoice it refunds — holding, not setting — which is also why the active guard
-- was never applied to it.
