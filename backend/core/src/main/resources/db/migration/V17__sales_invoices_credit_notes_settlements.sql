-- Step 9: the sales side, and the open-item layer that sits over it.
--
-- Sales Invoice, Credit Note, Receipt, Payment, Bank Transfer, open-item matching, rounding — plus
-- the seven obligations steps 3 to 8 recorded against this step, and Q10, Q15's remainder, Q16 and
-- Q26. ADR 0009 is the write-up; this file is where it becomes schema.
--
-- ---------------------------------------------------------------------------------------
-- THE ONE IDEA THIS FILE IS BUILT ON: DOCUMENTS POST, ALLOCATIONS DO NOT
-- ---------------------------------------------------------------------------------------
-- Brief §6's open item matching is a layer OVER Accounts receivable and Accounts payable, not a
-- second ledger beside them. A sales invoice posts (debit AR, credit revenue, credit output VAT). A
-- receipt posts (debit bank, credit AR). The ALLOCATION that says "this receipt paid that invoice"
-- posts nothing at all — both sides of it are already in AR, and an entry for it would be an entry
-- debiting and crediting the same account for the same amount.
--
-- That is why `open_item_allocation` has no journal_entry_id and never will. It also means an
-- allocation can be created, reduced and released freely without touching a posted entry, which is
-- exactly what Q13's second half requires: editing a receipt below its allocated total reduces
-- allocations, and reducing an allocation is not a correction to the ledger.
--
-- The consequence worth stating: a document's OPEN AMOUNT is computed, never stored. It is the
-- document's gross less what has been allocated against it. Nothing anywhere holds a "paid" flag,
-- for the reason no account holds a balance.
--
-- ---------------------------------------------------------------------------------------
-- Q10, ANSWERED: THE GENERIC RETAIL CUSTOMER IS SEEDED, AND IT IS STRUCTURAL
-- ---------------------------------------------------------------------------------------
-- Step 5 deliberately did not seed one, on the grounds that a catch-all customer absorbs every
-- unmatched sale and then cannot be untangled. Answered: seed it, with the same treatment the chart
-- of accounts gives its system accounts — because the alternative, a human creating it by hand on
-- the first day of trading, produces exactly the row step 5 was afraid of, only with no way for the
-- software to know which row it is.
--
-- So `customer.system_key` exists for one value, RETAIL_WALK_IN, and it does three things:
--
--   * it makes the row findable by machine, which is what stops a second one being created;
--   * it makes the row PROTECTED — it cannot be deactivated, and when merge is built it must refuse
--     this row on both sides. Brief §5's "alias forward, never rewrite history" is about two records
--     of one real party; this is not a party at all, it is the absence of one, so aliasing it into
--     somebody would attribute every anonymous till sale ever made to one named person;
--   * it makes the VAT treatment fixed rather than editable. DOMESTIC, no VAT number, no exemption
--     reason, by CHECK. It cannot sensibly carry a VAT number (it is not one identifiable party) and
--     it cannot be INTRA_EU_B2B or EXEMPT (both are claims about a specific counterparty).
--
-- What it is NOT: a default. Nothing falls back to it. A sale names this customer because whoever
-- rang it up chose "retail, no details", which is a real answer to who bought it.
--
-- ---------------------------------------------------------------------------------------
-- Q15's REMAINDER, ANSWERED: CONFIRM AT ENTRY, RECORD THE CONFIRMATION, NO QUEUE
-- ---------------------------------------------------------------------------------------
-- Q15 settled that rounding is compared PER DOCUMENT. What it left open is where a flagged-for-review
-- item lives — a review queue, or a flag on the record. Step 8 deliberately used a flag plus a query
-- for Q17's shortfall rather than inventing a queue by accident. Answered here properly:
--
--   * A REVIEW QUEUE IS A SECOND COPY OF STATE. It has to be created when the condition arises and
--     removed when it is resolved, and the day those two fall out of step the queue shows work that
--     is already done or hides work that is not. That is the argument this schema has made against
--     `normal_balance_side`, against a stored balance, and against a `superseded` flag beside
--     `reversal_of_id`. It applies unchanged here.
--
--   * WHERE THE AMBIGUITY IS VISIBLE AT ENTRY, IT IS CONFIRMED AT ENTRY. A rounding difference above
--     `ledger.rounding.threshold` REFUSES the invoice, naming both totals, until the caller states
--     explicitly that the difference is accepted. That is CLAUDE.md rule 7 exactly — auto-resolve
--     what is certain (a residual cent), require one-click confirmation for everything else — and it
--     is strictly better than a queue, because the person who can explain the difference is the
--     person in front of the document, not whoever opens the queue next week.
--
--   * THE CONFIRMATION IS RECORDED ON THE RECORD. `rounding_accepted_by` / `_at` / `_note`, so
--     "somebody looked at this and decided it was fine" is a fact in the data rather than an absence
--     of a queue row. That is the half a bare flag loses.
--
--   * WHERE THE CONDITION IS A CONSEQUENCE THE OPERATOR CANNOT FIX AT ENTRY, it stays a flag plus a
--     query — Q17's stock shortfall, which is what it is whether or not anyone confirms it.
--
-- Phase 8's Clearing Checks therefore reads queries, not a queue table, and there is no table here
-- for it to read a stale row out of.
--
-- ---------------------------------------------------------------------------------------
-- ROUNDING: THE LEDGER ALWAYS AGREES WITH THE DOCUMENT
-- ---------------------------------------------------------------------------------------
-- Brief §6 says rounding is independently computed and compared against the source document. Since
-- Prosvasis Go issues the invoice until roadmap phase 11, the source document's gross is the figure
-- the customer actually owes, so THE DIFFERENCE IS ALWAYS POSTED — to `Rounding differences` — and
-- the threshold decides only whether a human had to agree to it first. The alternative, posting our
-- own computed total and flagging the difference, would leave Accounts receivable disagreeing with
-- the document the customer holds, which is the one outcome open-item matching cannot survive.
--
-- `stated_total` is NULL when nothing external stated one. Then there is nothing to compare against
-- and the rounding difference is zero by definition, rather than zero by luck.
--
-- ---------------------------------------------------------------------------------------
-- WHAT IS DELIBERATELY ABSENT
-- ---------------------------------------------------------------------------------------
-- * NO `paid` FLAG AND NO STORED OPEN AMOUNT, anywhere. See above.
-- * NO CUSTOMER MERGE. Still not built — brief §5's alias-forward needs an alias table and a rule for
--   postings made under the retired id. What step 9 adds is the rule that the retail customer is
--   refused on both sides of it when it is built, stated on `CustomerSystemKey` so it is not
--   rediscovered then.
-- * NO PARTIAL-QUANTITY CREDIT WITHOUT A RETURN PATH. A credit note either returns stock or does not;
--   when it does, `InventoryService.returnConsumed` puts it back into the lots it came from at the
--   cost it left at. See the stock_consumption section below.
-- * NO DOCUMENT NUMBER OF OUR OWN for anything except the sales invoice and the credit note, which
--   carry the number the issuing system printed. Q40 is still open for journal entries, purchase
--   invoices and goods receipts.
-- * NO AUTOMATIC BANK MATCHING. Brief §6's frequent Bank Aggregator polling feeds this open-item
--   layer in roadmap phase 6; what step 9 owes it is the allocation model, which is here.

