-- =========================================================================================
-- V32 — STATUTORY IDENTIFIERS ON THE SALES INVOICE, AND THE THREE ARTEFACT ADDITIONS (R1a)
-- =========================================================================================
--
-- Four unrelated-looking things in one migration because they are one step's worth of
-- consequences of putting `docs/aade/v2.0.1/` in the repository:
--
--   1. ΜΑΡΚ, UID, QR URL, transmission status and the series reference on `sales_invoice`
--   2. `company.branch-number` and the AADE spec-version marker, as settings
--   3. VAT exemption reason codes 24 and 28, and provenance on the existing 29
--   4. myDATA unit-of-measure codes, from annex 8.13
--
-- ⚠️ SCHEMA AND VALIDATION ONLY. Nothing in R1a writes a ΜΑΡΚ, and nothing transmits. Every
-- new column is nullable or defaulted, so no existing row changes and no existing test can see
-- a difference — which is the whole R1a/R1b boundary.


-- -----------------------------------------------------------------------------------------
-- 1. STATUTORY IDENTIFIERS ON sales_invoice — ADR 0016
-- -----------------------------------------------------------------------------------------
-- ⚠️ These are CORE fields, and that is not a violation of architecture rule 2 ("external
-- system reference IDs never live on core entities"). The test that separates them is whether
-- the value survives the vendor being replaced:
--
--   * Go's internal document id identifies a row in GO'S database. Replace Go and it is
--     meaningless. It stays in the adapter's mapping table.
--   * The ΜΑΡΚ identifies NOVOCORE'S OWN DOCUMENT to the Greek state, permanently, and is
--     printed on the document itself. Replace Go and it is still the identifier of that sale.
--
-- ⚠️ Novocore never OBTAINS a ΜΑΡΚ. Greek law requires transmission at issuance, and the
-- document receives its ΜΑΡΚ and QR code there — through Prosvasis Go today and a certified
-- Πάροχος at step 40. A sales document appears in Novocore only after it legally exists, so
-- these columns are RECORDED, never allocated. That does not change in any phase.

ALTER TABLE sales_invoice
    -- ΜΑΡΚ — AADE's unique registration number for the document.
    --
    -- bigint rather than text because AADE's own schema types it that way: `invoiceMark` is
    -- `xs:long` in `response-v2.0.1.xsd`. Nothing computes with it; the type is chosen to match
    -- the authority's declaration rather than to enable arithmetic, and a value that arrives
    -- outside long range would be a spec change worth failing on rather than storing.
    ADD COLUMN mark bigint,

    -- The document UID — `invoiceUid`, `xs:string`. A hash AADE computes over the document's
    -- identifying fields; the second identifier a transmitted document carries.
    ADD COLUMN uid varchar(100),

    -- The URL encoded into the printed QR code — `qrUrl`, `xs:string`. Stored rather than
    -- composed, for the same reason vat_exemption_reason.mydata_code is stored verbatim:
    -- reproducing an authority's exact string by concatenation is a bet that costs nothing to
    -- avoid, and AADE has already changed the shape of things in this area between versions.
    ADD COLUMN qr_url varchar(500),

    -- Where the document stands with AADE.
    --
    --   UNKNOWN       recorded without transmission information. The honest state for every
    --                 row that exists today, and the default.
    --   NOT_REQUIRED  the document type does not require transmission (an operational document).
    --   TRANSMITTED   it reached AADE and carries a ΜΑΡΚ.
    --
    -- Three values and not four: a FAILED state is deliberately absent because nothing in this
    -- system can produce one — nothing transmits until step 29/40. Adding it now would be a
    -- state no code path can reach, which reads as coverage and is not.
    ADD COLUMN transmission_status varchar(30) NOT NULL DEFAULT 'UNKNOWN',

    -- ⚠️ NULLABLE, and it stays nullable through R1a. Every existing invoice predates series,
    -- and R1a cannot change what any existing test asserts. R1b is where a series becomes
    -- required and where channel starts coming FROM it.
    ADD COLUMN series_id bigint;

ALTER TABLE sales_invoice
    ADD CONSTRAINT sales_invoice_series_fk
        FOREIGN KEY (series_id) REFERENCES sales_document_series (id),

    ADD CONSTRAINT sales_invoice_transmission_status_known CHECK (
        transmission_status IN ('UNKNOWN', 'NOT_REQUIRED', 'TRANSMITTED')),

    -- A ΜΑΡΚ and TRANSMITTED are one fact said two ways: the document has a ΜΑΡΚ *because* it
    -- reached AADE. Allowing them to disagree would let a row claim transmission with no
    -- identifier to show for it, or carry an identifier while claiming it never went.
    ADD CONSTRAINT sales_invoice_mark_matches_transmission CHECK (
        (transmission_status = 'TRANSMITTED') = (mark IS NOT NULL)),

    -- Same shape as account.code: "no value" gets exactly one representation, so '' cannot
    -- become a second one and collide on the unique index below.
    ADD CONSTRAINT sales_invoice_uid_not_blank CHECK (uid IS NULL OR btrim(uid) <> ''),
    ADD CONSTRAINT sales_invoice_qr_url_not_blank CHECK (qr_url IS NULL OR btrim(qr_url) <> '');

-- AADE issues one ΜΑΡΚ per document. Two rows sharing one would mean the same transmitted
-- document recorded twice, which is the same defect the document-number trigger below exists
-- for, one authority further out.
CREATE UNIQUE INDEX sales_invoice_mark_idx ON sales_invoice (mark) WHERE mark IS NOT NULL;
CREATE UNIQUE INDEX sales_invoice_uid_idx ON sales_invoice (uid) WHERE uid IS NOT NULL;
CREATE INDEX sales_invoice_series_idx ON sales_invoice (series_id, id);

COMMENT ON COLUMN sales_invoice.mark IS
    'AADE''s ΜΑΡΚ. Recorded, never obtained — the document receives it at issuance through an '
    'external transmission path. A core field per ADR 0016: it survives Go being replaced.';

COMMENT ON COLUMN sales_invoice.series_id IS
    'The numbering series. Nullable through R1a because every existing invoice predates '
    'series; R1b makes it the source of the invoice''s channel.';


-- -----------------------------------------------------------------------------------------
-- ⚠️ RECONCILING DOCUMENT-NUMBER UNIQUENESS WITH SERIES
-- -----------------------------------------------------------------------------------------
-- V17 made `document_number` unique ACROSS THE WHOLE TABLE, via a BEFORE trigger plus a partial
-- unique index on upper(document_number) where reversal_of_id IS NULL. That was right when
-- there were no series and becomes WRONG the moment there are: ΑΛΠ-1 and ΤΠΔΑ-1 are two
-- different documents that both legitimately carry the number 1.
--
-- The key therefore becomes (series, number). The trap is that a plain UNIQUE (series_id,
-- upper(document_number)) would SILENTLY LOSE the guarantee for every row that exists today,
-- because two NULLs never collide in a unique index — so today's global uniqueness would
-- quietly become no uniqueness at all, on exactly the rows nobody would think to test.
--
-- COALESCE(series_id, -1) is what avoids that. With every row's series NULL — which is every
-- row today — all rows share group -1 and the index is EXACTLY today's global index. As series
-- are assigned, uniqueness becomes per-series. -1 is safe as the sentinel because series ids
-- come from a bigserial and are always positive.
--
-- ⚠️ This is additive in the strict sense: no currently-passing assertion changes.

DROP INDEX sales_invoice_number_idx;

CREATE UNIQUE INDEX sales_invoice_number_idx
    ON sales_invoice (COALESCE(series_id, -1), upper(document_number))
    WHERE reversal_of_id IS NULL;

-- The trigger carries the half no index can express — whether a row has been superseded — so
-- it gains the same series qualification rather than being replaced.
CREATE OR REPLACE FUNCTION sales_invoice_number_is_not_a_duplicate() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.reversal_of_id IS NOT NULL THEN
        RETURN NEW;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM sales_invoice existing
         WHERE upper(existing.document_number) = upper(NEW.document_number)
           -- IS NOT DISTINCT FROM and not =, so two rows with no series still collide. With
           -- `=` a NULL series would make the comparison NULL, the row would pass, and the
           -- guarantee would be lost for precisely the rows that have it today.
           AND existing.series_id IS NOT DISTINCT FROM NEW.series_id
           AND existing.id IS DISTINCT FROM NEW.id
           AND existing.reversal_of_id IS NULL
           AND NOT EXISTS (SELECT 1 FROM sales_invoice reversal
                            WHERE reversal.reversal_of_id = existing.id))
    THEN
        RAISE EXCEPTION
            'Sales invoice "%" has already been recorded in this series. Recording it twice '
            'would state the same revenue and the same output VAT twice. Reverse the one '
            'already recorded if it was wrong; a reversed invoice releases its number.',
            NEW.document_number;
    END IF;

    RETURN NEW;
