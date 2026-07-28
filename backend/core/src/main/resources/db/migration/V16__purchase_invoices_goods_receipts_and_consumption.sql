-- Step 8: purchase invoices, goods receipts, GR/IR clearing, purchase price variance, and FIFO
-- consumption. ADR 0004 becomes real here, and ADR 0008 answers the three questions that were
-- blocking it.
--
-- ---------------------------------------------------------------------------------------
-- THE TWO DOCUMENTS, AND WHY THEY ARE TWO
-- ---------------------------------------------------------------------------------------
-- ADR 0004: the GOODS RECEIPT creates inventory lots, not the purchase invoice, and a GR/IR clearing
-- account absorbs the gap between them in whichever order they arrive. Brief §6 makes myDATA the
-- default source of purchase invoices and says its timing may lag physical delivery, so goods
-- routinely arrive before their invoice exists. A design in which the invoice creates stock cannot
-- represent goods sitting on a shelf that nobody has billed us for yet — it shows zero, which then
-- blocks selling them.
--
--   Goods first:   receipt   debit Inventory       credit GR/IR clearing
--                  invoice   debit GR/IR clearing  credit Accounts payable
--   Invoice first: invoice   debit GR/IR clearing  credit Accounts payable
--                  receipt   debit Inventory       credit GR/IR clearing
--
-- Either way GR/IR nets to zero once both halves have landed for a quantity, and a residual balance
-- is a real discrepancy: goods received and not invoiced, or invoiced and not received. Both
-- directions are queryable (`linesAwaitingInvoice` / `linesAwaitingDelivery`), which is what phase 8's
-- Clearing Checks reads.
--
-- ---------------------------------------------------------------------------------------
-- ADR 0008, DECISION 1: A PRICE DIFFERENCE POSTS TO PURCHASE PRICE VARIANCE
-- ---------------------------------------------------------------------------------------
-- ADR 0004 left open what happens when a Goods Receipt precedes its invoice and the invoice then
-- carries a different price. Answered: THE LOT KEEPS THE COST IT WAS RECEIVED AT, and the difference
-- posts to its own account.
--
-- Retroactively re-costing a lot that FIFO has already consumed into posted COGS is the same problem
-- as editing a posted entry, expressed as a number instead of a document — the consumed portion has
-- already been costed out, so either the posted COGS ends up disagreeing with the lot it came from,
-- or an immutable entry (ADR 0006) has to be adjusted. The variance account IS that adjustment,
-- posted openly and somewhere it can be reported from.
--
-- It follows that a variance can only ever arise in the GOODS-FIRST direction. A receipt line matched
-- to an invoice line that already exists takes its unit cost FROM that invoice, so there is nothing
-- to vary — which is why `goods_receipt_line` refuses to state a cost when it names an invoice line.
--
-- Consequence recorded for step 10: Q18's landed-cost allocation must follow the same principle —
-- allocate against the lot's received cost, and do not reach back into consumption already costed
-- out.
--
-- ---------------------------------------------------------------------------------------
-- ADR 0008, DECISION 2 (Q17): STOCK MAY GO NEGATIVE IN AGGREGATE, AND IS FLAGGED
-- ---------------------------------------------------------------------------------------
-- A single lot still cannot go below zero — the V12 CHECK stands. What FIFO cannot fill is recorded
-- as the difference between `stock_consumption.quantity_requested` and `quantity_filled`, and
-- `stockOf` subtracts outstanding shortfalls so the product genuinely reads negative rather than
-- reading zero and hiding it.
--
-- No COGS is posted for the shortfall. There is no lot to take a cost from, and reaching for the last
-- purchase price would be the silent guess CLAUDE.md rule 7 forbids, so cost of goods sold is
-- understated for as long as the shortfall stands and the flag is what says so. A later Goods Receipt
-- does not retro-cost it either — that is decision 1 in reverse; the correction is to reverse the
-- consumption and consume again once there is stock to cost it against.
--
-- ---------------------------------------------------------------------------------------
-- ADR 0008, DECISION 3 (Q39): A GOODS RECEIPT IS IMMUTABLE
-- ---------------------------------------------------------------------------------------
-- `GOODS_RECEIPT` is added to journal_entry_source_known below and is deliberately ABSENT from
-- journal_source_is_amendable(), which lists the amendable sources explicitly — so the answer needs
-- no change to that function, and the existing test comparing it against the Java enum proves the two
-- agree.
--
-- Same reasoning as the inventory write-off, and stronger than the invoice's: the posting reflects a
-- physical stock movement, so editing it would change what the accounts say arrived without changing
-- the lots that arrived. Once a lot exists, FIFO order, its remaining quantity and its units all
-- depend on it.
--
-- ---------------------------------------------------------------------------------------
-- ROUNDING, AND A RESIDUAL THAT IS ACCEPTED RATHER THAN ENGINEERED AWAY
-- ---------------------------------------------------------------------------------------
-- Every posted amount is rounded once, at its own line: a quantity times a six-decimal unit figure,
-- rounded with the mode from `ledger.rounding.mode`. A consequence worth stating rather than
-- discovering: when one receipt line is matched by two invoices, the two rounded portions can sum to
-- a cent more or less than the single rounded amount the receipt credited, and that cent stays in
-- GR/IR clearing.
--
-- Accepted deliberately. GR/IR is `expected_to_clear`, and `ledger.rounding.threshold` already exists
-- to say what residual is small enough to be noise — that is the mechanism brief §6 defines for
-- exactly this, so phase 8's check has what it needs. The alternative, a running-total clearing scheme
-- carrying per-line cleared amounts, adds a column that must agree with the postings and buys a cent.
--
-- The one place a residual is NOT left to chance is the invoice line itself: its variance is computed
-- as the RESIDUAL of net minus the GR/IR portions, so an invoice line's own debits always sum exactly
-- to what the supplier charged. An unbalanced invoice would be refused by the ledger, which is the
-- right place for that to matter.
--
-- ---------------------------------------------------------------------------------------
-- WHAT IS DELIBERATELY ABSENT
-- ---------------------------------------------------------------------------------------
-- * NO `source_id` ON journal_entry, and the step 7 obligation is discharged by deciding against it
--   rather than by forgetting it. The link is stored on the DOCUMENT (`journal_entry_id`, UNIQUE),
--   one direction only, exactly as V15 stores one direction of the reversal link and queries the
--   other. Two reasons: an entry from an immutable source cannot be UPDATEd after posting, so a
--   source_id would have to be known before the document existed; and storing it on both sides would
--   be two copies of one fact, free to disagree — the argument that keeps `normal_balance_side` off
--   `account` and a quantity off a serial-tracked lot. `source` still says what KIND of document
--   produced an entry, which is what Q13's policy is keyed on.
-- * NO AUTOMATIC INVOICE CATEGORISATION. Brief §7's suggest-from-product-then-supplier-then-
--   Unclassified rule is about CHOOSING a destination for an invoice nobody typed, and it belongs with
--   the myDATA import that creates those. Here the destination is stated: an inventory line goes to
--   GR/IR, an expense line goes to the account it names. Nothing lands in Unclassified — Needs Review
--   by accident, which is the failure mode a half-built version of that feature would have.
-- * NO LATER MATCHING OPERATION. A match is made by whichever document is created second. Matching
--   afterwards would need its own journal entry belonging to no document, and an unmatched GR/IR
--   balance is already exactly what ADR 0004 says it is.
-- * NO SERIALIZED CONSUMPTION. Consuming an identified unit means marking it SOLD, and brief §5
--   requires the customer and invoice on it when that happens — step 9. A nullable customer id added
--   here would let a unit be sold to nobody, which is why step 6 left SOLD unreachable.
-- * NO FREIGHT ALLOCATION. Freight on a purchase invoice is an expense line like any other today,
--   pointed at `Freight / Landed Cost — Unallocated` if that is where it belongs. Allocating it into
--   lots is step 10 and Q18.