-- ---------------------------------------------------------------------------------------
-- Thirteen new account system keys, and why a set described as "deliberately small" grows by this
-- much in one step
-- ---------------------------------------------------------------------------------------
-- The criterion has not changed: a key exists when NOVOCORE'S OWN POSTING RULES have to locate a
-- specific account at runtime, because a code is blank and a name is operator-editable. Step 9 is
-- the first step where the software chooses a revenue account and a settlement account by itself,
-- and every one of these is chosen by a rule rather than by a person:
--
--   * the three channel Sales accounts and the three Sales returns accounts — `SalesChannel` picks
--     one of each pair, which is the whole reason step 3 split them per channel;
--   * `Services` — a SERVICE product credits it instead of a channel Sales account, which step 5
--     recorded as behaviour ProductType decides;
--   * Cash, the three Partner Clearing accounts, PayPal and Stripe — `SettlementMethod` picks one,
--     which is brief §6's payment-method settlement automation. Cash additionally needs a machine
--     identity for the €500 legal limit, which cannot be enforced against an account found by name.
--
-- No key was added for `Cost of service sold`: nothing computes a service's cost, so no rule
-- resolves it, and a key is a promise the account is reachable by code that does not exist.

ALTER TABLE account
    DROP CONSTRAINT account_system_key_known;

ALTER TABLE account
    ADD CONSTRAINT account_system_key_known CHECK (system_key IS NULL OR system_key IN (
        'ROUNDING_DIFFERENCES',
        'GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING',
        'PURCHASE_PRICE_VARIANCE',
        'UNCLASSIFIED_NEEDS_REVIEW',
        'FREIGHT_LANDED_COST_UNALLOCATED',
        'INVENTORY_WRITE_OFF',
        'ACCOUNTS_RECEIVABLE',
        'ACCOUNTS_PAYABLE',
        'INVENTORY',
        'FIXED_ASSETS_AT_COST',
        'FIXED_ASSETS_ACCUMULATED_DEPRECIATION',
        'COST_OF_GOODS_SOLD',
        'OUTPUT_VAT',
        'INPUT_VAT',
        'DEPRECIATION_EXPENSE',
        'SALES_STORE_AND_PHONE',
        'SALES_ECOMMERCE',
        'SALES_SKROUTZ',
        'SALES_RETURNS_STORE_AND_PHONE',
        'SALES_RETURNS_ECOMMERCE',
        'SALES_RETURNS_SKROUTZ',
        'SERVICES_INCOME',
        'CASH',
        'PARTNER_CLEARING_POS',
        'PARTNER_CLEARING_SKROUTZ',
        'PARTNER_CLEARING_ACS',
        'PAYPAL',
        'STRIPE'));

-- Keyed by name, which is safe exactly once: these are V4's own seeded rows and nothing has posted
-- to them. The DO block below fails the migration loudly if any of them has been renamed, rather
-- than leaving a posting rule with no account to resolve — V14 and V16's stance.
UPDATE account SET system_key = 'SALES_STORE_AND_PHONE'         WHERE name = 'Sales — Store & Phone';
UPDATE account SET system_key = 'SALES_ECOMMERCE'               WHERE name = 'Sales — eCommerce';
UPDATE account SET system_key = 'SALES_SKROUTZ'                 WHERE name = 'Sales — Skroutz';
UPDATE account SET system_key = 'SALES_RETURNS_STORE_AND_PHONE' WHERE name = 'Sales returns — Store & Phone';
UPDATE account SET system_key = 'SALES_RETURNS_ECOMMERCE'       WHERE name = 'Sales returns — eCommerce';
UPDATE account SET system_key = 'SALES_RETURNS_SKROUTZ'         WHERE name = 'Sales returns — Skroutz';
UPDATE account SET system_key = 'SERVICES_INCOME'               WHERE name = 'Services';
UPDATE account SET system_key = 'CASH'                          WHERE name = 'Cash';
UPDATE account SET system_key = 'PARTNER_CLEARING_POS'          WHERE name = 'Partner Clearing — POS provider';
UPDATE account SET system_key = 'PARTNER_CLEARING_SKROUTZ'      WHERE name = 'Partner Clearing — Skroutz';
UPDATE account SET system_key = 'PARTNER_CLEARING_ACS'          WHERE name = 'Partner Clearing — ACS Courier';
UPDATE account SET system_key = 'PAYPAL'                        WHERE name = 'PayPal';
UPDATE account SET system_key = 'STRIPE'                        WHERE name = 'Stripe';

DO $$
DECLARE
    missing text;
BEGIN
    SELECT string_agg(expected, ', ' ORDER BY expected)
      INTO missing
      FROM (VALUES
            ('SALES_STORE_AND_PHONE'), ('SALES_ECOMMERCE'), ('SALES_SKROUTZ'),
            ('SALES_RETURNS_STORE_AND_PHONE'), ('SALES_RETURNS_ECOMMERCE'), ('SALES_RETURNS_SKROUTZ'),
            ('SERVICES_INCOME'), ('CASH'), ('PARTNER_CLEARING_POS'), ('PARTNER_CLEARING_SKROUTZ'),
            ('PARTNER_CLEARING_ACS'), ('PAYPAL'), ('STRIPE')) AS wanted(expected)
     WHERE NOT EXISTS (SELECT 1 FROM account WHERE system_key = wanted.expected);

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION
            'V17 expected V4''s seeded account names and could not find: %. Every sales invoice and '
            'every settlement resolves one of these at runtime, so the step would fail on its first '
            'posting rather than here.', missing;
    END IF;
END $$;

-- ---------------------------------------------------------------------------------------
-- A permission section for sales
-- ---------------------------------------------------------------------------------------
-- Separate from CUSTOMERS for the reason PURCHASING is separate from SUPPLIERS: the customer list is
-- a directory, while a sales invoice is every figure of revenue in the business. Remote/Order Staff
-- has FULL on Customers and is deliberately granted nothing here.
--
-- Receipts, payments, bank transfers and open-item matching are their own section again, because
-- reading what the business was paid and by whom is a different job from recording a sale.

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
        'INVENTORY',
        'JOURNAL',
        'PURCHASING',
        'SALES',
        'SETTLEMENTS',
        'SALES_ORDER_FULFILLMENT',
        'BACK_IN_STOCK_REMINDERS'));

-- ---------------------------------------------------------------------------------------
-- Q10: the generic retail customer
-- ---------------------------------------------------------------------------------------

ALTER TABLE customer
    ADD COLUMN system_key varchar(40);

ALTER TABLE customer
    ADD CONSTRAINT customer_system_key_unique UNIQUE (system_key);

ALTER TABLE customer
    ADD CONSTRAINT customer_system_key_known CHECK (
        system_key IS NULL OR system_key IN ('RETAIL_WALK_IN'));

-- The VAT treatment is fixed rather than editable, and these are the three halves of that. A shared
-- anonymous record is not one identifiable party, so it cannot hold a party's VAT number and cannot
-- make a claim about a party's status.
ALTER TABLE customer
    ADD CONSTRAINT customer_system_record_is_domestic CHECK (
        system_key IS NULL OR vat_status = 'DOMESTIC');
ALTER TABLE customer
    ADD CONSTRAINT customer_system_record_has_no_vat_number CHECK (
        system_key IS NULL OR vat_number IS NULL);
ALTER TABLE customer
    ADD CONSTRAINT customer_system_record_has_no_exemption_reason CHECK (
        system_key IS NULL OR vat_exemption_reason_id IS NULL);

-- Not "system_key IS NULL OR active", which would merely record the intention. `active` is settable
-- by the service, so the CHECK is what makes deactivation impossible rather than merely refused.
ALTER TABLE customer
    ADD CONSTRAINT customer_system_record_stays_active CHECK (
        system_key IS NULL OR active = true);

