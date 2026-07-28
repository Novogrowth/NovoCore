-- The real AADE VAT exemption reason list, as configured in Prosvasis Go.
--
-- V5 built this table and deliberately left it empty, because these codes are transmitted to
-- AADE and a plausible wrong list is worse than a visibly empty one. The verified list has now
-- been supplied from Go's own "Διατάξεις απαλλαγής Φ.Π.Α." screen, so this seeds it.
--
-- ---------------------------------------------------------------------------------------
-- WHERE THESE VALUES COME FROM, AND WHAT WAS AND WAS NOT TRANSCRIBED
-- ---------------------------------------------------------------------------------------
-- 29 rows, with GAPS AT 24 AND 28. Gaps are expected — V5 says so — but note that these two
-- are absent from *Go's* list rather than known to be retired by AADE. Worth confirming against
-- AADE's published table before the myDATA adapter is built (roadmap phase 7); if AADE defines
-- 24 and 28 and we ever need them, they are two INSERTs, not a restructuring.
--
-- The article numbers below are the RECODIFIED Κώδικας ΦΠΑ numbering (άρθρο 2 και 3, 5, 17, 18,
-- 21, ... 58), not the older numbering (3, 5, 13, 14, 16, ... 47δ) that most existing
-- documentation still uses. They are transcribed as Go states them, because Go is the invoicing
-- system of record until phase 11 and its values are the ones actually transmitted today.
--
-- `description` DELIBERATELY OMITS THE NUMERIC PREFIX. Go stores its description as
-- "1 - Χωρίς ΦΠΑ - άρθρο 2 και 3 του Κώδικα ΦΠΑ", repeating the code inside the text. Here the
-- code is its own column, so keeping the prefix would render as "1 - 1 - Χωρίς ΦΠΑ ..." in any
-- picker and would need stripping at every use site. Stripping it once, here, also sidesteps a
-- transcription quirk in the source: Go's row for code 9 appears to carry the prefix "8 - ".
--
-- `mydata_code` IS STORED VERBATIM, prefix included, exactly as V5 requires — it is the value
-- that goes on the wire. For most rows it happens to equal code || '-' || description, and
-- VatExemptionReasonIT now asserts precisely which rows break that pattern rather than assuming
-- none do. Codes 12 and 13 are the interesting ones: their Go description names
-- "Πλοία Ανοικτής Θαλάσσης" while their myDATA string does not, which is the concrete
-- justification for having stored the two separately instead of composing one from the other.

-- ---------------------------------------------------------------------------------------
-- mydata_code becomes nullable
-- ---------------------------------------------------------------------------------------
-- Three of the 29 rows — the OSS and IOSS reasons, codes 29, 30 and 31 — have NO myDATA code in
-- Go. That is a real, distinguishable state and not missing data on our side: the AADE exemption
-- reason exists whether or not our current invoicing system has mapped it to a myDATA value.
--
-- NULL says exactly that. The alternatives were both worse: composing a myDATA string for them
-- would fabricate a value that gets transmitted, and omitting the three rows would leave an
-- exempt OSS/IOSS sale with no reason to select at all. The consequence to honour later is that
-- anything transmitting to myDATA (phase 7) must REFUSE a reason whose myDATA code is NULL
-- rather than send a blank or a guess.
--
-- The blank check is re-stated in the same shape used for account.code, so that "no value" has
-- exactly one representation and '' cannot be used as a second one. UNIQUE is left in place;
-- PostgreSQL permits many NULLs in a unique index, which is the behaviour wanted here.

ALTER TABLE vat_exemption_reason
    ALTER COLUMN mydata_code DROP NOT NULL;

ALTER TABLE vat_exemption_reason
    DROP CONSTRAINT vat_exemption_reason_mydata_code_not_blank;

ALTER TABLE vat_exemption_reason
    ADD CONSTRAINT vat_exemption_reason_mydata_code_not_blank
        CHECK (mydata_code IS NULL OR btrim(mydata_code) <> '');