END;
$$;


-- -----------------------------------------------------------------------------------------
-- 2. SETTINGS — the issuer branch number, and which spec version the codifications are from
-- -----------------------------------------------------------------------------------------
-- ⚠️ `company.branch-number` is NOT `mydata.*`. A branch number is a fact about this company
-- that myDATA happens to ask for, and naming it after the consumer would put every other
-- consumer of the same fact in the wrong namespace. It is also the first field of a company
-- identity block that DOES NOT EXIST: there is no ΑΦΜ, no company name and no address in
-- settings today, which is a wider gap than this one key and is recorded rather than filled.
--
-- Head office is 0 — AADE's own convention, not a placeholder. ⚠️ Never a constant in code: an
-- establishment number that lives in a Java field cannot be corrected by the person who knows
-- it is wrong.
--
-- The spec-version marker is what makes "are we behind AADE?" a question with an answer. It
-- points at `docs/aade/v2.0.1/`, whose README states what each annex seeded. ⚠️ The future diff
-- check ALERTS A HUMAN AND NEVER AUTO-APPLIES — a code list that updated itself would silently
-- change what already-transmitted documents claim.

INSERT INTO setting (setting_key, value, description) VALUES
    ('company.branch-number',
     '0',
     'The establishment (branch) number this installation issues from. 0 is head office, '
     'per AADE''s convention. Deliberately not under a mydata.* key: it is a fact about the '
     'company that myDATA happens to ask for.'),

    ('aade.spec-version',
     'v2.0.1',
     'Which AADE myDATA specification version the seeded statutory codifications correspond '
     'to. The artefacts are in docs/aade/v2.0.1/. A newer AADE release is diffed against them '
     'and ALERTS A HUMAN; it is never auto-applied.');