COMMENT ON COLUMN customer.system_key IS
    'Q10. Non-null on a customer NovoCore''s own logic has to locate — today only the shared retail '
    'record. Protected from deactivation by CHECK and from merge by rule: it is the absence of an '
    'identified party, not a party, so aliasing it forward would attribute every anonymous till sale '
    'to one named person.';

INSERT INTO customer (name, vat_status, system_key, created_by, updated_by)
VALUES ('Πελάτης Λιανικής', 'DOMESTIC', 'RETAIL_WALK_IN', 'system', 'system');

-- ---------------------------------------------------------------------------------------
-- Sales invoices
-- ---------------------------------------------------------------------------------------
-- RECORDED, NOT ISSUED — the same stance as the purchase invoice, and for a reason that is temporary
-- but real: Prosvasis Go is the invoicing system of record until roadmap phase 11, so `document_number`
-- is the number Go printed and `stated_total` is what Go's document says the customer owes. That is
-- also the second reason immutability (Q13) is right here: the document exists outside NovoCore.

CREATE TABLE sales_invoice (
    id                      bigserial     PRIMARY KEY,

    customer_id             bigint        NOT NULL,

    -- Which of the three channel Sales accounts this invoice credits. Step 3 split them precisely so
    -- that return rate stays visible per channel; the channel is on the invoice rather than on the
    -- line because one document is one sale through one channel.
    channel                 varchar(30)   NOT NULL,

    -- Brief §6's payment-method settlement automation, as a property of the document. It decides ONE
    -- thing: which account the invoice debits. A method that settles immediately debits its own
    -- account and the invoice is born fully settled with no open amount; ON_ACCOUNT and BANK_DEPOSIT
    -- debit Accounts receivable and the invoice is an open item until a Receipt allocates against it.
    settlement_method       varchar(30)   NOT NULL,

    -- The issuing system's number. NOT NULL: every sales document has one, unlike a delivery note.
    document_number         varchar(60)   NOT NULL,

    invoice_date            date          NOT NULL,
    description             varchar(500),

    -- What the source document says the customer owes, gross. NULL when nothing external stated one —
    -- then there is nothing to compare against and the rounding difference is zero by definition.
    stated_total            numeric(19,2),
    stated_total_currency   char(3),

    -- The difference between the stated total and what NovoCore computed from the lines, always
    -- posted to `Rounding differences` so that Accounts receivable agrees with the document the
    -- customer holds. Either sign. Zero on the ordinary invoice.
    rounding_amount         numeric(19,2) NOT NULL,
    rounding_amount_currency char(3)      NOT NULL,

    -- Q15's remainder. STORED rather than derived from the threshold on read, because
    -- `ledger.rounding.threshold` is operator-changeable and a later change must not retroactively
    -- alter which invoices somebody had to agree to. Same argument as freezing an invoice line's net.
    rounding_needed_review  boolean       NOT NULL DEFAULT false,
    rounding_accepted_by    varchar(100),
    rounding_accepted_at    timestamptz,
    rounding_note           varchar(500),

    journal_entry_id        bigint        NOT NULL,
    reversal_of_id          bigint,

    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              varchar(100)  NOT NULL DEFAULT 'system',
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT sales_invoice_customer_fk
        FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT sales_invoice_journal_entry_fk
        FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),
    CONSTRAINT sales_invoice_reversal_of_fk
        FOREIGN KEY (reversal_of_id) REFERENCES sales_invoice (id),

    CONSTRAINT sales_invoice_entry_unique UNIQUE (journal_entry_id),
    CONSTRAINT sales_invoice_reversed_at_most_once UNIQUE (reversal_of_id),
    CONSTRAINT sales_invoice_not_its_own_reversal CHECK (
        reversal_of_id IS NULL OR reversal_of_id <> id),

    CONSTRAINT sales_invoice_channel_known CHECK (
        channel IN ('STORE_AND_PHONE', 'ECOMMERCE', 'SKROUTZ')),
    CONSTRAINT sales_invoice_settlement_method_known CHECK (
        settlement_method IN ('CASH', 'CARD_POS', 'SKROUTZ', 'ACS_COD', 'PAYPAL', 'STRIPE',
                              'BANK_DEPOSIT', 'ON_ACCOUNT')),

    CONSTRAINT sales_invoice_number_not_blank CHECK (btrim(document_number) <> ''),
    CONSTRAINT sales_invoice_date_is_plausible CHECK (invoice_date >= DATE '2000-01-01'),

    CONSTRAINT sales_invoice_stated_total_has_currency CHECK (
        (stated_total IS NULL) = (stated_total_currency IS NULL)),
    CONSTRAINT sales_invoice_rounding_has_currency CHECK (
        (rounding_amount IS NULL) = (rounding_amount_currency IS NULL)),

    -- A rounding difference with nothing to compare against is not a rounding difference.
    CONSTRAINT sales_invoice_rounding_needs_a_stated_total CHECK (
        rounding_amount = 0 OR stated_total IS NOT NULL),

    -- The acceptance is one fact: who, and when. Recorded only where there was something to accept.
    CONSTRAINT sales_invoice_acceptance_is_whole CHECK (
        (rounding_accepted_by IS NULL) = (rounding_accepted_at IS NULL)),
    CONSTRAINT sales_invoice_acceptance_needs_a_review CHECK (
        rounding_accepted_by IS NULL OR rounding_needed_review = true),
    CONSTRAINT sales_invoice_review_was_accepted CHECK (
        rounding_needed_review = false OR rounding_accepted_by IS NOT NULL)
);

COMMENT ON TABLE sales_invoice IS
    'A sale, recorded rather than issued while Prosvasis Go remains the invoicing system of record. '
    'Immutable once posted (Q13); corrected by a reversing document. Its settlement method decides '
    'whether it debits a bank/clearing account and is born settled, or debits AR and is an open item.';

-- The same rule and the same mechanism as the purchase invoice's supplier number, and here for a
-- stronger reason: a duplicated sales document number means the same sale recorded twice, which
-- overstates revenue and output VAT. A trigger rather than a partial unique index because whether a
-- row is superseded depends on whether ANOTHER row points at it, which no index over this row's own
-- columns can see. A reversing document deliberately carries the original's number, and once an
-- invoice has been reversed its number is released for a correct re-entry.
CREATE FUNCTION sales_invoice_number_is_not_a_duplicate() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.reversal_of_id IS NOT NULL THEN
        RETURN NEW;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM sales_invoice existing
         WHERE upper(existing.document_number) = upper(NEW.document_number)
           AND existing.id IS DISTINCT FROM NEW.id
           AND existing.reversal_of_id IS NULL
           AND NOT EXISTS (SELECT 1 FROM sales_invoice reversal
                            WHERE reversal.reversal_of_id = existing.id))
    THEN
        RAISE EXCEPTION
            'Sales invoice "%" has already been recorded. Recording it twice would state the same '
            'revenue and the same output VAT twice. Reverse the one already recorded if it was '
            'wrong; a reversed invoice releases its number.', NEW.document_number;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER sales_invoice_number_is_not_a_duplicate
    BEFORE INSERT OR UPDATE ON sales_invoice
    FOR EACH ROW EXECUTE FUNCTION sales_invoice_number_is_not_a_duplicate();

CREATE UNIQUE INDEX sales_invoice_number_idx
    ON sales_invoice (upper(document_number))
    WHERE reversal_of_id IS NULL;

CREATE INDEX sales_invoice_customer_idx ON sales_invoice (customer_id, invoice_date);
CREATE INDEX sales_invoice_date_idx ON sales_invoice (invoice_date, id);

