-- =========================================================================================
-- V31 — DOCUMENT REFERENCE DATA (R1a). TWO LAYERS, AND THE DISTINCTION IS WHO AUTHORS A ROW
-- =========================================================================================
--
-- Governed by `CLAUDE.md` §"The document model", item 5, and by `docs/PROGRESS.md` under
-- "Why the model changed". Read one of those before changing anything here.
--
-- -----------------------------------------------------------------------------------------
-- ⚠️ THE MODEL THIS REPLACES, AND WHY IT WAS WRONG
-- -----------------------------------------------------------------------------------------
-- An earlier design had ONE layer: two seed-only tables, `sales_document_type` and
-- `purchase_document_type`, whose rows WERE the AADE codes — 34 sales codes and 15 purchase
-- codes split out of the XSD's single 55-value enumeration by annex 8.1's group headings.
--
-- The owner's actual Prosvasis Go configuration disproved it. He runs 15 sales types and 4
-- purchase types, and:
--
--   * Go's type numbers (7001, 7071, 2062, ...) are GO'S INTERNAL IDS, not AADE codes. Under
--     architecture rule 2 they belong in the Go adapter's mapping table and must never touch a
--     core entity. The old model had nowhere to put them and no way to notice they were not
--     AADE's.
--   * SIX of the nineteen have NO AADE INVOICE TYPE AT ALL — Προσφορά, Δελτίο Αποστολής,
--     Δελτίο ποσοτικής παραλαβής, Παραγγελία, Δελτίο Παραλαβής, ΔΑ Αποστολής Σε Προμηθευτή.
--     They are operational documents, not tax documents. A model in which the AADE code IS the
--     row cannot represent a document that has none.
--   * More types and series will be needed in future, so both must be USER-CREATABLE.
--
-- So there are two layers, and only the first is AADE's:
--
--   LAYER 1  aade_invoice_type          all 55 XSD values, group as a COLUMN, SEED-ONLY
--   LAYER 2  sales_document_type        the business's own list, USER-CREATABLE, ships EMPTY
--            purchase_document_type     ditto
--
-- ⭐ The six 17.x "Εγγραφές Οντότητας" codes were previously a blocker — they are the entity's
-- own journal entries and fit NEITHER business table. That blocker dissolved rather than being
-- decided: with one codification table they are simply six rows carrying ENTITY_ADJUSTING.
-- 28 + 6 + 6 + 9 + 6 = 55 still holds, and the DO block below asserts it.
--
-- -----------------------------------------------------------------------------------------
-- ⚠️ WHERE EVERY VALUE IN THE SEED CAME FROM
-- -----------------------------------------------------------------------------------------
-- CODES come from `docs/aade/v2.0.1/v2.0.1 XSDs/SimpleTypes-v2.0.1.xsd`, the InvoiceType
-- simpleType — 55 flat <xs:enumeration value="…"/> elements with no layout to lose.
--
-- GREEK DESCRIPTIONS AND GROUP HEADINGS were read VISUALLY from pages 89–93 of
-- `myDATA API Documentation v2.0.1_official_erp.pdf`, rasterised at 170 dpi with PyMuPDF.
--
-- ⚠️ NOT from `pdftotext`. That extractor silently drifts the code column and the description
-- column apart on these tables — annexes 8.2, 8.7, 8.8 and 8.13 are all confirmed affected —
-- producing clean, plausible, off-by-one pairs. It is a named anti-pattern in `CLAUDE.md`
-- ("the extractor answered, and it was not answering the question"). A seed written from a text
-- dump would transmit the wrong document type to the tax authority under a code list that had
-- been "read from the official artefact".
--
-- -----------------------------------------------------------------------------------------
-- ⚠️ CODES 4 AND 12 HAVE NO DESCRIPTION IN ANNEX 8.1. THIS IS NOT AN OMISSION HERE
-- -----------------------------------------------------------------------------------------
-- Both appear in the XSD enumeration, so both are legal values. In the annex their description
-- cell is EMPTY, and the only text AADE gives them is the group label in the left-hand column:
-- "Για Μελλοντική Χρήση" — For Future Use. That label is what is seeded, verbatim, and it is
-- read from the artefact rather than invented. Both are seeded ACTIVE like every other row,
-- because activation here means "AADE defines it", not "we use it" — see below.
--
-- -----------------------------------------------------------------------------------------
-- WHAT `active` MEANS ON THIS TABLE, AND WHAT IT DOES NOT
-- -----------------------------------------------------------------------------------------
-- All 55 rows are seeded active. `active` on a statutory codification means "AADE still
-- publishes this code", not "this business issues it" — the second question is what layer 2
-- answers, and conflating the two is what made the previous model unbuildable: it asked the
-- seed to know which types the business uses, a fact that lives nowhere in these artefacts.
-- =========================================================================================