-- ---------------------------------------------------------------------------------------
-- The purchase price variance account — ADR 0008
-- ---------------------------------------------------------------------------------------
-- In the COGS group, after `Inventory write-off / shrinkage`, so gross margin reflects what was
-- actually paid for the goods rather than what we expected to pay. NOT expected_to_clear: a variance
-- balance is a result to look at, not a discrepancy waiting to be cleared, and flagging it would put
-- a permanent false positive into phase 8's Clearing Checks — the argument V14 made for the two VAT
-- accounts.
--
-- STANDARD rather than CONTROL. It carries no sub-ledger of its own: which supplier a variance came
-- from is answered by the invoice that posted it, and declaring a supplier sub-ledger here would make
-- every variance line reconcilable against a supplier balance it is not part of.

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
        'DEPRECIATION_EXPENSE'));

INSERT INTO account (group_id, display_order, name, account_type, account_kind,
                     sub_ledger_type, system_key, expected_to_clear)
SELECT g.id, 3, 'Purchase price variance', 'EXPENSE', 'STANDARD', NULL,
       'PURCHASE_PRICE_VARIANCE', false
FROM account_group g
WHERE g.name = 'COGS';

-- Fail loudly rather than migrating a chart this file was not written against — V14's stance, and the
-- reason it exists: every purchase invoice posting rule resolves this account at runtime.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM account WHERE system_key = 'PURCHASE_PRICE_VARIANCE') THEN
        RAISE EXCEPTION
            'V16 expected V4''s "COGS" account group and did not find it. Purchase price variance '
            'has no home, so every purchase invoice matched against a delivery would fail at runtime.';
    END IF;
END $$;

-- ---------------------------------------------------------------------------------------
-- Q39: the journal gains a Goods Receipt source
-- ---------------------------------------------------------------------------------------
-- Adding a value here is deliberately a migration (V15's comment), so that the correction-policy
-- question gets asked rather than defaulted. It was asked, and it is Q39.

ALTER TABLE journal_entry
    DROP CONSTRAINT journal_entry_source_known;

ALTER TABLE journal_entry
    ADD CONSTRAINT journal_entry_source_known CHECK (source IN (
        'PURCHASE_INVOICE',
        'GOODS_RECEIPT',
        'SALES_INVOICE',
        'CREDIT_NOTE',
        'RECEIPT',
        'PAYMENT',
        'BANK_TRANSFER',
        'MANUAL_JOURNAL_ENTRY',
        'INVENTORY_WRITE_OFF'));

-- ---------------------------------------------------------------------------------------
-- A permission section for purchasing
-- ---------------------------------------------------------------------------------------
-- Separate from SUPPLIERS for the reason INVENTORY is separate from PRODUCTS: the supplier list is a
-- directory, while a purchase invoice states what we pay for everything we sell. No grants are
-- seeded — access is default-deny.

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
        'SALES_ORDER_FULFILLMENT',
        'BACK_IN_STOCK_REMINDERS'));