-- Q15's query, as an index rather than a scan: the invoices whose rounding difference somebody had
-- to accept. Phase 8's Clearing Checks reads exactly this, and there is no queue table for it to read
-- a stale row out of.
CREATE INDEX sales_invoice_rounding_review_idx ON sales_invoice (invoice_date, id)
    WHERE rounding_needed_review = true;

CREATE TABLE sales_invoice_line (
    id                       bigserial     PRIMARY KEY,

    invoice_id               bigint        NOT NULL,
    line_number              integer       NOT NULL,

    -- PRODUCT sells something out of the catalogue — goods, a service, or a bundle. CHARGE is a fee
    -- charged TO the customer as revenue, through the ChargeType lookup V7 seeded: delivery, COD fee.
    -- The two shapes are the purchase invoice line's rule applied to the other direction.
    line_type                varchar(20)   NOT NULL,

    product_id               bigint,
    charge_type_id           bigint,

    quantity                 numeric(19,6) NOT NULL,
    unit_price               numeric(19,6) NOT NULL,
    unit_price_currency      char(3)       NOT NULL,

    -- Frozen, for purchase_invoice_line's reason: these are what a document somebody else holds says,
    -- and `ledger.rounding.mode` is operator-changeable, so re-deriving them later could produce a
    -- different cent from the one that was charged and filed.
    net_amount               numeric(19,2) NOT NULL,
    net_amount_currency      char(3)       NOT NULL,
    vat_amount               numeric(19,2) NOT NULL,
    vat_amount_currency      char(3)       NOT NULL,

    -- Exactly one, never both and never neither — the purchase side's rule, unchanged.
    vat_class_id             bigint,
    vat_exemption_reason_id  bigint,

    -- WHICH LEVEL SUPPLIED THE RATE. VatClassPrecedence returns it and step 3b built it so that "why
    -- is this line at 13%?" is answerable about a real invoice; storing it is what makes that true a
    -- year later, when the customer's override has been changed and the product's default with it.
    vat_class_source         varchar(20),

    -- The stock this line took out, one direction only. NULL on a service line, on a charge line, on
    -- a bundle line (its components carry their own), and on a goods line whose product had nothing
    -- to consume.
    stock_consumption_id     bigint,

    description              varchar(500),

    created_at               timestamptz   NOT NULL DEFAULT now(),
    created_by               varchar(100)  NOT NULL DEFAULT 'system',
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    updated_by               varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT sales_invoice_line_invoice_fk
        FOREIGN KEY (invoice_id) REFERENCES sales_invoice (id),
    CONSTRAINT sales_invoice_line_product_fk
        FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT sales_invoice_line_charge_type_fk
        FOREIGN KEY (charge_type_id) REFERENCES charge_type (id),
    CONSTRAINT sales_invoice_line_vat_class_fk
        FOREIGN KEY (vat_class_id) REFERENCES vat_class (id),
    CONSTRAINT sales_invoice_line_exemption_fk
        FOREIGN KEY (vat_exemption_reason_id) REFERENCES vat_exemption_reason (id),
    CONSTRAINT sales_invoice_line_consumption_fk
        FOREIGN KEY (stock_consumption_id) REFERENCES stock_consumption (id),

    CONSTRAINT sales_invoice_line_number_unique UNIQUE (invoice_id, line_number),
    CONSTRAINT sales_invoice_line_number_sane CHECK (line_number >= 0),
    -- One consumption belongs to one line, which is what makes the stored direction sufficient.
    CONSTRAINT sales_invoice_line_consumption_unique UNIQUE (stock_consumption_id),

    CONSTRAINT sales_invoice_line_type_known CHECK (line_type IN ('PRODUCT', 'CHARGE')),
    CONSTRAINT sales_invoice_line_shape CHECK (
        (line_type = 'PRODUCT' AND product_id IS NOT NULL AND charge_type_id IS NULL)
        OR
        (line_type = 'CHARGE' AND product_id IS NULL AND charge_type_id IS NOT NULL)),

    CONSTRAINT sales_invoice_line_quantity_positive CHECK (quantity > 0),
    -- Zero allowed: a line given away as part of a promotion is a real line, and unlike a zero
    -- SELLING PRICE on a product it is an explicit act on one document rather than an unset field.
    CONSTRAINT sales_invoice_line_unit_price_not_negative CHECK (unit_price >= 0),
    CONSTRAINT sales_invoice_line_net_not_negative CHECK (net_amount >= 0),
    CONSTRAINT sales_invoice_line_vat_not_negative CHECK (vat_amount >= 0),

    CONSTRAINT sales_invoice_line_unit_price_has_currency CHECK (
        (unit_price IS NULL) = (unit_price_currency IS NULL)),
    CONSTRAINT sales_invoice_line_net_has_currency CHECK (
        (net_amount IS NULL) = (net_amount_currency IS NULL)),
    CONSTRAINT sales_invoice_line_vat_has_currency CHECK (
        (vat_amount IS NULL) = (vat_amount_currency IS NULL)),

    CONSTRAINT sales_invoice_line_vat_treatment_is_stated CHECK (
        (vat_class_id IS NULL) <> (vat_exemption_reason_id IS NULL)),
    CONSTRAINT sales_invoice_line_exempt_carries_no_vat CHECK (
        vat_exemption_reason_id IS NULL OR vat_amount = 0),
    -- The source is recorded exactly when a class won; an exempt line had no rate to choose.
    CONSTRAINT sales_invoice_line_vat_source_goes_with_class CHECK (
        (vat_class_id IS NULL) = (vat_class_source IS NULL)),
    CONSTRAINT sales_invoice_line_vat_source_known CHECK (
        vat_class_source IS NULL OR vat_class_source IN ('INVOICE_LINE', 'CUSTOMER', 'PRODUCT'))
);

CREATE INDEX sales_invoice_line_invoice_idx ON sales_invoice_line (invoice_id, line_number);
CREATE INDEX sales_invoice_line_product_idx
    ON sales_invoice_line (product_id) WHERE product_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------
-- Bundle decomposition, materialised — and the step 6 obligation it discharges
-- ---------------------------------------------------------------------------------------
-- Brief §5: "Revenue reporting shows both bundle-level and decomposed component-level lines (linked,
-- not duplicated)." The bundle level is `sales_invoice_line`; this is the component level, and
-- BundleDecomposition's constructor already enforces that the components sum exactly to the bundle
-- line, so the two levels are the same money by construction rather than by a reader's care.
--
-- STORED AT SALE TIME, NEVER RECOMPUTED. That is what discharges the step 6 obligation about
-- `BundleService.dissolve` on a bundle that has been sold: brief §5's "alias forward, never rewrite
-- history" is satisfied because there is no history to rewrite — the decomposition on a posted
-- invoice is a copy of what was allocated on the day, so dissolving the bundle afterwards changes
-- nothing about any invoice already recorded, and the product keeps existing under its own SKU.
--
-- The obligation this creates in exchange, and it is a real one: A REPORT MUST READ THESE ROWS AND
-- NEVER `BundleService.componentsOf`. The current definition can differ from the one that was sold,
-- or be gone entirely.