-- -----------------------------------------------------------------------------------------
-- LAYER 1 — aade_invoice_type
-- -----------------------------------------------------------------------------------------

CREATE TABLE aade_invoice_type (
    id            bigserial    PRIMARY KEY,

    -- AADE's own code, verbatim from the XSD: '1.1', '13.30', '4'. Text and not numeric —
    -- '13.30' and '13.3' are different codes and a numeric column cannot hold both.
    code          varchar(10)  NOT NULL,

    -- The Greek description from annex 8.1, read visually. Editable through the codification
    -- contract's `describe`, because a description is a label; the code is the identity. This
    -- migration remains the record of what AADE published, which is what a future version diff
    -- compares against — never the live row.
    description   varchar(200) NOT NULL,

    -- Annex 8.1's group heading, as a column. This is what dissolved the third-table blocker.
    invoice_group varchar(30)  NOT NULL,

    active        boolean      NOT NULL DEFAULT true,

    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    varchar(100) NOT NULL DEFAULT 'system',
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT aade_invoice_type_code_unique UNIQUE (code),

    CONSTRAINT aade_invoice_type_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT aade_invoice_type_description_not_blank CHECK (btrim(description) <> ''),

    -- The five groups of annex 8.1, named for what they mean rather than transliterated:
    --   ISSUER_MATCHED      Αντικριζόμενα Παραστατικά Εκδότη      — we issue, matched
    --   ISSUER_UNMATCHED    Μη Αντικριζόμενα Παραστατικά Εκδότη   — we issue, retail
    --   RECIPIENT_UNMATCHED Μη Αντικριζόμενα Παραστατικά Λήπτη    — we receive, retail
    --   RECIPIENT_MATCHED   Αντικριζόμενα Παραστατικά Λήπτη       — we receive, matched
    --   ENTITY_ADJUSTING    Εγγραφές Οντότητας                    — our own adjusting entries
    CONSTRAINT aade_invoice_type_group_known CHECK (invoice_group IN (
        'ISSUER_MATCHED', 'ISSUER_UNMATCHED',
        'RECIPIENT_UNMATCHED', 'RECIPIENT_MATCHED',
        'ENTITY_ADJUSTING'))
);

COMMENT ON TABLE aade_invoice_type IS
    'The AADE myDATA InvoiceType codification, v2.0.1 — all 55 values. SEED-ONLY: Flyway owns '
    'row authorship, and the statutory-codification contract (activate, deactivate, describe; '
    'no create) applies to this table and to vat_exemption_reason, and to nothing else. The '
    'business''s own document lists are sales_document_type / purchase_document_type.';

COMMENT ON COLUMN aade_invoice_type.invoice_group IS
    'Annex 8.1''s group heading. Decides whether a code is one we issue or one we receive, '
    'which is where Novocore''s sales/purchase split comes from — the XSD has ONE enumeration '
    'covering both, and the split is ours rather than AADE''s.';

COMMENT ON COLUMN aade_invoice_type.active IS
    'Whether AADE still publishes this code. NOT whether this business issues it — that is '
    'what sales_document_type / purchase_document_type answer.';