-- ---------------------------------------------------------------------------------------
-- Purchase invoices
-- ---------------------------------------------------------------------------------------

CREATE TABLE purchase_invoice (
    id                      bigserial     PRIMARY KEY,

    supplier_id             bigint        NOT NULL,

    -- The SUPPLIER'S document number, not ours. This is somebody else's invoice, which is what makes
    -- it immutable (Q13) and what makes a duplicate detectable — a hazard the myDATA import turns from
    -- theoretical into routine, since the same invoice can arrive from AADE and from a human on the
    -- same morning.
    supplier_invoice_number varchar(60)   NOT NULL,

    -- The supplier's document date, which is also the accounting date of the entry. Not the date it
    -- reached us: a report for the month it was issued in should contain it.
    invoice_date            date          NOT NULL,

    description             varchar(500),

    -- NOT NULL, unlike the goods receipt's. An invoice always creates a payable — a supplier who
    -- invoices zero has not sent an invoice.
    journal_entry_id        bigint        NOT NULL,

    -- Set on the document that CORRECTS another. It carries no lines of its own: everything a reader
    -- needs is on the original, which stays exactly as posted, and duplicating its lines would give
    -- one purchase two statements of itself.
    reversal_of_id          bigint,

    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              varchar(100)  NOT NULL DEFAULT 'system',
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT purchase_invoice_supplier_fk
        FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT purchase_invoice_journal_entry_fk
        FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),
    CONSTRAINT purchase_invoice_reversal_of_fk
        FOREIGN KEY (reversal_of_id) REFERENCES purchase_invoice (id),

    -- One entry belongs to one document, which is what makes the stored direction of the link
    -- sufficient and the queried direction unambiguous.
    CONSTRAINT purchase_invoice_entry_unique UNIQUE (journal_entry_id),
    CONSTRAINT purchase_invoice_reversed_at_most_once UNIQUE (reversal_of_id),
    CONSTRAINT purchase_invoice_not_its_own_reversal CHECK (
        reversal_of_id IS NULL OR reversal_of_id <> id),

    CONSTRAINT purchase_invoice_number_not_blank CHECK (btrim(supplier_invoice_number) <> ''),
    CONSTRAINT purchase_invoice_date_is_plausible CHECK (invoice_date >= DATE '2000-01-01')
);

-- A supplier does not issue the same number twice, so a repeat is a duplicate entry — the hazard the
-- myDATA import turns from theoretical into routine.
--
-- A TRIGGER RATHER THAN A UNIQUE INDEX, because the rule is not row-local and a partial index cannot
-- express it. Two documents legitimately carry the same number: the reversing document deliberately
-- carries the number of the invoice it corrects, since that is the whole of its identity; and once an
-- invoice has been reversed, re-entering it correctly under the same supplier number is the ordinary
-- thing to want, because the commonest reason to reverse one is that it was typed wrong. Whether a row
-- is superseded depends on whether ANOTHER row points at it, which no index over this row's own
-- columns can see. The alternative — a `superseded` flag maintained alongside `reversal_of_id` —
-- would be a second copy of a fact, free to disagree with the first.
--
-- Case-insensitive, so 'inv-1' and 'INV-1' are the one document they obviously are, and so the Java
-- check and this one give the same answer.

CREATE FUNCTION purchase_invoice_number_is_not_a_duplicate() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.reversal_of_id IS NOT NULL THEN
        RETURN NEW;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM purchase_invoice existing
         WHERE existing.supplier_id = NEW.supplier_id
           AND upper(existing.supplier_invoice_number) = upper(NEW.supplier_invoice_number)
           AND existing.id IS DISTINCT FROM NEW.id
           AND existing.reversal_of_id IS NULL
           AND NOT EXISTS (SELECT 1 FROM purchase_invoice reversal
                            WHERE reversal.reversal_of_id = existing.id))
    THEN
        RAISE EXCEPTION
            'Supplier % has already sent invoice "%". A supplier does not issue the same number '
            'twice, so this is a duplicate. Reverse the one already recorded if it was wrong; a '
            'reversed invoice releases its number.',
            NEW.supplier_id, NEW.supplier_invoice_number;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER purchase_invoice_number_is_not_a_duplicate
    BEFORE INSERT OR UPDATE ON purchase_invoice
    FOR EACH ROW EXECUTE FUNCTION purchase_invoice_number_is_not_a_duplicate();

CREATE INDEX purchase_invoice_number_idx
    ON purchase_invoice (supplier_id, upper(supplier_invoice_number));