CREATE TABLE sales_invoice_line_component (
    id                         bigserial     PRIMARY KEY,

    invoice_line_id            bigint        NOT NULL,
    component_number           integer       NOT NULL,

    product_id                 bigint        NOT NULL,
    quantity                   numeric(19,6) NOT NULL,

    -- This component's share of the bundle line's net, allocated by BundleAllocation — exact integer
    -- arithmetic in cents, largest-remainder, so the parts add up to the whole with no residual and
    -- no rounding mode involved at all.
    allocated_amount           numeric(19,2) NOT NULL,
    allocated_amount_currency  char(3)       NOT NULL,

    stock_consumption_id       bigint,

    created_at                 timestamptz   NOT NULL DEFAULT now(),
    created_by                 varchar(100)  NOT NULL DEFAULT 'system',
    updated_at                 timestamptz   NOT NULL DEFAULT now(),
    updated_by                 varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT sales_invoice_line_component_line_fk
        FOREIGN KEY (invoice_line_id) REFERENCES sales_invoice_line (id),
    CONSTRAINT sales_invoice_line_component_product_fk
        FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT sales_invoice_line_component_consumption_fk
        FOREIGN KEY (stock_consumption_id) REFERENCES stock_consumption (id),

    CONSTRAINT sales_invoice_line_component_number_unique
        UNIQUE (invoice_line_id, component_number),
    CONSTRAINT sales_invoice_line_component_product_unique
        UNIQUE (invoice_line_id, product_id),
    CONSTRAINT sales_invoice_line_component_consumption_unique UNIQUE (stock_consumption_id),

    CONSTRAINT sales_invoice_line_component_number_sane CHECK (component_number >= 0),
    CONSTRAINT sales_invoice_line_component_quantity_positive CHECK (quantity > 0),
    CONSTRAINT sales_invoice_line_component_amount_not_negative CHECK (allocated_amount >= 0),
    CONSTRAINT sales_invoice_line_component_amount_has_currency CHECK (
        (allocated_amount IS NULL) = (allocated_amount_currency IS NULL))
);

CREATE INDEX sales_invoice_line_component_line_idx
    ON sales_invoice_line_component (invoice_line_id, component_number);
CREATE INDEX sales_invoice_line_component_product_idx
    ON sales_invoice_line_component (product_id);

-- ---------------------------------------------------------------------------------------
-- The serialized unit's sale link — the step 6 obligation, discharged
-- ---------------------------------------------------------------------------------------
-- Brief §5 puts the customer/invoice link ON THE UNIT once sold, and step 6 deliberately added no
-- nullable customer id early, because a unit marked sold to somebody with no document behind it is a
-- claim nothing can substantiate. There is a document now.
--
-- Both columns together or neither, and only on a SOLD unit — so `SerializedUnitStatus.SOLD` becomes
-- reachable and cannot be reached without saying who bought it and on what.

ALTER TABLE serialized_unit
    ADD COLUMN sold_to_customer_id     bigint,
    ADD COLUMN sold_on_invoice_line_id bigint;

ALTER TABLE serialized_unit
    ADD CONSTRAINT serialized_unit_sold_to_customer_fk
        FOREIGN KEY (sold_to_customer_id) REFERENCES customer (id),
    ADD CONSTRAINT serialized_unit_sold_on_invoice_line_fk
        FOREIGN KEY (sold_on_invoice_line_id) REFERENCES sales_invoice_line (id),

    ADD CONSTRAINT serialized_unit_sale_link_is_whole CHECK (
        (sold_to_customer_id IS NULL) = (sold_on_invoice_line_id IS NULL)),
    ADD CONSTRAINT serialized_unit_sale_link_iff_sold CHECK (
        (status = 'SOLD') = (sold_to_customer_id IS NOT NULL));

COMMENT ON COLUMN serialized_unit.sold_on_invoice_line_id IS
    'Brief §5''s customer/invoice link on a sold unit. Biconditional with SOLD: the status cannot be '
    'set without the document, and the document cannot be recorded on a unit that has not been sold.';

CREATE INDEX serialized_unit_sold_to_idx
    ON serialized_unit (sold_to_customer_id) WHERE sold_to_customer_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------
-- Returning consumed stock — and why it is not a reversal
-- ---------------------------------------------------------------------------------------
-- Step 8 built `reverseConsumption`: an exact ledger mirror, the whole quantity, `reversal_of_id`
-- set, UNIQUE so it can happen at most once. That is the correction for a consumption that SHOULD NOT
-- HAVE HAPPENED, and it stays exactly as it is.
--
-- A customer returning two of the five they bought is a different fact. The consumption was correct;
-- goods have come back. So it posts an ordinary entry (debit Inventory, credit Cost of goods sold, one
-- line per lot) rather than a mirror, it can happen more than once against one consumption, and it can
-- be for part of the quantity.
--
-- Represented as rows in `stock_consumption` because that is what they are — the same lots, the same
-- stored unit costs, the same shape — with `returns_consumption_id` naming the consumption they came
-- out of. A separate `stock_return` table would duplicate the lines table and the lot arithmetic to
-- express a sign.
--
-- THE INVARIANT: total returned against one consumption never exceeds what it filled. A trigger, not a
-- CHECK, because it spans rows.

ALTER TABLE stock_consumption
    ADD COLUMN returns_consumption_id bigint;

ALTER TABLE stock_consumption
    ADD CONSTRAINT stock_consumption_returns_fk
        FOREIGN KEY (returns_consumption_id) REFERENCES stock_consumption (id),
    ADD CONSTRAINT stock_consumption_not_its_own_return CHECK (
        returns_consumption_id IS NULL OR returns_consumption_id <> id),
    -- A row is a reversal, or a return, or an ordinary consumption. Never two of those.
    ADD CONSTRAINT stock_consumption_reversal_or_return CHECK (
        reversal_of_id IS NULL OR returns_consumption_id IS NULL);

COMMENT ON COLUMN stock_consumption.returns_consumption_id IS
    'The consumption this row put stock back into. A RETURN, not a reversal: the original was correct '
    'and the goods came back, so it posts an ordinary entry, may be partial, and may happen more than '
    'once. Reversal remains exactly-once, by UNIQUE constraint, and is for a consumption that should '
    'not have happened.';

CREATE INDEX stock_consumption_returns_idx
    ON stock_consumption (returns_consumption_id) WHERE returns_consumption_id IS NOT NULL;

CREATE FUNCTION stock_consumption_returns_within_what_was_taken() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    original_filled numeric(19,6);
    already_returned numeric(19,6);
BEGIN
    IF NEW.returns_consumption_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT quantity_filled INTO original_filled
      FROM stock_consumption WHERE id = NEW.returns_consumption_id;

    SELECT COALESCE(sum(quantity_filled), 0) INTO already_returned
      FROM stock_consumption
     WHERE returns_consumption_id = NEW.returns_consumption_id
       AND id IS DISTINCT FROM NEW.id;

    IF already_returned + NEW.quantity_filled > original_filled THEN
        RAISE EXCEPTION
            'Consumption % took % out of stock and % has already come back, so % more cannot. '
            'Returning more than was sold would create stock out of a credit note.',
            NEW.returns_consumption_id, original_filled, already_returned, NEW.quantity_filled;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER stock_consumption_returns_within_what_was_taken
    BEFORE INSERT OR UPDATE ON stock_consumption
    FOR EACH ROW EXECUTE FUNCTION stock_consumption_returns_within_what_was_taken();

-- ---------------------------------------------------------------------------------------
-- Credit notes — Q26
-- ---------------------------------------------------------------------------------------
-- Its own transaction type, not a negative sales invoice. It references the invoice it corrects, posts
-- to the per-channel `Sales returns` CONTRA_INCOME account rather than reducing the channel's Sales
-- account, and is immutable once issued — the same policy as the invoice it corrects, for the same
-- reason. Step 3 created three returns accounts precisely so return rate stays visible per channel;
-- netting a credit note into Sales would collapse exactly that.
--
-- ITS OPEN AMOUNT FACES THE OTHER WAY (brief §6: "credit note = negative-open-amount document"). It
-- can be allocated against the invoice it corrects, reducing both open amounts and posting nothing, or
-- refunded by an outgoing settlement to the customer, which posts.