-- The 55 rows. Order is the XSD's enumeration order, which is also annex 8.1's reading order.
INSERT INTO aade_invoice_type (code, description, invoice_group) VALUES
    -- Αντικριζόμενα Παραστατικά Εκδότη ημεδαπής / αλλοδαπής — 28 codes
    ('1.1',   'Τιμολόγιο Πώλησης',                                                    'ISSUER_MATCHED'),
    ('1.2',   'Τιμολόγιο Πώλησης / Ενδοκοινοτικές Παραδόσεις',                        'ISSUER_MATCHED'),
    ('1.3',   'Τιμολόγιο Πώλησης / Παραδόσεις Τρίτων Χωρών',                          'ISSUER_MATCHED'),
    ('1.4',   'Τιμολόγιο Πώλησης / Πώληση για Λογαριασμό Τρίτων',                     'ISSUER_MATCHED'),
    ('1.5',   'Τιμολόγιο Πώλησης / Εκκαθάριση Πωλήσεων Τρίτων - Αμοιβή από Πωλήσεις Τρίτων',
                                                                                      'ISSUER_MATCHED'),
    ('1.6',   'Τιμολόγιο Πώλησης / Συμπληρωματικό Παραστατικό',                       'ISSUER_MATCHED'),
    ('2.1',   'Τιμολόγιο Παροχής',                                                    'ISSUER_MATCHED'),
    ('2.2',   'Τιμολόγιο Παροχής / Ενδοκοινοτική Παροχή Υπηρεσιών',                   'ISSUER_MATCHED'),
    ('2.3',   'Τιμολόγιο Παροχής / Παροχή Υπηρεσιών σε λήπτη Τρίτης Χώρας',           'ISSUER_MATCHED'),
    ('2.4',   'Τιμολόγιο Παροχής / Συμπληρωματικό Παραστατικό',                       'ISSUER_MATCHED'),
    ('3.1',   'Τίτλος Κτήσης (μη υπόχρεος Εκδότης)',                                  'ISSUER_MATCHED'),
    ('3.2',   'Τίτλος Κτήσης (άρνηση έκδοσης από υπόχρεο Εκδότη)',                    'ISSUER_MATCHED'),
    -- No description cell in annex 8.1; the group label is the only text AADE gives it.
    ('4',     'Για Μελλοντική Χρήση',                                                 'ISSUER_MATCHED'),
    ('5.1',   'Πιστωτικό Τιμολόγιο / Συσχετιζόμενο',                                  'ISSUER_MATCHED'),
    ('5.2',   'Πιστωτικό Τιμολόγιο / Μη Συσχετιζόμενο',                               'ISSUER_MATCHED'),
    -- 6.1 and 6.2 sit under one sub-heading, "Στοιχείο Αυτοπαράδοσης - Ιδιοχρησιμοποίησης",
    -- as two distinct codes. Both are issuer-side, so both are sales document types. R3.
    ('6.1',   'Στοιχείο Αυτοπαράδοσης',                                               'ISSUER_MATCHED'),
    ('6.2',   'Στοιχείο Ιδιοχρησιμοποίησης',                                          'ISSUER_MATCHED'),
    ('7.1',   'Συμβόλαιο - Έσοδο',                                                    'ISSUER_MATCHED'),
    ('8.1',   'Ενοίκια - Έσοδο',                                                      'ISSUER_MATCHED'),
    ('8.2',   'Τέλος ανθεκτικότητας κλιματικής κρίσης',                               'ISSUER_MATCHED'),
    -- 8.3 is absent from the XSD enumeration and from the annex. Not an omission here.
    ('8.4',   'Απόδειξη Είσπραξης POS',                                               'ISSUER_MATCHED'),
    ('8.5',   'Απόδειξη Επιστροφής POS',                                              'ISSUER_MATCHED'),
    ('8.6',   'Δελτίο Παραγγελίας Εστίασης',                                          'ISSUER_MATCHED'),
    ('9.1',   'Δελτίο Αποστολής Συσχετιζόμενο',                                       'ISSUER_MATCHED'),
    ('9.2',   'Συγκεντρωτικό Δελτίο Αποστολής',                                       'ISSUER_MATCHED'),
    ('9.3',   'Δελτίο Αποστολής',                                                     'ISSUER_MATCHED'),
    ('10.1',  'Δελτίο Ποσοτικής Παραλαβής Συσχετιζόμενο',                             'ISSUER_MATCHED'),
    ('10.2',  'Δελτίο Ποσοτικής Παραλαβής Μη Συσχετιζόμενο',                          'ISSUER_MATCHED'),

    -- Μη Αντικριζόμενα Παραστατικά Εκδότη → Παραστατικά Λιανικής — 6 codes
    ('11.1',  'ΑΛΠ',                                                                  'ISSUER_UNMATCHED'),
    ('11.2',  'ΑΠΥ',                                                                  'ISSUER_UNMATCHED'),
    ('11.3',  'Απλοποιημένο Τιμολόγιο',                                               'ISSUER_UNMATCHED'),
    ('11.4',  'Πιστωτικό Στοιχ. Λιανικής',                                            'ISSUER_UNMATCHED'),
    ('11.5',  'Απόδειξη Λιανικής Πώλησης για Λογ/σμό Τρίτων',                         'ISSUER_UNMATCHED'),
    -- No description cell in annex 8.1, exactly as for code 4.
    ('12',    'Για Μελλοντική Χρήση',                                                 'ISSUER_UNMATCHED'),

    -- Μη Αντικριζόμενα Παραστατικά Λήπτη → Λήψη Παραστατικών Λιανικής — 6 codes
    ('13.1',  'Έξοδα - Αγορές Λιανικών Συναλλαγών ημεδαπής / αλλοδαπής',              'RECIPIENT_UNMATCHED'),
    ('13.2',  'Παροχή Λιανικών Συναλλαγών ημεδαπής / αλλοδαπής',                      'RECIPIENT_UNMATCHED'),
    ('13.3',  'Κοινόχρηστα',                                                          'RECIPIENT_UNMATCHED'),
    ('13.4',  'Συνδρομές',                                                            'RECIPIENT_UNMATCHED'),
    ('13.30', 'Παραστατικά Οντότητας ως Αναγράφονται από την ίδια (Δυναμικό)',        'RECIPIENT_UNMATCHED'),
    ('13.31', 'Πιστωτικό Στοιχ. Λιανικής ημεδαπής / αλλοδαπής',                       'RECIPIENT_UNMATCHED'),

    -- Αντικριζόμενα Παραστατικά Λήπτη ημεδαπής / αλλοδαπής — 9 codes
    ('14.1',  'Τιμολόγιο / Ενδοκοινοτικές Αποκτήσεις',                                'RECIPIENT_MATCHED'),
    ('14.2',  'Τιμολόγιο / Αποκτήσεις Τρίτων Χωρών',                                  'RECIPIENT_MATCHED'),
    ('14.3',  'Τιμολόγιο / Ενδοκοινοτική Λήψη Υπηρεσιών',                             'RECIPIENT_MATCHED'),
    ('14.4',  'Τιμολόγιο / Λήψη Υπηρεσιών Τρίτων Χωρών',                              'RECIPIENT_MATCHED'),
    ('14.5',  'ΕΦΚΑ και λοιποί Ασφαλιστικοί Οργανισμοί',                              'RECIPIENT_MATCHED'),
    ('14.30', 'Παραστατικά Οντότητας ως Αναγράφονται από την ίδια (Δυναμικό)',        'RECIPIENT_MATCHED'),
    ('14.31', 'Πιστωτικό ημεδαπής / αλλοδαπής',                                       'RECIPIENT_MATCHED'),
    ('15.1',  'Συμβόλαιο - Έξοδο',                                                    'RECIPIENT_MATCHED'),
    ('16.1',  'Ενοίκιο Έξοδο',                                                        'RECIPIENT_MATCHED'),

    -- Εγγραφές Τακτοποίησης Εσόδων-Εξόδων → Εγγραφές Οντότητας — 6 codes.
    -- The entity's own journal entries. These are the six that had nowhere to live under the
    -- previous two-table model, and they are ordinary rows here.
    ('17.1',  'Μισθοδοσία',                                                           'ENTITY_ADJUSTING'),
    ('17.2',  'Αποσβέσεις',                                                           'ENTITY_ADJUSTING'),
    ('17.3',  'Λοιπές Εγγραφές Τακτοποίησης Εσόδων - Λογιστική Βάση',                 'ENTITY_ADJUSTING'),
    ('17.4',  'Λοιπές Εγγραφές Τακτοποίησης Εσόδων - Φορολογική Βάση',                'ENTITY_ADJUSTING'),
    ('17.5',  'Λοιπές Εγγραφές Τακτοποίησης Εξόδων - Λογιστική Βάση',                 'ENTITY_ADJUSTING'),
    ('17.6',  'Λοιπές Εγγραφές Τακτοποίησης Εξόδων - Φορολογική Βάση',                'ENTITY_ADJUSTING');