COMMENT ON TABLE purchase_invoice IS
    'A supplier''s invoice, recorded rather than issued. Immutable once posted (Q13); corrected by a '
    'reversing document. Its inventory lines debit GR/IR clearing, never Inventory (ADR 0004).';

CREATE INDEX purchase_invoice_supplier_idx ON purchase_invoice (supplier_id, invoice_date);
CREATE INDEX purchase_invoice_date_idx ON purchase_invoice (invoice_date, id);

CREATE TABLE purchase_invoice_line (
    id                       bigserial     PRIMARY KEY,

    invoice_id               bigint        NOT NULL,
    line_number              integer       NOT NULL,

    -- A gr.novotrade.novocore.core.api.purchasing.PurchaseLineType name. Not a categorisation scheme:
    -- it is the difference between a line that becomes stock and one that does not, and it decides
    -- where the net is debited.
    line_type                varchar(20)   NOT NULL,

    -- INVENTORY shape.
    product_id               bigint,
    quantity                 numeric(19,6),
    unit_price               numeric(19,6),
    unit_price_currency      char(3),

    -- EXPENSE shape. The account is named by the operator; nothing infers it.
    expense_account_id       bigint,

    -- THE POSTED FIGURES, FROZEN. Unlike stock_write_off, which deliberately stores no amount and
    -- reads it back off the entry, these are stored — and the difference is not inconsistency. A
    -- write-off's amount is purely a consequence of our own posting. An invoice line's net and VAT are
    -- what somebody else's document SAYS, and they have to keep reconciling against what the supplier
    -- and AADE hold, forever. `ledger.rounding.mode` is operator-changeable, so re-deriving them later
    -- could produce a different cent from the one that posted and was agreed.
    net_amount               numeric(19,2) NOT NULL,
    net_amount_currency      char(3)       NOT NULL,
    vat_amount               numeric(19,2) NOT NULL,
    vat_amount_currency      char(3)       NOT NULL,

    -- Q14: the class, never the rate (1040 and 1041 both charge 4%). Exactly one of these two is
    -- present — see the CHECK. A line with neither has no answer to what VAT it carried, and a VAT
    -- return cannot be assembled from a blank; a line with both states two legal positions about the
    -- same money.
    vat_class_id             bigint,
    vat_exemption_reason_id  bigint,

    -- An intra-EU B2B acquisition self-assesses: input VAT and output VAT for the same class and base,
    -- netting to zero in cash while leaving both figures declarable. Q14 settled that this needs no
    -- new structure, and it does not — this flag decides whether the output side is posted.
    reverse_charge           boolean       NOT NULL DEFAULT false,

    -- ADR 0008. The residual of net minus the GR/IR portions, so an invoice line's debits always sum
    -- exactly to what the supplier charged. Zero on any line that matched nothing, and on every
    -- expense line. Carries either sign: an invoice BELOW the expected price is a credit variance, and
    -- forcing it positive would hide the good news along with the bad.
    variance_amount          numeric(19,2) NOT NULL,
    variance_amount_currency char(3)       NOT NULL,

    description              varchar(500),

    created_at               timestamptz   NOT NULL DEFAULT now(),
    created_by               varchar(100)  NOT NULL DEFAULT 'system',
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    updated_by               varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT purchase_invoice_line_invoice_fk
        FOREIGN KEY (invoice_id) REFERENCES purchase_invoice (id),
    CONSTRAINT purchase_invoice_line_product_fk
        FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT purchase_invoice_line_account_fk
        FOREIGN KEY (expense_account_id) REFERENCES account (id),
    CONSTRAINT purchase_invoice_line_vat_class_fk
        FOREIGN KEY (vat_class_id) REFERENCES vat_class (id),
    CONSTRAINT purchase_invoice_line_exemption_fk
        FOREIGN KEY (vat_exemption_reason_id) REFERENCES vat_exemption_reason (id),

    CONSTRAINT purchase_invoice_line_number_unique UNIQUE (invoice_id, line_number),
    CONSTRAINT purchase_invoice_line_number_sane CHECK (line_number >= 0),

    CONSTRAINT purchase_invoice_line_type_known CHECK (line_type IN ('INVENTORY', 'EXPENSE')),

    -- The two shapes, and no third one — V12's rule for lots, applied here. An inventory line is a
    -- quantity at a price and names no account; an expense line names an account and buys no stock.
    CONSTRAINT purchase_invoice_line_shape CHECK (
        (line_type = 'INVENTORY'
         AND product_id IS NOT NULL AND quantity IS NOT NULL AND unit_price IS NOT NULL
         AND expense_account_id IS NULL)
        OR
        (line_type = 'EXPENSE'
         AND product_id IS NULL AND quantity IS NULL AND unit_price IS NULL
         AND expense_account_id IS NOT NULL)),

    CONSTRAINT purchase_invoice_line_quantity_positive CHECK (
        quantity IS NULL OR quantity > 0),
    -- Zero is allowed: a supplier's free sample invoiced at nothing is a real line, exactly as
    -- UnitCost allows a zero-cost lot.
    CONSTRAINT purchase_invoice_line_unit_price_not_negative CHECK (
        unit_price IS NULL OR unit_price >= 0),
    CONSTRAINT purchase_invoice_line_net_not_negative CHECK (net_amount >= 0),
    CONSTRAINT purchase_invoice_line_vat_not_negative CHECK (vat_amount >= 0),

    -- ADR 0005's biconditional, stated for every monetary column even where NOT NULL makes it
    -- unbreakable, because the convention is what the next monetary column is read against.
    CONSTRAINT purchase_invoice_line_unit_price_has_currency CHECK (
        (unit_price IS NULL) = (unit_price_currency IS NULL)),
    CONSTRAINT purchase_invoice_line_net_has_currency CHECK (
        (net_amount IS NULL) = (net_amount_currency IS NULL)),
    CONSTRAINT purchase_invoice_line_vat_has_currency CHECK (
        (vat_amount IS NULL) = (vat_amount_currency IS NULL)),
    CONSTRAINT purchase_invoice_line_variance_has_currency CHECK (
        (variance_amount IS NULL) = (variance_amount_currency IS NULL)),

    CONSTRAINT purchase_invoice_line_vat_treatment_is_stated CHECK (
        (vat_class_id IS NULL) <> (vat_exemption_reason_id IS NULL)),
    CONSTRAINT purchase_invoice_line_reverse_charge_needs_a_rate CHECK (
        reverse_charge = false OR vat_class_id IS NOT NULL),
    -- An exempt line charges no VAT: that is what being outside the scope means.
    CONSTRAINT purchase_invoice_line_exempt_carries_no_vat CHECK (
        vat_exemption_reason_id IS NULL OR vat_amount = 0)
);