COMMENT ON COLUMN vat_exemption_reason.mydata_code IS
    'The exact string myDATA expects, verbatim. NULL where our invoicing system has no myDATA '
    'mapping for the reason (OSS/IOSS). A NULL must be refused by anything transmitting, never '
    'replaced with a composed value.';

COMMENT ON TABLE vat_exemption_reason IS
    'Official AADE VAT exemption reasons, each tied to an article of the Κώδικας ΦΠΑ. '
    'Seeded from Prosvasis Go, recodified article numbering. Not the same as a 0% VAT class.';

-- ---------------------------------------------------------------------------------------
-- Seed
-- ---------------------------------------------------------------------------------------
-- input_vat_deductible is false for every row: AADE's "Δικαίωμα έκπτωσης Φ.Π.Α. εισροών" reads
-- "Όχι" throughout this list. Written out per row rather than defaulted, so that the day one
-- entry differs it is a one-value change to a stated value rather than an exception to a rule
-- nobody recorded.

INSERT INTO vat_exemption_reason (code, description, mydata_code, input_vat_deductible) VALUES
    (1,  'Χωρίς ΦΠΑ - άρθρο 2 και 3 του Κώδικα ΦΠΑ',
         '1-Χωρίς ΦΠΑ - άρθρο 2 και 3 του Κώδικα ΦΠΑ',            false),
    (2,  'Χωρίς ΦΠΑ - άρθρο 5 του Κώδικα ΦΠΑ',
         '2-Χωρίς ΦΠΑ - άρθρο 5 του Κώδικα ΦΠΑ',                  false),
    (3,  'Χωρίς ΦΠΑ - άρθρο 17 του Κώδικα ΦΠΑ',
         '3-Χωρίς ΦΠΑ - άρθρο 17 του Κώδικα ΦΠΑ',                 false),
    (4,  'Χωρίς ΦΠΑ - άρθρο 18 του Κώδικα ΦΠΑ',
         '4-Χωρίς ΦΠΑ - άρθρο 18 του Κώδικα ΦΠΑ',                 false),
    (5,  'Χωρίς ΦΠΑ - άρθρο 21 του Κώδικα ΦΠΑ',
         '5-Χωρίς ΦΠΑ - άρθρο 21 του Κώδικα ΦΠΑ',                 false),
    (6,  'Χωρίς ΦΠΑ - άρθρο 24 του Κώδικα ΦΠΑ',
         '6-Χωρίς ΦΠΑ - άρθρο 24 του Κώδικα ΦΠΑ',                 false),
    (7,  'Χωρίς ΦΠΑ - άρθρο 27 του Κώδικα ΦΠΑ',
         '7-Χωρίς ΦΠΑ - άρθρο 27 του Κώδικα ΦΠΑ',                 false),
    (8,  'Χωρίς ΦΠΑ - άρθρο 29 του Κώδικα ΦΠΑ',
         '8-Χωρίς ΦΠΑ - άρθρο 29 του Κώδικα ΦΠΑ',                 false),
    (9,  'Χωρίς ΦΠΑ - άρθρο 30 του Κώδικα ΦΠΑ',
         '9-Χωρίς ΦΠΑ - άρθρο 30 του Κώδικα ΦΠΑ',                 false),
    (10, 'Χωρίς ΦΠΑ - άρθρο 31 του Κώδικα ΦΠΑ',
         '10-Χωρίς ΦΠΑ - άρθρο 31 του Κώδικα ΦΠΑ',                false),
    (11, 'Χωρίς ΦΠΑ - άρθρο 32 του Κώδικα ΦΠΑ',
         '11-Χωρίς ΦΠΑ - άρθρο 32 του Κώδικα ΦΠΑ',                false),
    -- 12 and 13: description and myDATA string genuinely differ. See the header.
    (12, 'Χωρίς ΦΠΑ - άρθρο 32 - Πλοία Ανοικτής Θαλάσσης του Κώδικα ΦΠΑ',
         '12-Χωρίς ΦΠΑ - άρθρο 32 του Κώδικα ΦΠΑ',                false),
    (13, 'Χωρίς ΦΠΑ - άρθρο 32 .1.γ. - Πλοία Ανοικτής Θαλάσσης του Κώδικα ΦΠΑ',
         '13-Χωρίς ΦΠΑ - άρθρο 32 .1.γ. του Κώδικα ΦΠΑ',          false),
    (14, 'Χωρίς ΦΠΑ - άρθρο 33 του Κώδικα ΦΠΑ',
         '14-Χωρίς ΦΠΑ - άρθρο 33 του Κώδικα ΦΠΑ',                false),
    (15, 'Χωρίς ΦΠΑ - άρθρο 44 του Κώδικα ΦΠΑ',
         '15-Χωρίς ΦΠΑ - άρθρο 44 του Κώδικα ΦΠΑ',                false),
    (16, 'Χωρίς ΦΠΑ - άρθρο 45 του Κώδικα ΦΠΑ',
         '16-Χωρίς ΦΠΑ - άρθρο 45 του Κώδικα ΦΠΑ',                false),
    (17, 'Χωρίς ΦΠΑ - άρθρο 47 του Κώδικα ΦΠΑ',
         '17-Χωρίς ΦΠΑ - άρθρο 47 του Κώδικα ΦΠΑ',                false),
    (18, 'Χωρίς ΦΠΑ - άρθρο 48 του Κώδικα ΦΠΑ',
         '18-Χωρίς ΦΠΑ - άρθρο 48 του Κώδικα ΦΠΑ',                false),
    (19, 'Χωρίς ΦΠΑ - άρθρο 54 του Κώδικα ΦΠΑ',
         '19-Χωρίς ΦΠΑ - άρθρο 54 του Κώδικα ΦΠΑ',                false),
    -- 20-23: "ΦΠΑ εμπεριεχόμενος" — VAT included in the price rather than absent. A different
    -- thing from the "Χωρίς ΦΠΑ" rows above, and the reason this list is not simply a set of
    -- article references.
    (20, 'ΦΠΑ εμπεριεχόμενος - άρθρο 50 του Κώδικα ΦΠΑ',
         '20-ΦΠΑ εμπεριεχόμενος - άρθρο 50 του Κώδικα ΦΠΑ',       false),
    (21, 'ΦΠΑ εμπεριεχόμενος - άρθρο 51 του Κώδικα ΦΠΑ',
         '21-ΦΠΑ εμπεριεχόμενος - άρθρο 51 του Κώδικα ΦΠΑ',       false),
    (22, 'ΦΠΑ εμπεριεχόμενος - άρθρο 52 του Κώδικα ΦΠΑ',
         '22-ΦΠΑ εμπεριεχόμενος - άρθρο 52 του Κώδικα ΦΠΑ',       false),
    (23, 'ΦΠΑ εμπεριεχόμενος - άρθρο 53 του Κώδικα ΦΠΑ',
         '23-ΦΠΑ εμπεριεχόμενος - άρθρο 53 του Κώδικα ΦΠΑ',       false),
    -- No 24 in Go's list.
    (25, 'Χωρίς ΦΠΑ - ΠΟΛ.1029/1995',
         '25-Χωρίς ΦΠΑ - ΠΟΛ.1029/1995',                          false),
    (26, 'Χωρίς ΦΠΑ - ΠΟΛ.1167/2015',
         '26-Χωρίς ΦΠΑ - ΠΟΛ.1167/2015',                          false),
    (27, 'Λοιπές Εξαιρέσεις ΦΠΑ',
         '27-Λοιπές Εξαιρέσεις ΦΠΑ',                              false),
    -- No 28 in Go's list.
    -- 29-31: no myDATA code in Go. NULL rather than a composed guess — see the header.
    (29, 'Χωρίς ΦΠΑ - άρθρο 56 του Κώδικα ΦΠΑ (OSS_μη ενωσιακό καθεστώς)',
         NULL,                                                    false),
    (30, 'Χωρίς ΦΠΑ - άρθρο 57 του Κώδικα ΦΠΑ (OSS_ενωσιακό καθεστώς)',
         NULL,                                                    false),
    (31, 'Χωρίς ΦΠΑ - άρθρο 58 του Κώδικα ΦΠΑ (IOSS)',
         NULL,                                                    false);