-- ⭐ THE CROSS-CHECK, AS A MIGRATION-TIME ASSERTION RATHER THAN A COMMENT.
--
-- The XSD gives 55 codes with no groups; annex 8.1 gives groups with no machine-readable code
-- list. Neither source alone can be wrong-checked. The two together can: if every group count
-- below holds AND they sum to the enumeration size, then no code was dropped, none was
-- invented, and none was filed under the wrong heading — because a misfiled code changes two
-- counts at once and a dropped one changes the total.
--
-- `AadeInvoiceTypeIT` asserts the same thing at test time. This is here as well because a
-- migration that seeds a statutory list should not be able to seed a wrong one silently.
DO $$
DECLARE
    total       int;
    issuer_m    int;
    issuer_u    int;
    recipient_u int;
    recipient_m int;
    entity_a    int;
BEGIN
    SELECT count(*) INTO total FROM aade_invoice_type;
    SELECT count(*) INTO issuer_m    FROM aade_invoice_type WHERE invoice_group = 'ISSUER_MATCHED';
    SELECT count(*) INTO issuer_u    FROM aade_invoice_type WHERE invoice_group = 'ISSUER_UNMATCHED';
    SELECT count(*) INTO recipient_u FROM aade_invoice_type WHERE invoice_group = 'RECIPIENT_UNMATCHED';
    SELECT count(*) INTO recipient_m FROM aade_invoice_type WHERE invoice_group = 'RECIPIENT_MATCHED';
    SELECT count(*) INTO entity_a    FROM aade_invoice_type WHERE invoice_group = 'ENTITY_ADJUSTING';

    IF total <> 55 OR issuer_m <> 28 OR issuer_u <> 6
       OR recipient_u <> 6 OR recipient_m <> 9 OR entity_a <> 6 THEN
        RAISE EXCEPTION
            'AADE invoice type seed does not reconcile with annex 8.1. Expected 28/6/6/9/6 = 55, '
            'got %/%/%/%/% = %. Every code comes from SimpleTypes-v2.0.1.xsd and every group '
            'from a rasterised reading of annex 8.1; a mismatch means one of the two was '
            'transcribed wrong.',
            issuer_m, issuer_u, recipient_u, recipient_m, entity_a, total;
    END IF;