CREATE TABLE credit_note (
    id                       bigserial     PRIMARY KEY,

    -- Required. Q26 says a credit note references the invoice it corrects, and that reference is what
    -- supplies the channel, the customer and the returns account. A credit note against no invoice
    -- would be a discount posted as a return, which is a different fact with a different account.
    sales_invoice_id         bigint        NOT NULL,
    customer_id              bigint        NOT NULL,

    document_number          varchar(60)   NOT NULL,
    credit_note_date         date          NOT NULL,
    description              varchar(500),

    stated_total             numeric(19,2),
    stated_total_currency    char(3),
    rounding_amount          numeric(19,2) NOT NULL,
    rounding_amount_currency char(3)       NOT NULL,
    rounding_needed_review   boolean       NOT NULL DEFAULT false,
    rounding_accepted_by     varchar(100),
    rounding_accepted_at     timestamptz,
    rounding_note            varchar(500),

    journal_entry_id         bigint        NOT NULL,
    reversal_of_id           bigint,

    created_at               timestamptz   NOT NULL DEFAULT now(),
    created_by               varchar(100)  NOT NULL DEFAULT 'system',
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    updated_by               varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT credit_note_invoice_fk
        FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoice (id),
    CONSTRAINT credit_note_customer_fk
        FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT credit_note_journal_entry_fk
        FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),
    CONSTRAINT credit_note_reversal_of_fk
        FOREIGN KEY (reversal_of_id) REFERENCES credit_note (id),

    CONSTRAINT credit_note_entry_unique UNIQUE (journal_entry_id),
    CONSTRAINT credit_note_reversed_at_most_once UNIQUE (reversal_of_id),
    CONSTRAINT credit_note_not_its_own_reversal CHECK (
        reversal_of_id IS NULL OR reversal_of_id <> id),

    CONSTRAINT credit_note_number_not_blank CHECK (btrim(document_number) <> ''),
    CONSTRAINT credit_note_date_is_plausible CHECK (credit_note_date >= DATE '2000-01-01'),

    CONSTRAINT credit_note_stated_total_has_currency CHECK (
        (stated_total IS NULL) = (stated_total_currency IS NULL)),
    CONSTRAINT credit_note_rounding_has_currency CHECK (
        (rounding_amount IS NULL) = (rounding_amount_currency IS NULL)),
    CONSTRAINT credit_note_rounding_needs_a_stated_total CHECK (
        rounding_amount = 0 OR stated_total IS NOT NULL),
    CONSTRAINT credit_note_acceptance_is_whole CHECK (
        (rounding_accepted_by IS NULL) = (rounding_accepted_at IS NULL)),
    CONSTRAINT credit_note_acceptance_needs_a_review CHECK (
        rounding_accepted_by IS NULL OR rounding_needed_review = true),
    CONSTRAINT credit_note_review_was_accepted CHECK (
        rounding_needed_review = false OR rounding_accepted_by IS NOT NULL)
);

COMMENT ON TABLE credit_note IS
    'Q26: its own transaction type, referencing the invoice it corrects, posting to the per-channel '
    'Sales returns account, immutable once issued. Its open amount faces the other way — it settles '
    'an invoice, or is refunded by an outgoing settlement.';

CREATE UNIQUE INDEX credit_note_number_idx
    ON credit_note (upper(document_number))
    WHERE reversal_of_id IS NULL;

CREATE INDEX credit_note_invoice_idx ON credit_note (sales_invoice_id);
CREATE INDEX credit_note_customer_idx ON credit_note (customer_id, credit_note_date);
CREATE INDEX credit_note_date_idx ON credit_note (credit_note_date, id);
CREATE INDEX credit_note_rounding_review_idx ON credit_note (credit_note_date, id)
    WHERE rounding_needed_review = true;

CREATE TABLE credit_note_line (
    id                       bigserial     PRIMARY KEY,

    credit_note_id           bigint        NOT NULL,
    line_number              integer       NOT NULL,

    -- The invoice line being credited. Required, which is what keeps a credit note anchored to real
    -- lines rather than being a free-hand negative document: the VAT class, the product and the
    -- channel all come from there, so a credit note cannot credit at a rate the sale never charged.
    sales_invoice_line_id    bigint        NOT NULL,

    quantity                 numeric(19,6) NOT NULL,
    unit_price               numeric(19,6) NOT NULL,
    unit_price_currency      char(3)       NOT NULL,

    net_amount               numeric(19,2) NOT NULL,
    net_amount_currency      char(3)       NOT NULL,
    vat_amount               numeric(19,2) NOT NULL,
    vat_amount_currency      char(3)       NOT NULL,

    -- Whether the goods physically came back. FALSE is the ordinary case for a price correction, and
    -- for a return of something that was never stock. TRUE puts the quantity back into the lots it
    -- left, at the cost it left at, through `InventoryService.returnConsumed`.
    stock_returned           boolean       NOT NULL DEFAULT false,
    return_consumption_id    bigint,

    description              varchar(500),

    created_at               timestamptz   NOT NULL DEFAULT now(),
    created_by               varchar(100)  NOT NULL DEFAULT 'system',
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    updated_by               varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT credit_note_line_note_fk
        FOREIGN KEY (credit_note_id) REFERENCES credit_note (id),
    CONSTRAINT credit_note_line_invoice_line_fk
        FOREIGN KEY (sales_invoice_line_id) REFERENCES sales_invoice_line (id),
    CONSTRAINT credit_note_line_return_consumption_fk
        FOREIGN KEY (return_consumption_id) REFERENCES stock_consumption (id),

    CONSTRAINT credit_note_line_number_unique UNIQUE (credit_note_id, line_number),
    CONSTRAINT credit_note_line_number_sane CHECK (line_number >= 0),
    CONSTRAINT credit_note_line_return_consumption_unique UNIQUE (return_consumption_id),

    CONSTRAINT credit_note_line_quantity_positive CHECK (quantity > 0),
    CONSTRAINT credit_note_line_unit_price_not_negative CHECK (unit_price >= 0),
    CONSTRAINT credit_note_line_net_not_negative CHECK (net_amount >= 0),
    CONSTRAINT credit_note_line_vat_not_negative CHECK (vat_amount >= 0),

    CONSTRAINT credit_note_line_unit_price_has_currency CHECK (
        (unit_price IS NULL) = (unit_price_currency IS NULL)),
    CONSTRAINT credit_note_line_net_has_currency CHECK (
        (net_amount IS NULL) = (net_amount_currency IS NULL)),
    CONSTRAINT credit_note_line_vat_has_currency CHECK (
        (vat_amount IS NULL) = (vat_amount_currency IS NULL)),

    -- A consumption is recorded exactly when stock came back. A line claiming a return with nothing
    -- to show for it would leave the goods missing and the ledger saying otherwise. The other way
    -- round is legitimate: a zero-cost lot returns stock and posts nothing, so the consumption row
    -- can be absent while the flag is set — which is why this is one-directional.
    CONSTRAINT credit_note_line_return_needs_the_flag CHECK (
        return_consumption_id IS NULL OR stock_returned = true)
);

CREATE INDEX credit_note_line_note_idx ON credit_note_line (credit_note_id, line_number);
CREATE INDEX credit_note_line_invoice_line_idx ON credit_note_line (sales_invoice_line_id);