COMMENT ON COLUMN purchase_invoice_line.variance_amount IS
    'ADR 0008: what the supplier charged for matched goods, less what those goods were received into '
    'stock at. Posted to Purchase price variance; the lot is never re-costed.';

CREATE INDEX purchase_invoice_line_invoice_idx
    ON purchase_invoice_line (invoice_id, line_number);
CREATE INDEX purchase_invoice_line_product_idx
    ON purchase_invoice_line (product_id) WHERE product_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------
-- Goods receipts
-- ---------------------------------------------------------------------------------------

CREATE TABLE goods_receipt (
    id                   bigserial     PRIMARY KEY,

    -- On the receipt rather than the line: a delivery comes from one supplier. A van carrying two
    -- suppliers' goods is two deliveries that shared a vehicle, and recording them as one would put
    -- both into a single GR/IR position that neither supplier's invoice can clear.
    supplier_id          bigint        NOT NULL,

    -- The supplier's own dispatch note reference, when the delivery came with one. Optional, because
    -- plenty do not, and refusing to record real stock for want of a piece of paper is worse. Not
    -- unique: unlike an invoice number, nothing is reconciled against it.
    delivery_note_number varchar(60),

    -- When the goods physically arrived. Brief §6's whole point is that this is not the invoice date.
    receipt_date         date          NOT NULL,

    description          varchar(500),

    -- NULLABLE, and the case is the write-off's: every line arrived at a unit cost of zero, so nothing
    -- was capitalised and the ledger rightly refuses a zero-amount entry. A supplier's free sample is
    -- a real delivery that recognises nothing.
    journal_entry_id     bigint,

    reversal_of_id       bigint,

    created_at           timestamptz   NOT NULL DEFAULT now(),
    created_by           varchar(100)  NOT NULL DEFAULT 'system',
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    updated_by           varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT goods_receipt_supplier_fk
        FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT goods_receipt_journal_entry_fk
        FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),
    CONSTRAINT goods_receipt_reversal_of_fk
        FOREIGN KEY (reversal_of_id) REFERENCES goods_receipt (id),

    CONSTRAINT goods_receipt_entry_unique UNIQUE (journal_entry_id),
    CONSTRAINT goods_receipt_reversed_at_most_once UNIQUE (reversal_of_id),
    CONSTRAINT goods_receipt_not_its_own_reversal CHECK (
        reversal_of_id IS NULL OR reversal_of_id <> id),

    CONSTRAINT goods_receipt_note_not_blank CHECK (
        delivery_note_number IS NULL OR btrim(delivery_note_number) <> ''),
    CONSTRAINT goods_receipt_date_is_plausible CHECK (receipt_date >= DATE '2000-01-01')
);

COMMENT ON TABLE goods_receipt IS
    'A physical delivery. ADR 0004 makes this the inventory event: every line becomes one lot, and '
    'the receipt debits Inventory against GR/IR clearing. Immutable (Q39, ADR 0008).';

CREATE INDEX goods_receipt_supplier_idx ON goods_receipt (supplier_id, receipt_date);
CREATE INDEX goods_receipt_date_idx ON goods_receipt (receipt_date, id);