END;
$$;


-- -----------------------------------------------------------------------------------------
-- LAYER 2 — the business's own document types. ⚠️ BOTH SHIP EMPTY
-- -----------------------------------------------------------------------------------------
-- The owner creates his nineteen through R2's screens, choosing each AADE type himself. They
-- are deliberately NOT seeded here and their Go→AADE mappings are deliberately NOT inferred:
-- he knows which is which better than an inference does, an inferred mapping would be a guess
-- written into a statutory field, and creating them by hand exercises the CRUD mechanism the
-- moment it exists.
--
-- ⚠️ `affects_stock` AND `transfers_stock` ARE NULLABLE, AND NULL MEANS NOBODY HAS DECIDED.
-- A `false` there is a guess wearing a value's clothes: it reads as a decision that stock does
-- not move, and nothing distinguishes it from a field left alone. The CHECK below is what makes
-- null safe — a type cannot be created active without both answered.

CREATE TABLE sales_document_type (
    id                          bigserial    PRIMARY KEY,

    description                 varchar(120) NOT NULL,

    -- Whether recording a document of this type moves stock at all. ΑΛΠ and ΤΠΔΑ combine sale
    -- and transport, so stock moves; a plain Τιμολόγιο is purely sales and does not reduce it.
    -- ⚠️ READ, not merely recorded — R1b branches SalesInvoiceServiceImpl on it.
    affects_stock               boolean,

    -- Whether the document also transfers stock to the counterparty, as opposed to only
    -- consuming it. Separate from affects_stock because a dispatch document (18b) transfers
    -- without being a sale, and a Τιμολόγιο can be a sale without transferring.
    transfers_stock             boolean,

    -- Whether a document of this type must reach AADE. False for the operational documents —
    -- Προσφορά, Παραγγελία — which are not tax documents at all.
    requires_mydata_transmission boolean     NOT NULL,

    -- ⚠️ NULLABLE, and null is a real answer: six of the owner's nineteen types have no AADE
    -- invoice type. Modelled as absence, never as a sentinel row and never as an "N/A" code —
    -- an N/A row in a statutory codification is an invented AADE code.
    aade_invoice_type_id        bigint,

    active                      boolean      NOT NULL DEFAULT true,

    created_at                  timestamptz  NOT NULL DEFAULT now(),
    created_by                  varchar(100) NOT NULL DEFAULT 'system',
    updated_at                  timestamptz  NOT NULL DEFAULT now(),
    updated_by                  varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT sales_document_type_description_unique UNIQUE (description),
    CONSTRAINT sales_document_type_description_not_blank CHECK (btrim(description) <> ''),

    CONSTRAINT sales_document_type_aade_type_fk
        FOREIGN KEY (aade_invoice_type_id) REFERENCES aade_invoice_type (id),

    -- ⚠️ CHECKED ON CREATION ONLY, in the sense that matters: an ACTIVE type must have both
    -- stock flags decided, and an INACTIVE one need not. Deactivating a type later therefore
    -- never invalidates it, and never invalidates the historical documents recorded under it.
    -- Written as a table CHECK rather than in the service because a constraint the database
    -- holds cannot be bypassed by a second write path.
    CONSTRAINT sales_document_type_active_has_stock_behaviour CHECK (
        active = false OR (affects_stock IS NOT NULL AND transfers_stock IS NOT NULL))
);