-- -----------------------------------------------------------------------------------------
-- 3. VAT EXEMPTION REASONS — codes 24 and 28, and provenance on the 29 already here
-- -----------------------------------------------------------------------------------------
-- V8 seeded 29 rows from Prosvasis Go's screen, with gaps at 24 and 28, and its own header
-- asked for confirmation against AADE's published table before the myDATA adapter. The table
-- is annex 8.3, it is now in the repository, and it answers both halves:
--
--   * `VatExemptionType` in SimpleTypes-v2.0.1.xsd is `xs:int` restricted to 1..31 —
--     THIRTY-ONE CODES WITH NO GAPS. So 24 and 28 are not retired; they were absent from Go.
--   * Every one of the 29 seeded descriptions matches AADE's ν.5144/2024 column. The
--     recodified article numbering V8 transcribed from Go is confirmed against the authority.
--     ⭐ The rows were right; the PROVENANCE was not, and that is what this fixes.
--
-- Descriptions below were read VISUALLY from a rasterised page 95, not from a text dump.
--
-- ⚠️ ONE NORMALISATION, STATED RATHER THAN SILENT: the annex renders code 28 with a
-- typographic en dash and a right single quotation mark (– and ’). Those are normalised to
-- ASCII '-' and ''', which is the punctuation every one of the existing 29 rows already uses.
-- The comparison that called those 29 "an exact match" was already a normalised one; doing it
-- differently here would make two rows in one table follow two conventions.
--
-- ⚠️ mydata_code IS NULL ON BOTH, AND THAT IS NOT AN OVERSIGHT.
--
-- The approved checklist said to seed them "with verbatim wire strings from annex 8.3". Annex
-- 8.3 DOES NOT CONTAIN WIRE STRINGS. It gives the reason text under two article numberings.
-- The `N-description` form in the other 26 rows is PROSVASIS GO'S rendering, transcribed from
-- Go because Go is what actually transmits today — and V8's header is explicit that it is
-- stored verbatim rather than composed, because composing is a bet. Codes 12 and 13 are the
-- proof: their description names "Πλοία Ανοικτής Θαλάσσης" and their myDATA string does not.
--
-- Go has no row for 24 or 28, so there is no verbatim string to copy. Composing one would be
-- exactly the bet the design refuses, on a value that gets transmitted to the tax authority.
-- NULL is the same stance already taken for the OSS/IOSS rows: "no mapping exists", not "not
-- filled in yet". Anything transmitting must refuse a NULL rather than substitute a guess.
--
-- 📌 Both are on the accountant list, for `input_vat_deductible` and now also for their myDATA
-- string. `input_vat_deductible` is false here because AADE's own
-- "Δικαίωμα έκπτωσης Φ.Π.Α. εισροών" column reads "Όχι" throughout this list, which is the
-- same basis on which all 29 existing rows carry false.

INSERT INTO vat_exemption_reason (code, description, mydata_code, input_vat_deductible) VALUES
    (24, 'Χωρίς ΦΠΑ - άρθρο 8 του Κώδικα ΦΠΑ',
         NULL, false),
    (28, 'Χωρίς ΦΠΑ - άρθρο 29 περ. β'' παρ.1 του Κώδικα ΦΠΑ, (Tax Free)',
         NULL, false);

COMMENT ON TABLE vat_exemption_reason IS
    'Official AADE VAT exemption reasons, each tied to an article of the Κώδικας ΦΠΑ. '
    'PROVENANCE: descriptions confirmed against annex 8.3 of the myDATA v2.0.1 ERP '
    'specification (docs/aade/v2.0.1/), ν.5144/2024 column, read from a rasterised page. '
    'The version this corresponds to is the aade.spec-version setting. 31 codes exist and 31 '
    'rows are seeded; 5 carry no myDATA string (29-31 OSS/IOSS, 24 and 28) because none has '
    'ever been supplied verbatim and composing one is forbidden. Not the same as a 0% VAT class.';

COMMENT ON COLUMN vat_exemption_reason.code IS
    'AADE''s reason number. VatExemptionType in SimpleTypes-v2.0.1.xsd restricts this to '
    '1..31 with no gaps, which is how V8''s open question about codes 24 and 28 was closed: '
    'they exist, and were merely absent from Prosvasis Go''s list.';


-- -----------------------------------------------------------------------------------------
-- 4. myDATA UNIT-OF-MEASURE CODES — annex 8.13, and what it does NOT answer
-- -----------------------------------------------------------------------------------------
-- V11 left `mydata_code` NULL on all eight seeded units because "the verified AADE unit code
-- list has not been supplied", and Q38 sat on the waiting-on-the-accountant list from step 3b
-- for the same reason. ⭐ The list was published all along: annex 8.13, cross-checked against
-- `QuantityType` in SimpleTypes-v2.0.1.xsd, which is `xs:int` restricted to 1..7.
--
-- ⚠️ SEVEN AADE CODES, NOT EIGHT. The approved checklist said "the 8 unit-of-measure myDATA
-- codes"; there are seven, and the eight is the number of NovoCore unit ROWS. The two numbers
-- were conflated, and they do not line up:
--
--     1 Τεμάχια                    2 Κιλά      3 Λίτρα     4 Μέτρα
--     5 Τετραγωνικά Μέτρα          6 Κυβικά Μέτρα          7 Τεμάχια_Λοιπές Περιπτώσεις
--
-- FOUR of our eight map with certainty and are set here. ⚠️ FOUR ARE LEFT NULL AND LISTED OPEN,
-- and each is a different kind of gap rather than the same one four times:
--
--   * GRAM and MILLILITRE — AADE'S LIST HAS NO SUB-UNITS. Mapping GRAM to 2 (Κιλά) would
--     transmit a quantity wrong by a factor of a thousand, which is invisible on our side and
--     a compliance defect on AADE's. There is no code to map them to, and no amount of
--     deciding produces one; the question is what to transmit for a line priced per gram, and
--     it is a real question for the accountant rather than a lookup.
--   * SET and PACK — a genuine judgement between 1 (Τεμάχια) and 7 (Τεμάχια_Λοιπές
--     Περιπτώσεις). Both readings are defensible, the choice changes what is transmitted, and
--     "auto-resolve only what is genuinely certain" says this is not certain.
--
-- ⭐ So Q38's lesson needs sharpening rather than closing: the artefact answered the half
-- nobody could answer without it, and left a half that genuinely IS the accountant's. The
-- failure was filing the whole question against a person because the document that answers
-- most of it was not in the repository.
--
-- `GET /api/units-of-measure/without-mydata-code` already lists exactly these four, which is
-- the route built in step 16b so that a question waiting on somebody else is visible to the
-- person who has to ask them.

UPDATE unit_of_measure SET mydata_code = '1' WHERE code = 'PIECE';
UPDATE unit_of_measure SET mydata_code = '2' WHERE code = 'KILOGRAM';
UPDATE unit_of_measure SET mydata_code = '3' WHERE code = 'LITRE';
UPDATE unit_of_measure SET mydata_code = '4' WHERE code = 'METRE';

COMMENT ON COLUMN unit_of_measure.mydata_code IS
    'AADE''s Είδος Ποσότητας code from annex 8.13, 1..7 per QuantityType in '
    'SimpleTypes-v2.0.1.xsd. NULL where no mapping exists — GRAM and MILLILITRE have no AADE '
    'code at all (the list has no sub-units), and SET and PACK are an open judgement between '
    '1 and 7. Transmission must refuse a NULL rather than composing a code.';

-- A guard for the four that were just set, and a negative control for the four that were not:
-- if this migration ever silently matched nothing — a renamed unit code, a reordered seed —
-- the UPDATEs above would succeed while changing zero rows, and nothing would say so.
DO $$
DECLARE
    mapped   int;
    unmapped int;
BEGIN
    SELECT count(*) INTO mapped FROM unit_of_measure WHERE mydata_code IS NOT NULL;
    SELECT count(*) INTO unmapped FROM unit_of_measure WHERE mydata_code IS NULL;

    IF mapped <> 4 THEN
        RAISE EXCEPTION
            'Expected exactly 4 units of measure to carry a myDATA code after this migration, '
            'found %. The UPDATEs above match on unit_of_measure.code; if a code was renamed '
            'they match nothing and report success.', mapped;
    END IF;

    IF unmapped <> 4 THEN
        RAISE EXCEPTION
            'Expected exactly 4 units of measure to remain without a myDATA code (GRAM, '
            'MILLILITRE, SET, PACK), found %. If this is a new unit, decide its code or leave '
            'it open deliberately and update this assertion.', unmapped;
    END IF;
END;
$$;