-- ---------------------------------------------------------------------------------------
-- Q16: unallocated credit as a standalone document
-- ---------------------------------------------------------------------------------------
-- Answered in step 7: a standalone credit document, not a bare AR balance adjustment, consistent with
-- treating every financial event as a trackable document with its own lifecycle.
--
-- IT POSTS NOTHING, AND THAT IS THE POINT. The money is already in Accounts receivable — the receipt
-- that overpaid put it there. This document does not move it again; it says whose it is and that it is
-- available to settle something later, which is precisely the open-item layer's job. Giving it a
-- journal entry would debit and credit AR for the same amount.
--
-- IT IS CREATED ONLY WHEN THE CALLER SAYS SO. A receipt whose allocations come to less than its amount
-- has two entirely different meanings — the customer overpaid and the balance is theirs to spend, or
-- this is a bulk remittance nobody has finished matching yet — and guessing between them is what rule
-- 7 forbids. The unmatched case stays unallocated on the settlement and is queryable, which is brief
-- §6's "unmatched lines flagged for Clearing Checks".

CREATE TABLE customer_credit (
    id                      bigserial     PRIMARY KEY,

    customer_id             bigint        NOT NULL,

    -- The settlement that produced it. Required: credit arises from money actually received, and a
    -- credit conjured with no receipt behind it is a gift nobody recorded giving.
    settlement_id           bigint        NOT NULL,

    credit_date             date          NOT NULL,
    amount                  numeric(19,2) NOT NULL,
    amount_currency         char(3)       NOT NULL,
    description             varchar(500),

    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              varchar(100)  NOT NULL DEFAULT 'system',
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT customer_credit_customer_fk
        FOREIGN KEY (customer_id) REFERENCES customer (id),

    CONSTRAINT customer_credit_amount_positive CHECK (amount > 0),
    CONSTRAINT customer_credit_amount_has_currency CHECK (
        (amount IS NULL) = (amount_currency IS NULL)),
    CONSTRAINT customer_credit_date_is_plausible CHECK (credit_date >= DATE '2000-01-01'),
    -- One settlement produces at most one credit document: the remainder is one amount.
    CONSTRAINT customer_credit_settlement_unique UNIQUE (settlement_id)
);

COMMENT ON TABLE customer_credit IS
    'Q16: unallocated customer credit as a standalone document with its own open amount. Posts '
    'nothing — the receipt that overpaid already put the money in Accounts receivable.';

CREATE INDEX customer_credit_customer_idx ON customer_credit (customer_id, credit_date);

-- ---------------------------------------------------------------------------------------
-- Receipts and payments — one table, and the reasoning for that
-- ---------------------------------------------------------------------------------------
-- Brief §6 names Receipt and Payment as two typed transactions and JournalSource carries both, because
-- Q13's policy is stated per source. Structurally they are one thing: money moving between one of our
-- accounts and one counterparty's sub-ledger, with allocations against that counterparty's documents.
-- Every column, every rule and every line of the allocation logic would be identical in two tables.
--
-- So: ONE TABLE, with `direction` deciding the side and `party_type` deciding the control account.
-- Four combinations, and all four are real:
--
--   INCOMING  + CUSTOMER  a receipt from a customer         debit bank, credit AR
--   OUTGOING  + SUPPLIER  a payment to a supplier           debit AP,   credit bank
--   OUTGOING  + CUSTOMER  a refund against a credit note    debit AR,   credit bank
--   INCOMING  + SUPPLIER  a refund from a supplier          debit bank, credit AP
--
-- The source on the resulting journal entry is still RECEIPT or PAYMENT per direction, so Q13's policy
-- is unchanged and the ledger cannot tell that the two share a table. WHEN TO SPLIT THEM: the first
-- column that belongs to one and not the other. There is none today.

CREATE TABLE settlement (
    id                 bigserial     PRIMARY KEY,

    direction          varchar(20)   NOT NULL,
    party_type         varchar(20)   NOT NULL,
    party_id           bigint        NOT NULL,

    -- OUR account the money moved through — a bank account, the cash box, or a partner clearing
    -- account. Named explicitly rather than derived from a method, because which bank it landed in is
    -- a fact about what happened and not a policy the software gets to choose.
    account_id         bigint        NOT NULL,

    settlement_date    date          NOT NULL,
    amount             numeric(19,2) NOT NULL,
    amount_currency    char(3)       NOT NULL,

    -- The bank's own reference, the POS batch, the receipt book number. Free text, not unique:
    -- nothing is reconciled against it until the Bank Aggregator adapter exists.
    reference          varchar(100),
    description        varchar(500),

    journal_entry_id   bigint        NOT NULL,

    created_at         timestamptz   NOT NULL DEFAULT now(),
    created_by         varchar(100)  NOT NULL DEFAULT 'system',
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    updated_by         varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT settlement_account_fk
        FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT settlement_journal_entry_fk
        FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),

    CONSTRAINT settlement_entry_unique UNIQUE (journal_entry_id),

    CONSTRAINT settlement_direction_known CHECK (direction IN ('INCOMING', 'OUTGOING')),
    CONSTRAINT settlement_party_type_known CHECK (party_type IN ('CUSTOMER', 'SUPPLIER')),
    CONSTRAINT settlement_party_id_positive CHECK (party_id > 0),

    CONSTRAINT settlement_amount_positive CHECK (amount > 0),
    CONSTRAINT settlement_amount_has_currency CHECK (
        (amount IS NULL) = (amount_currency IS NULL)),
    CONSTRAINT settlement_reference_not_blank CHECK (reference IS NULL OR btrim(reference) <> ''),
    CONSTRAINT settlement_date_is_plausible CHECK (settlement_date >= DATE '2000-01-01')
);

COMMENT ON TABLE settlement IS
    'A Receipt or a Payment (brief §6). Editable in place (Q13), with the previous state written to '
    'the audit log; editing one below its allocated total releases allocations most-recent-first.';

-- No reversal columns, deliberately: Q13 makes these AMENDABLE IN PLACE, so correction is an edit
-- recorded in the audit log rather than a reversing document. The ledger enforces that half — a
-- RECEIPT entry is amendable and `JournalService.reverse` refuses it, naming this service.

-- The party is polymorphic and therefore cannot be a foreign key, so the referenced row is checked to
-- exist by trigger with dynamic SQL — V15's pattern for a journal line's sub-ledger reference, and
-- here for the same reason: doing it in Java would be a check the database can make directly.
CREATE FUNCTION settlement_party_exists() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    referenced text;
    exists_flag boolean;
BEGIN
    referenced := CASE NEW.party_type
                      WHEN 'CUSTOMER' THEN 'customer'
                      WHEN 'SUPPLIER' THEN 'supplier'
                  END;

    EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I WHERE id = $1)', referenced)
       INTO exists_flag USING NEW.party_id;

    IF NOT exists_flag THEN
        RAISE EXCEPTION
            'Settlement names % #%, which does not exist. Money cannot be received from or paid to '
            'a party with no record.', NEW.party_type, NEW.party_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER settlement_party_exists
    BEFORE INSERT OR UPDATE ON settlement
    FOR EACH ROW EXECUTE FUNCTION settlement_party_exists();

CREATE INDEX settlement_party_idx ON settlement (party_type, party_id, settlement_date);
CREATE INDEX settlement_date_idx ON settlement (settlement_date, id);
CREATE INDEX settlement_account_idx ON settlement (account_id, settlement_date);

ALTER TABLE customer_credit
    ADD CONSTRAINT customer_credit_settlement_fk
        FOREIGN KEY (settlement_id) REFERENCES settlement (id);