CREATE TABLE purchase_document_type (
    id                          bigserial    PRIMARY KEY,

    description                 varchar(120) NOT NULL,

    -- ⚠️ affects_stock IS MEANINGFUL ON THE PURCHASE SIDE. It is not a copy-paste of the sales
    -- column and must not be removed.
    --
    -- The owner's evidence, and it is the clearest justification the column has anywhere:
    -- 2062 ΤΔΑΑ (Τιμολόγιο Δελτίο Αποστολής Αγοράς) is used daily and brings stock in with a
    -- payable behind it. 2041 Δελτίο Παραλαβής is the exception case — a machine sent to a
    -- supplier for service and returned. That is a purchase document bringing stock IN with
    -- NO PAYABLE behind it, which nothing else in the model can express.
    affects_stock               boolean,
    transfers_stock             boolean,
    requires_mydata_transmission boolean     NOT NULL,
    aade_invoice_type_id        bigint,
    active                      boolean      NOT NULL DEFAULT true,

    created_at                  timestamptz  NOT NULL DEFAULT now(),
    created_by                  varchar(100) NOT NULL DEFAULT 'system',
    updated_at                  timestamptz  NOT NULL DEFAULT now(),
    updated_by                  varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT purchase_document_type_description_unique UNIQUE (description),
    CONSTRAINT purchase_document_type_description_not_blank CHECK (btrim(description) <> ''),

    CONSTRAINT purchase_document_type_aade_type_fk
        FOREIGN KEY (aade_invoice_type_id) REFERENCES aade_invoice_type (id),

    CONSTRAINT purchase_document_type_active_has_stock_behaviour CHECK (
        active = false OR (affects_stock IS NOT NULL AND transfers_stock IS NOT NULL))
);

COMMENT ON TABLE sales_document_type IS
    'The business''s own sales document list — USER-CREATABLE, full CRUD. Ships empty; the '
    'owner creates his own. Its nullable FK names an aade_invoice_type where one applies, and '
    'six of his nineteen types have none.';

COMMENT ON TABLE purchase_document_type IS
    'The business''s own purchase document list — USER-CREATABLE, full CRUD. Ships empty.';

COMMENT ON COLUMN sales_document_type.affects_stock IS
    'NULL means nobody has decided. A false would read as a decision that stock does not move '
    'and be indistinguishable from a field left alone.';