CREATE TABLE goods_receipt_line (
    id                       bigserial     PRIMARY KEY,

    receipt_id               bigint        NOT NULL,
    line_number              integer       NOT NULL,

    product_id               bigint        NOT NULL,

    -- One column for both shapes, as stock_write_off does: for a serialized line this is the unit
    -- count, which the lot's units then state independently. Stored here rather than derived because
    -- this is what the delivery said arrived, and the lot is what is left.
    quantity                 numeric(19,6) NOT NULL,

    -- WHAT THIS LINE PUT INTO STOCK, and why it is stored rather than read off the lot: step 10 will
    -- allocate landed costs onto a lot and move its unit cost away from this figure. This is what the
    -- GR/IR credit was made at, and it has to stay recoverable — it is one half of every variance
    -- ADR 0008 computes.
    unit_cost                numeric(19,6) NOT NULL,
    unit_cost_currency       char(3)       NOT NULL,

    -- A gr.novotrade.novocore.core.api.inventory.StockLocation name, for V12's reason: the
    -- authoritative list is the Java enum, because each value has behaviour only NovoCore supplies.
    location                 varchar(20)   NOT NULL,

    -- Set when the invoice arrived FIRST and this delivery was received against it. The cost then
    -- comes from that invoice line, which is why this direction can never produce a variance.
    purchase_invoice_line_id bigint,

    created_at               timestamptz   NOT NULL DEFAULT now(),
    created_by               varchar(100)  NOT NULL DEFAULT 'system',
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    updated_by               varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT goods_receipt_line_receipt_fk
        FOREIGN KEY (receipt_id) REFERENCES goods_receipt (id),
    CONSTRAINT goods_receipt_line_product_fk
        FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT goods_receipt_line_invoice_line_fk
        FOREIGN KEY (purchase_invoice_line_id) REFERENCES purchase_invoice_line (id),

    CONSTRAINT goods_receipt_line_number_unique UNIQUE (receipt_id, line_number),
    CONSTRAINT goods_receipt_line_number_sane CHECK (line_number >= 0),

    CONSTRAINT goods_receipt_line_quantity_positive CHECK (quantity > 0),
    -- Zero allowed, negative not — UnitCost's rule, in the schema.
    CONSTRAINT goods_receipt_line_unit_cost_not_negative CHECK (unit_cost >= 0),
    CONSTRAINT goods_receipt_line_unit_cost_has_currency CHECK (
        (unit_cost IS NULL) = (unit_cost_currency IS NULL)),
    CONSTRAINT goods_receipt_line_location_known CHECK (
        location IN ('INVENTORY', 'SERVICE', 'DAMAGED_GOODS'))
);

CREATE INDEX goods_receipt_line_receipt_idx ON goods_receipt_line (receipt_id, line_number);
CREATE INDEX goods_receipt_line_product_idx ON goods_receipt_line (product_id);

-- ---------------------------------------------------------------------------------------
-- The lot's source document — the step 8 obligation V12 deferred
-- ---------------------------------------------------------------------------------------
-- Brief §5 lists a source document reference on a lot, and V12 deliberately added no nullable column
-- early because there was nothing to point at for a whole step. There is now.
--
-- ONE DIRECTION ONLY. `goods_receipt_line` deliberately has no `lot_id`: the mapping is one-to-one and
-- storing it both ways would be two copies of one fact. It lives on the lot because brief §5 puts it
-- there, and because a lot is the thing a later reader holds when they ask where stock came from.
--
-- NULLABLE, and the null case is real rather than defensive: phase 2b migrates opening stock out of
-- Manager, and no NovoCore delivery ever created it. NULL says that, instead of pretending to a
-- document that does not exist.

ALTER TABLE inventory_lot
    ADD COLUMN goods_receipt_line_id bigint;

ALTER TABLE inventory_lot
    ADD CONSTRAINT inventory_lot_receipt_line_fk
        FOREIGN KEY (goods_receipt_line_id) REFERENCES goods_receipt_line (id);

-- One delivery line becomes exactly one lot. Two lots claiming the same line would each look
-- individually correct while the stock had been counted twice.
ALTER TABLE inventory_lot
    ADD CONSTRAINT inventory_lot_receipt_line_unique UNIQUE (goods_receipt_line_id);

COMMENT ON COLUMN inventory_lot.goods_receipt_line_id IS
    'Brief §5''s source document reference. NULL means no NovoCore Goods Receipt created this lot — '
    'phase 2b''s opening stock, and nothing else once that is done.';

-- ---------------------------------------------------------------------------------------
-- Reversing a delivery of serialized units — a fourth unit status, and a freed serial number
-- ---------------------------------------------------------------------------------------
-- Q39 makes a Goods Receipt correctable only by reversal, and a delivery of serial-tracked machines
-- has to be reversible too, or the answer has a hole in it. The three existing statuses cannot say
-- what happened:
--
--   * IN_STOCK is false — the delivery has been un-made.
--   * SOLD is false and would put revenue where there is none.
--   * WRITTEN_OFF is the worst of the three: it would show shrinkage in the report the single
--     write-off account was chosen over three FOR, describing a loss that never occurred.
--
-- So UNRECEIVED, which says exactly the true thing: this unit was never really ours, because the
-- delivery that brought it in was reversed.
--
-- AND IT FREES THE SERIAL NUMBER. Serial uniqueness across all stock (V12) exists to catch a
-- duplicate scan, and the commonest reason to reverse a delivery is that it was entered wrong — at
-- which point re-entering those same machines correctly must not be blocked by the mistake. The other
-- three statuses all mean "this serial was genuinely ours at some point", which is history worth
-- keeping unique; UNRECEIVED means the record should never have existed. So the constraint becomes a
-- PARTIAL unique index rather than a table constraint.