-- ---------------------------------------------------------------------------------------
-- Open-item allocations
-- ---------------------------------------------------------------------------------------
-- Brief §6: "each invoice has a computed open amount. A Receipt/Payment carries one or more
-- allocations — covers simple payments, installments, bulk remittances, overpayment, refunds."
--
-- ONE ROW SAYS: this much of that source settled this much of that target. Both ends are polymorphic
-- because there are genuinely three kinds of source and three kinds of target, and a nullable foreign
-- key per combination would be nine columns of which seven are always null.
--
-- IT POSTS NOTHING. See the top of this file.
--
-- `allocation_order` IS Q13's SECOND HALF, MADE POSSIBLE. Editing a Receipt below its already-allocated
-- total must release allocations MOST RECENT FIRST, which requires knowing which was applied last —
-- and `created_at` cannot answer it, because several allocations created in one transaction share a
-- timestamp to the microsecond. So the order is explicit, ascending, unique per source.

CREATE TABLE open_item_allocation (
    id                bigserial     PRIMARY KEY,

    source_type       varchar(20)   NOT NULL,
    source_id         bigint        NOT NULL,
    target_type       varchar(20)   NOT NULL,
    target_id         bigint        NOT NULL,

    allocation_order  integer       NOT NULL,

    amount            numeric(19,2) NOT NULL,
    amount_currency   char(3)       NOT NULL,

    created_at        timestamptz   NOT NULL DEFAULT now(),
    created_by        varchar(100)  NOT NULL DEFAULT 'system',
    updated_at        timestamptz   NOT NULL DEFAULT now(),
    updated_by        varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT open_item_allocation_source_known CHECK (
        source_type IN ('SETTLEMENT', 'CREDIT_NOTE', 'CUSTOMER_CREDIT')),
    CONSTRAINT open_item_allocation_target_known CHECK (
        target_type IN ('SALES_INVOICE', 'PURCHASE_INVOICE', 'CREDIT_NOTE')),

    -- Which pairings mean anything. A credit note settles a sales invoice; customer credit settles a
    -- sales invoice; a settlement settles any of the three. A credit note settling a purchase invoice
    -- would be a customer's return paying a supplier.
    CONSTRAINT open_item_allocation_pairing_is_meaningful CHECK (
        source_type = 'SETTLEMENT'
        OR (source_type IN ('CREDIT_NOTE', 'CUSTOMER_CREDIT') AND target_type = 'SALES_INVOICE')),

    -- A source does not settle the same target twice: the amount is what carries "some of it", and a
    -- second row would be indistinguishable from a duplicate. Same rule as `gr_ir_match`'s pair.
    CONSTRAINT open_item_allocation_pair_unique UNIQUE (
        source_type, source_id, target_type, target_id),
    CONSTRAINT open_item_allocation_order_unique UNIQUE (source_type, source_id, allocation_order),
    CONSTRAINT open_item_allocation_order_sane CHECK (allocation_order >= 0),

    CONSTRAINT open_item_allocation_amount_positive CHECK (amount > 0),
    CONSTRAINT open_item_allocation_amount_has_currency CHECK (
        (amount IS NULL) = (amount_currency IS NULL)),
    CONSTRAINT open_item_allocation_ids_positive CHECK (source_id > 0 AND target_id > 0),
    -- A credit note cannot settle itself.
    CONSTRAINT open_item_allocation_not_self CHECK (
        source_type <> target_type OR source_id <> target_id)
);

COMMENT ON TABLE open_item_allocation IS
    'Brief §6''s open item matching. Says which source settled which document and by how much; posts '
    'nothing, because both ends of it are already in the same control account.';

CREATE FUNCTION open_item_allocation_ends_exist() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    referenced text;
    exists_flag boolean;
BEGIN
    referenced := CASE NEW.source_type
                      WHEN 'SETTLEMENT'      THEN 'settlement'
                      WHEN 'CREDIT_NOTE'     THEN 'credit_note'
                      WHEN 'CUSTOMER_CREDIT' THEN 'customer_credit'
                  END;
    EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I WHERE id = $1)', referenced)
       INTO exists_flag USING NEW.source_id;
    IF NOT exists_flag THEN
        RAISE EXCEPTION 'Allocation names % #% as its source, which does not exist.',
            NEW.source_type, NEW.source_id;
    END IF;

    referenced := CASE NEW.target_type
                      WHEN 'SALES_INVOICE'    THEN 'sales_invoice'
                      WHEN 'PURCHASE_INVOICE' THEN 'purchase_invoice'
                      WHEN 'CREDIT_NOTE'      THEN 'credit_note'
                  END;
    EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I WHERE id = $1)', referenced)
       INTO exists_flag USING NEW.target_id;
    IF NOT exists_flag THEN
        RAISE EXCEPTION 'Allocation names % #% as its target, which does not exist.',
            NEW.target_type, NEW.target_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER open_item_allocation_ends_exist
    BEFORE INSERT OR UPDATE ON open_item_allocation
    FOR EACH ROW EXECUTE FUNCTION open_item_allocation_ends_exist();

CREATE INDEX open_item_allocation_source_idx
    ON open_item_allocation (source_type, source_id, allocation_order);
CREATE INDEX open_item_allocation_target_idx
    ON open_item_allocation (target_type, target_id);

-- ---------------------------------------------------------------------------------------
-- Bank transfers
-- ---------------------------------------------------------------------------------------
-- Brief §4 drops Manager's "Inter Account Transfers" account, which Manager had under Equity — the
-- error the brief corrects. A transfer between two of our own accounts is two asset-account lines and
-- nothing more, which is also why JournalSource marks this the one document-shaped transaction that IS
-- reversible through the ledger alone: it allocates against nothing and moves no stock, so its entry is
-- the whole of it.

CREATE TABLE bank_transfer (
    id                bigserial     PRIMARY KEY,

    from_account_id   bigint        NOT NULL,
    to_account_id     bigint        NOT NULL,

    transfer_date     date          NOT NULL,
    amount            numeric(19,2) NOT NULL,
    amount_currency   char(3)       NOT NULL,

    reference         varchar(100),
    description       varchar(500),

    journal_entry_id  bigint        NOT NULL,

    created_at        timestamptz   NOT NULL DEFAULT now(),
    created_by        varchar(100)  NOT NULL DEFAULT 'system',
    updated_at        timestamptz   NOT NULL DEFAULT now(),
    updated_by        varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT bank_transfer_from_fk FOREIGN KEY (from_account_id) REFERENCES account (id),
    CONSTRAINT bank_transfer_to_fk   FOREIGN KEY (to_account_id)   REFERENCES account (id),
    CONSTRAINT bank_transfer_journal_entry_fk
        FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),

    CONSTRAINT bank_transfer_entry_unique UNIQUE (journal_entry_id),

    -- Moving money from an account to itself is a pair of lines that cancel: it balances, it states
    -- nothing, and it would appear on the account's ledger twice.
    CONSTRAINT bank_transfer_accounts_differ CHECK (from_account_id <> to_account_id),
    CONSTRAINT bank_transfer_amount_positive CHECK (amount > 0),
    CONSTRAINT bank_transfer_amount_has_currency CHECK (
        (amount IS NULL) = (amount_currency IS NULL)),
    CONSTRAINT bank_transfer_reference_not_blank CHECK (
        reference IS NULL OR btrim(reference) <> ''),
    CONSTRAINT bank_transfer_date_is_plausible CHECK (transfer_date >= DATE '2000-01-01')
);

COMMENT ON TABLE bank_transfer IS
    'Money moved between two of our own accounts. Editable in place (Q13) and the one document-shaped '
    'transaction reversible through the ledger alone, since its entry is the whole of it.';

CREATE INDEX bank_transfer_from_idx ON bank_transfer (from_account_id, transfer_date);
CREATE INDEX bank_transfer_to_idx   ON bank_transfer (to_account_id, transfer_date);
CREATE INDEX bank_transfer_date_idx ON bank_transfer (transfer_date, id);