COMMENT ON COLUMN purchase_document_type.affects_stock IS
    'NULL means nobody has decided. Meaningful on this side: 2041 Δελτίο Παραλαβής brings '
    'stock in with no payable behind it (a machine returned from service).';

COMMENT ON COLUMN sales_document_type.aade_invoice_type_id IS
    'NULL where the document is operational rather than a tax document — Προσφορά, Δελτίο '
    'Αποστολής, Παραγγελία. Never a sentinel row: an "N/A" code would be an invented AADE code.';

CREATE INDEX sales_document_type_aade_idx ON sales_document_type (aade_invoice_type_id);
CREATE INDEX purchase_document_type_aade_idx ON purchase_document_type (aade_invoice_type_id);


-- -----------------------------------------------------------------------------------------
-- THE SERIES TABLES. ⚠️ BOTH SHIP EMPTY
-- -----------------------------------------------------------------------------------------
-- ⚠️ NOVOCORE RECORDS NUMBERS, IT DOES NOT GENERATE THEM. There is deliberately no sequence,
-- no counter and no allocation-at-commit here. Legal issuance runs through Prosvasis Go today
-- and a certified Πάροχος at step 40, and the document receives its ΜΑΡΚ there. What changes
-- at step 40 is that Novocore begins allocating the series number itself; the machinery for
-- that belongs at step 40 and nowhere earlier.
--
-- "Integers from 1, continuous" is an EXPECTATION about what the issuing system produces, and
-- is recorded as such rather than enforced. Gap detection belongs with step 25, Clearing Checks.

CREATE TABLE sales_document_series (
    id                          bigserial    PRIMARY KEY,

    -- What the operator types and what appears on the document: 'ΑΛΠ', 'ΑΛΠW', 'ΤΠΔΑ'.
    abbreviation                varchar(20)  NOT NULL,
    description                 varchar(120) NOT NULL,

    document_type_id            bigint       NOT NULL,

    -- ⚠️ NULLABLE, AND NULL MEANS "THIS SERIES IS NOT A SALES CHANNEL".
    --
    -- A series is where channel becomes authoritative in R1b: ΑΛΠW is the web series, so an
    -- invoice in it is a web sale BY DEFINITION rather than by someone remembering to tick a
    -- box. Self-supply series (6.1 / 6.2) have no channel at all — the customer is the issuer
    -- and there is no sales channel to attribute.
    --
    -- ⚠️ R1b must NOT relax sales_invoice.channel's NOT NULL to accommodate that. It must
    -- REFUSE to record a sales invoice against a channel-less series, because self-supply has
    -- no posting rule yet (blocked on the accountant) and the constraint is what holds that
    -- question open instead of papering over it. R3 resolves both together.
    --
    -- Values are SalesChannel's. R1 references the existing channel; it does not create one.
    channel                     varchar(30),

    -- Whether a document in this series receives a ΜΑΡΚ. False for operational series, whose
    -- documents never reach AADE at all.
    gets_mark                   boolean      NOT NULL,

    -- Which series a document in this series may be transformed into — correcting a mistake
    -- must produce the correct series or a return document in ONE action, never re-keyed.
    -- ⚠️ Only the allowed-target reference is stored now; the behaviour needs the Go adapter.
    transformable_into_series_id bigint,

    active                      boolean      NOT NULL DEFAULT true,

    created_at                  timestamptz  NOT NULL DEFAULT now(),
    created_by                  varchar(100) NOT NULL DEFAULT 'system',
    updated_at                  timestamptz  NOT NULL DEFAULT now(),
    updated_by                  varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT sales_document_series_abbreviation_unique UNIQUE (abbreviation),
    CONSTRAINT sales_document_series_abbreviation_not_blank CHECK (btrim(abbreviation) <> ''),
    CONSTRAINT sales_document_series_description_not_blank CHECK (btrim(description) <> ''),

    CONSTRAINT sales_document_series_type_fk
        FOREIGN KEY (document_type_id) REFERENCES sales_document_type (id),
    CONSTRAINT sales_document_series_transform_fk
        FOREIGN KEY (transformable_into_series_id) REFERENCES sales_document_series (id),

    -- The same three values sales_invoice.channel is constrained to, since step 9.
    CONSTRAINT sales_document_series_channel_known CHECK (
        channel IS NULL OR channel IN ('STORE_AND_PHONE', 'ECOMMERCE', 'SKROUTZ')),

    -- A series that transforms into itself is a no-op that reads as a configured rule.
    CONSTRAINT sales_document_series_transform_not_self CHECK (
        transformable_into_series_id IS NULL OR transformable_into_series_id <> id)
);