ALTER TABLE serialized_unit
    DROP CONSTRAINT serialized_unit_status_known;

ALTER TABLE serialized_unit
    ADD CONSTRAINT serialized_unit_status_known CHECK (
        status IN ('IN_STOCK', 'SOLD', 'WRITTEN_OFF', 'UNRECEIVED'));

ALTER TABLE serialized_unit
    DROP CONSTRAINT serialized_unit_serial_number_unique;

CREATE UNIQUE INDEX serialized_unit_serial_number_unique
    ON serialized_unit (serial_number)
    WHERE status <> 'UNRECEIVED';

COMMENT ON COLUMN serialized_unit.status IS
    'IN_STOCK / SOLD / WRITTEN_OFF are units that were genuinely ours. UNRECEIVED means the Goods '
    'Receipt that created this unit was reversed, so the record should never have existed — which is '
    'why it alone does not hold its serial number against a re-entry.';

-- ---------------------------------------------------------------------------------------
-- GR/IR matches
-- ---------------------------------------------------------------------------------------
-- A quantity of one delivery line that one invoice line is paying for. A table rather than a nullable
-- foreign key in either direction, because brief §6 handles partial delivery across several days: one
-- invoice line is routinely settled by several receipts, and a supplier billing in instalments splits
-- one receipt across two invoices. A link either way would make one of those unrepresentable.
--
-- It carries NO MONEY. The variance is computed and stored per INVOICE LINE, as the residual that
-- makes that line's debits sum exactly to what the supplier charged; a per-match figure would be a
-- second decomposition of the same amount, and the two would differ by a cent as soon as rounding got
-- involved. What a match states is the physical fact — this many units — and the two unit figures are
-- read off the lines it joins.

CREATE TABLE gr_ir_match (
    id                       bigserial     PRIMARY KEY,

    purchase_invoice_line_id bigint        NOT NULL,
    goods_receipt_line_id    bigint        NOT NULL,
    quantity                 numeric(19,6) NOT NULL,

    created_at               timestamptz   NOT NULL DEFAULT now(),
    created_by               varchar(100)  NOT NULL DEFAULT 'system',
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    updated_by               varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT gr_ir_match_invoice_line_fk
        FOREIGN KEY (purchase_invoice_line_id) REFERENCES purchase_invoice_line (id),
    CONSTRAINT gr_ir_match_receipt_line_fk
        FOREIGN KEY (goods_receipt_line_id) REFERENCES goods_receipt_line (id),

    -- One pair, one row. Two rows for the same pair would be a second match nobody could tell from a
    -- duplicate, and the quantity is what carries "some of it".
    CONSTRAINT gr_ir_match_pair_unique UNIQUE (purchase_invoice_line_id, goods_receipt_line_id),
    CONSTRAINT gr_ir_match_quantity_positive CHECK (quantity > 0)
);

COMMENT ON TABLE gr_ir_match IS
    'ADR 0004''s open receiving amount, made explicit: how much of a delivery line one invoice line '
    'pays for. Unmatched quantity on either side is what a non-zero GR/IR balance consists of.';

CREATE INDEX gr_ir_match_invoice_line_idx ON gr_ir_match (purchase_invoice_line_id);
CREATE INDEX gr_ir_match_receipt_line_idx ON gr_ir_match (goods_receipt_line_id);

-- ---------------------------------------------------------------------------------------
-- FIFO consumption — Q17
-- ---------------------------------------------------------------------------------------
-- Stock leaving as a COST OF SALE, as against the write-off's loss. Same shape as stock_write_off and
-- for the same reasons: it reduces the lots AND posts, in one transaction, because either alone is
-- worse than neither; it is never deleted; and its reversal is a row of its own rather than an edit.
--
-- WHY IT HAS LINES AND THE WRITE-OFF DOES NOT: a write-off always names its lot, so there is one. A
-- consumption asks for a quantity of a PRODUCT and FIFO decides which lots answer, so which lots and
-- how much of each is the thing this record exists to remember — brief §6's "one line per lot
-- consumed", which is also how the journal entry is shaped.
--
-- `quantity_filled` IS STORED AND THE SHORTFALL IS NOT. The shortfall is requested minus filled, and
-- storing it too would be a third number that must agree with the other two. `quantity_filled` itself
-- is not derived from the lines either — when a lot was carried at zero the line still exists, so
-- summing lines would be right, but a consumption that filled NOTHING has no lines at all and would
-- then be indistinguishable from one nobody recorded.