-- ⚠️ THERE IS NO CHANNEL COLUMN ON THE PURCHASE SERIES TABLE, AND THAT IS DELIBERATE.
-- Channel is where a SALE came from. It never applies to a purchase. A column that can only
-- ever be null invites someone to fill it, and a purchase series carrying 'ECOMMERCE' would be
-- storable, meaningless, and indistinguishable from data.
CREATE TABLE purchase_document_series (
    id                          bigserial    PRIMARY KEY,

    abbreviation                varchar(20)  NOT NULL,
    description                 varchar(120) NOT NULL,
    document_type_id            bigint       NOT NULL,
    gets_mark                   boolean      NOT NULL,
    transformable_into_series_id bigint,
    active                      boolean      NOT NULL DEFAULT true,

    created_at                  timestamptz  NOT NULL DEFAULT now(),
    created_by                  varchar(100) NOT NULL DEFAULT 'system',
    updated_at                  timestamptz  NOT NULL DEFAULT now(),
    updated_by                  varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT purchase_document_series_abbreviation_unique UNIQUE (abbreviation),
    CONSTRAINT purchase_document_series_abbreviation_not_blank CHECK (btrim(abbreviation) <> ''),
    CONSTRAINT purchase_document_series_description_not_blank CHECK (btrim(description) <> ''),

    CONSTRAINT purchase_document_series_type_fk
        FOREIGN KEY (document_type_id) REFERENCES purchase_document_type (id),
    CONSTRAINT purchase_document_series_transform_fk
        FOREIGN KEY (transformable_into_series_id) REFERENCES purchase_document_series (id),

    CONSTRAINT purchase_document_series_transform_not_self CHECK (
        transformable_into_series_id IS NULL OR transformable_into_series_id <> id)
);

COMMENT ON TABLE sales_document_series IS
    'A numbering series of a sales document type. USER-CREATABLE; ships empty. Novocore RECORDS '
    'numbers and does not generate them — no sequence, no counter, no allocation, until step 40.';

COMMENT ON TABLE purchase_document_series IS
    'A numbering series of a purchase document type. USER-CREATABLE; ships empty. Deliberately '
    'has NO channel column: channel is where a SALE came from and never applies to a purchase.';

COMMENT ON COLUMN sales_document_series.channel IS
    'NULL means this series is not a sales channel (self-supply). R1b makes channel come FROM '
    'the series rather than being independently settable.';

CREATE INDEX sales_document_series_type_idx ON sales_document_series (document_type_id);
CREATE INDEX purchase_document_series_type_idx ON purchase_document_series (document_type_id);


-- -----------------------------------------------------------------------------------------
-- DELIVERY METHODS. ⚠️ SHIPS EMPTY
-- -----------------------------------------------------------------------------------------
-- How goods reach the customer — courier, own vehicle, collection. Not an AADE codification:
-- annex 8.14 Σκοπός Διακίνησης is the transport PURPOSE and belongs with 18b, which is a
-- different question from who carries the parcel.

CREATE TABLE delivery_method (
    id           bigserial    PRIMARY KEY,

    abbreviation varchar(20)  NOT NULL,
    description  varchar(120) NOT NULL,
    active       boolean      NOT NULL DEFAULT true,

    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   varchar(100) NOT NULL DEFAULT 'system',
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT delivery_method_abbreviation_unique UNIQUE (abbreviation),
    CONSTRAINT delivery_method_abbreviation_not_blank CHECK (btrim(abbreviation) <> ''),
    CONSTRAINT delivery_method_description_not_blank CHECK (btrim(description) <> '')
);

COMMENT ON TABLE delivery_method IS
    'How goods reach the customer. A business reference list, not an AADE codification — '
    'annex 8.14 Σκοπός Διακίνησης is the transport purpose and belongs with 18b.';