CREATE TABLE stock_consumption (
    id                 bigserial     PRIMARY KEY,

    product_id         bigint        NOT NULL,

    quantity_requested numeric(19,6) NOT NULL,
    -- Q17: what FIFO could actually take out of lots. Less than requested means aggregate stock has
    -- gone negative, which is allowed and flagged (ADR 0008) rather than blocked — brief §6 already
    -- treats goods arriving after their invoice as routine, so refusing a real sale over paperwork
    -- timing would contradict the design.
    quantity_filled    numeric(19,6) NOT NULL,

    consumption_date   date          NOT NULL,

    -- A gr.novotrade.novocore.core.api.ledger.JournalSource name, restricted to those whose
    -- mayConsumeStock() is true. Restricted rather than free, because consuming reduces lots and posts
    -- cost of goods sold together: an unrestricted source would let anything at all derecognise
    -- inventory as a cost of sale.
    source             varchar(40)   NOT NULL,

    note               varchar(500),

    -- NULL when nothing was posted: every lot consumed was carried at zero, or nothing could be filled
    -- at all. Both are real.
    journal_entry_id   bigint,

    reversal_of_id     bigint,

    created_at         timestamptz   NOT NULL DEFAULT now(),
    created_by         varchar(100)  NOT NULL DEFAULT 'system',
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    updated_by         varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT stock_consumption_product_fk
        FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT stock_consumption_journal_entry_fk
        FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),
    CONSTRAINT stock_consumption_reversal_of_fk
        FOREIGN KEY (reversal_of_id) REFERENCES stock_consumption (id),

    CONSTRAINT stock_consumption_entry_unique UNIQUE (journal_entry_id),
    CONSTRAINT stock_consumption_reversed_at_most_once UNIQUE (reversal_of_id),
    CONSTRAINT stock_consumption_not_its_own_reversal CHECK (
        reversal_of_id IS NULL OR reversal_of_id <> id),

    CONSTRAINT stock_consumption_requested_positive CHECK (quantity_requested > 0),
    CONSTRAINT stock_consumption_filled_within_requested CHECK (
        quantity_filled >= 0 AND quantity_filled <= quantity_requested),

    -- Kept in step with JournalSource.mayConsumeStock(); a test asserts the two agree, the same way
    -- journal_source_is_amendable is held to isAmendable().
    CONSTRAINT stock_consumption_source_may_consume CHECK (source IN ('SALES_INVOICE')),

    CONSTRAINT stock_consumption_note_not_blank CHECK (note IS NULL OR btrim(note) <> ''),
    CONSTRAINT stock_consumption_date_is_plausible CHECK (
        consumption_date >= DATE '2000-01-01')
);

COMMENT ON TABLE stock_consumption IS
    'Stock leaving as a cost of sale, consumed FIFO. quantity_filled below quantity_requested is '
    'Q17''s negative stock: allowed, never silent, and never retro-costed by a later receipt.';

CREATE INDEX stock_consumption_product_idx ON stock_consumption (product_id, consumption_date);
CREATE INDEX stock_consumption_date_idx ON stock_consumption (consumption_date, id);

-- Q17's flag, as an index rather than a scan: the consumptions that drove stock negative and have not
-- been corrected. Phase 8's Clearing Checks reads exactly this.
CREATE INDEX stock_consumption_shortfall_idx ON stock_consumption (product_id, id)
    WHERE quantity_filled < quantity_requested;

CREATE TABLE stock_consumption_line (
    id                 bigserial     PRIMARY KEY,

    consumption_id     bigint        NOT NULL,
    line_number        integer       NOT NULL,

    lot_id             bigint        NOT NULL,
    quantity           numeric(19,6) NOT NULL,

    -- The lot's unit cost AT THE MOMENT IT WAS CONSUMED. Stored for goods_receipt_line.unit_cost's
    -- reason: step 10 will move a lot's cost, and this is what was actually costed out. Recomputing it
    -- from the lot afterwards would give a different answer with nothing to say which was historical.
    unit_cost          numeric(19,6) NOT NULL,
    unit_cost_currency char(3)       NOT NULL,

    created_at         timestamptz   NOT NULL DEFAULT now(),
    created_by         varchar(100)  NOT NULL DEFAULT 'system',
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    updated_by         varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT stock_consumption_line_consumption_fk
        FOREIGN KEY (consumption_id) REFERENCES stock_consumption (id),
    CONSTRAINT stock_consumption_line_lot_fk
        FOREIGN KEY (lot_id) REFERENCES inventory_lot (id),

    CONSTRAINT stock_consumption_line_number_unique UNIQUE (consumption_id, line_number),
    CONSTRAINT stock_consumption_line_number_sane CHECK (line_number >= 0),
    -- One lot appears once in a consumption: FIFO takes what it can from a lot and moves on, so a
    -- second line for the same lot would mean the first did not take what it said it took.
    CONSTRAINT stock_consumption_line_lot_unique UNIQUE (consumption_id, lot_id),

    CONSTRAINT stock_consumption_line_quantity_positive CHECK (quantity > 0),
    CONSTRAINT stock_consumption_line_unit_cost_not_negative CHECK (unit_cost >= 0),
    CONSTRAINT stock_consumption_line_unit_cost_has_currency CHECK (
        (unit_cost IS NULL) = (unit_cost_currency IS NULL))
);

CREATE INDEX stock_consumption_line_consumption_idx
    ON stock_consumption_line (consumption_id, line_number);
-- One lot's consumption history: what the Inventory sub-ledger reconciles a lot's remaining quantity
-- against, and what a reversal reads to put the quantities back where they came from.
CREATE INDEX stock_consumption_line_lot_idx ON stock_consumption_line (lot_id, id);
