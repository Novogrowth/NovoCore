-- Step 10: freight / landed cost allocation. Q18, answered as ADR 0010.
--
-- ---------------------------------------------------------------------------------------
-- THE QUESTION, AND WHY IT WAS LEFT OPEN UNTIL NOW
-- ---------------------------------------------------------------------------------------
-- Brief §4 puts freight and duty into `Freight / Landed Cost — Unallocated` on receipt and then
-- allocates them proportionally by value into the lots they delivered, so that a lot's unit cost is
-- what the goods really cost to have on a shelf. V4 created that account and flagged it
-- expected_to_clear; V16 gave a carrier's invoice somewhere to land, as an ordinary expense line
-- pointed at it. Nothing has ever cleared it.
--
-- Q18 is what stopped it: allocation raises a lot's unit cost after the fact, and freight routinely
-- arrives after some of the goods it carried have already been sold. Those units were costed out at
-- the old figure into a cost-of-goods-sold entry that has posted. ADR 0008 already settled that a
-- posting reflecting a physically verified event is not reached back into once other things depend on
-- it — which fixed the SHAPE of Q18's answer without answering it.
--
-- ---------------------------------------------------------------------------------------
-- ADR 0010: A LOT'S SHARE IS SPLIT BY WHAT IS STILL IN IT
-- ---------------------------------------------------------------------------------------
-- Each lot's share of the freight is divided in the proportion the lot itself is divided:
--
--   the part belonging to stock STILL ON HAND   raises that lot's unit cost, normally
--   the part belonging to stock ALREADY GONE    posts to `Landed cost variance`
--
-- The second half is the whole of Q18. It cannot raise a unit cost, because the units it is about are
-- not in the lot any more; and it cannot be added to the cost of goods sold that took them out,
-- because that entry is immutable (ADR 0006) and because re-costing consumption already costed out is
-- exactly what ADR 0008 refuses. So it posts openly, to an account it can be reported from — the
-- exact counterpart of `Purchase price variance`, one account along in the same group.
--
-- A fully-sold lot may therefore still be named in an allocation, and all of its share goes to
-- variance. That is the case Q18 exists for. Refusing it would be worse: the freight was genuinely
-- incurred against those goods, and leaving it in an expected-to-clear account forever states nothing
-- about anything.
--
-- ---------------------------------------------------------------------------------------
-- THE BASIS IS THE RECEIVED COST, WHICH IS WHY THE LOT NOW STORES TWO FIGURES
-- ---------------------------------------------------------------------------------------
-- Proportional-by-value needs a value per lot, and the value it uses is everything the lot received
-- extended at the cost it was RECEIVED at — not at what it is currently carried at.
--
-- The difference only shows up on the second allocation against the same lots, and then it is
-- decisive: if the basis were the carrying cost, the first freight invoice would have moved the
-- proportions the second one divides by, so two invoices covering one shipment would split
-- differently depending on the order they were entered in. Nobody would ever see that happen; they
-- would simply have costs that could not be reproduced.
--
-- So `inventory_lot.unit_cost` becomes `received_unit_cost` and STOPS CHANGING, and a second column
-- accumulates what has been allocated onto it:
--
--   received_unit_cost          what the goods cost. Frozen for the life of the lot.
--   allocated_landed_unit_cost  freight and duty allocated onto one unit since. Starts at zero.
--   (their sum)                 what the lot is CARRIED at — brief §5's "includes allocated landed
--                               costs". Computed on read, never stored.
--
-- Two independent facts and a derived total, rather than a total and one of its parts. Storing the
-- carrying cost as well would be the third number that must agree with the other two — the argument
-- that keeps `normal_balance_side` off `account`, a quantity off a serial-tracked lot, and a balance
-- off every account in this schema.
--
-- It also makes a freight allocation's own arithmetic reconstructible from frozen inputs, which is
-- why `freight_allocation_line` stores no basis: the received cost and the received quantity are both
-- frozen, so the weight each lot carried is recomputable exactly.
--
-- NOTE ON `goods_receipt_line.unit_cost`. It stays what it was and is not the same fact as this one.
-- That column is what a DELIVERY DOCUMENT said, and one half of every GR/IR variance ADR 0008
-- computes; `received_unit_cost` is what a LOT was opened at. They coincide for every lot a Goods
-- Receipt created, and they cannot for the phase 2b opening stock that no delivery created — which is
-- precisely the case a rule reading the delivery document instead would have no answer for.
--
-- ---------------------------------------------------------------------------------------
-- WHAT AN ALLOCATION IS ALLOCATED OUT OF
-- ---------------------------------------------------------------------------------------
-- A PURCHASE INVOICE EXPENSE LINE pointed at `Freight / Landed Cost — Unallocated`, not a bare
-- amount. Naming the source is what makes "how much of this freight is still unallocated" a question
-- with an answer, and what stops an allocation crediting more out of that account than anything put
-- into it. Same shape as a GR/IR match, which names the delivery line it settles.
--
-- One source line per allocation. A freight invoice with several chargeable lines — transport, then
-- customs handling — is allocated once per line, so each line's remainder stays individually
-- answerable. A single allocation may still span MANY LOTS ACROSS MANY PURCHASE INVOICES, which is the
-- ordinary case for a consolidated shipment, and the lots are always NAMED: nothing infers which stock
-- a container held from dates and suppliers (CLAUDE.md rule 7).
--
-- ---------------------------------------------------------------------------------------
-- IMMUTABLE, CORRECTED BY REVERSAL
-- ---------------------------------------------------------------------------------------
-- `FREIGHT_ALLOCATION` is added to journal_entry_source_known below and is deliberately ABSENT from
-- journal_source_is_amendable(), which lists the amendable sources explicitly — so, as with
-- GOODS_RECEIPT in V16, the answer needs no change to that function and the existing test comparing it
-- against the Java enum proves the two still agree.
--
-- The reason is the Goods Receipt's rather than the invoice's: this posting changes what lots are
-- carried at, and every FIFO consumption from that moment on costs at the new figure. Editing the
-- entry would change the accounts without changing the lots.
--
-- Reversal takes the per-unit cost back off each lot and posts the mirror in one transaction, and
-- REFUSES once any of the lots has moved since — checked against `quantity_remaining_at_allocation`,
-- which is the one figure on a line that a later read cannot reconstruct. Stock that left after the
-- allocation was costed out at the raised figure, so the freight is already inside a posted cost of
-- goods sold; crediting Inventory back would be crediting stock that is not there.
--
-- ---------------------------------------------------------------------------------------
-- WHAT IS DELIBERATELY ABSENT
-- ---------------------------------------------------------------------------------------
-- * NO WEIGHT-BASED ALLOCATION. Brief §4 chose proportional-by-value with the approximation stated and
--   accepted. Weight-based would need a weight per product, which nothing in the model has and nothing
--   has asked for.
-- * NO SUGGESTED LOTS. Guessing which lots a shipment carried from the supplier and the dates around
--   it is exactly the silent resolution rule 7 forbids, and it would be wrong for a consolidated
--   container, which is the case this feature exists for.
-- * NO AUTOMATIC ALLOCATION ON RECEIPT. The freight invoice and the delivery arrive independently and
--   usually not together; an allocation is a person saying which goods a carrier's invoice was for.
-- * NO PARTIAL REVERSAL, and no un-allocation of one lot out of a posted document. Refused rather than
--   partly undone — ADR 0008's stance on the Goods Receipt. The correction is a new allocation onto
--   the lots the cost really belonged to.
-- * NO SECOND DECOMPOSITION OF THE SHARE. A line stores the two POSTED halves (capitalised and
--   variance); the share is their sum and the basis is recomputable, so neither is a column.

-- ---------------------------------------------------------------------------------------
-- The landed cost variance account — ADR 0010
-- ---------------------------------------------------------------------------------------
-- In the COGS group immediately after `Purchase price variance`, whose reasoning it inherits whole:
-- gross margin should reflect it, and it is NOT expected_to_clear, because a variance balance is a
-- result to look at rather than a discrepancy waiting to be cleared. Flagging it would put a
-- permanent false positive into phase 8's Clearing Checks.
--
-- STANDARD rather than CONTROL, again as the purchase price variance is: it carries no sub-ledger of
-- its own. Its lines still carry the lot they came from, which the ledger permits on a non-Control
-- account and which `Cost of goods sold` has done since step 8 — knowing which lot a cost came out of
-- is what having lots is for.
--
-- A persistent balance here is a real signal rather than an error: it says freight is routinely being
-- allocated after the goods have sold, which is a purchasing-process question.

ALTER TABLE account
    DROP CONSTRAINT account_system_key_known;

ALTER TABLE account
    ADD CONSTRAINT account_system_key_known CHECK (system_key IS NULL OR system_key IN (
        'ROUNDING_DIFFERENCES',
        'GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING',
        'PURCHASE_PRICE_VARIANCE',
        'LANDED_COST_VARIANCE',
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

INSERT INTO account (group_id, display_order, name, account_type, account_kind,
                     sub_ledger_type, system_key, expected_to_clear)
SELECT g.id, 4, 'Landed cost variance', 'EXPENSE', 'STANDARD', NULL,
       'LANDED_COST_VARIANCE', false
FROM account_group g
WHERE g.name = 'COGS';

-- Fail loudly rather than migrating a chart this file was not written against — V14, V16 and V17's
-- stance, and the reason it exists: every freight allocation resolves this account at runtime.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM account WHERE system_key = 'LANDED_COST_VARIANCE') THEN
        RAISE EXCEPTION
            'V18 expected V4''s "COGS" account group and did not find it. Landed cost variance has '
            'no home, so every freight allocation touching stock that has already sold would fail '
            'at runtime.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM account WHERE system_key = 'FREIGHT_LANDED_COST_UNALLOCATED') THEN
        RAISE EXCEPTION
            'V18 expected V4''s "Freight / Landed Cost — Unallocated" account and did not find it. '
            'It is the account every allocation credits, so there would be nothing to allocate out '
            'of.';
    END IF;
END $$;

-- ---------------------------------------------------------------------------------------
-- The journal gains a Freight Allocation source
-- ---------------------------------------------------------------------------------------
-- Adding a value here is deliberately a migration (V15's comment), so that the correction-policy
-- question gets asked rather than defaulted. It was asked, and the answer is immutable — see the
-- header.

ALTER TABLE journal_entry
    DROP CONSTRAINT journal_entry_source_known;

ALTER TABLE journal_entry
    ADD CONSTRAINT journal_entry_source_known CHECK (source IN (
        'PURCHASE_INVOICE',
        'GOODS_RECEIPT',
        'FREIGHT_ALLOCATION',
        'SALES_INVOICE',
        'CREDIT_NOTE',
        'RECEIPT',
        'PAYMENT',
        'BANK_TRANSFER',
        'MANUAL_JOURNAL_ENTRY',
        'INVENTORY_WRITE_OFF'));

-- No new permission Section. A freight allocation is spending a supplier's invoice; that it moves a
-- lot's cost is the consequence rather than the operation, and PURCHASING is where the two documents
-- it sits between already live.

-- ---------------------------------------------------------------------------------------
-- A lot's cost becomes two figures
-- ---------------------------------------------------------------------------------------
-- The rename is the load-bearing half: `unit_cost` was a name that quietly meant two different things
-- before and after an allocation. `received_unit_cost` says which one it is, and the CHECK below says
-- it can only go up by way of the other column.

ALTER TABLE inventory_lot
    RENAME COLUMN unit_cost TO received_unit_cost;

ALTER TABLE inventory_lot
    RENAME COLUMN unit_cost_currency TO received_unit_cost_currency;

ALTER TABLE inventory_lot
    RENAME CONSTRAINT inventory_lot_unit_cost_not_negative
        TO inventory_lot_received_unit_cost_not_negative;

ALTER TABLE inventory_lot
    RENAME CONSTRAINT inventory_lot_unit_cost_has_currency
        TO inventory_lot_received_unit_cost_has_currency;

-- Added with a default so that existing lots — which have had nothing allocated to them, because
-- nothing could — are backfilled correctly, then the default is dropped. Every later insert states
-- the value, for the reason V11 gives about migrations that silently need an empty table: a default
-- that survives is a value nobody chose.
ALTER TABLE inventory_lot
    ADD COLUMN allocated_landed_unit_cost          numeric(19,6) NOT NULL DEFAULT 0,
    ADD COLUMN allocated_landed_unit_cost_currency char(3);

UPDATE inventory_lot
   SET allocated_landed_unit_cost_currency = received_unit_cost_currency;

ALTER TABLE inventory_lot
    ALTER COLUMN allocated_landed_unit_cost DROP DEFAULT,
    ALTER COLUMN allocated_landed_unit_cost_currency SET NOT NULL;

ALTER TABLE inventory_lot
    -- Zero is the ordinary state and negative is not a fact about any lot: un-allocating more than
    -- was allocated would carry stock below what was paid for it. A freight credit note is the
    -- reversal of the allocation it corrects, not a negative allocation.
    ADD CONSTRAINT inventory_lot_allocated_landed_cost_not_negative
        CHECK (allocated_landed_unit_cost >= 0),

    -- ADR 0005's biconditional, stated even though both sides are NOT NULL, for V12's reason: the
    -- convention is what the next monetary column is read against.
    ADD CONSTRAINT inventory_lot_allocated_landed_cost_has_currency
        CHECK ((allocated_landed_unit_cost IS NULL) = (allocated_landed_unit_cost_currency IS NULL)),

    -- The two halves of one lot's cost are added together on every read, and Money/UnitCost refuse to
    -- add across currencies (ADR 0005). A lot whose halves disagreed would therefore be unreadable
    -- rather than merely wrong, so it is refused here instead.
    ADD CONSTRAINT inventory_lot_cost_halves_share_a_currency
        CHECK (allocated_landed_unit_cost_currency = received_unit_cost_currency);

COMMENT ON COLUMN inventory_lot.received_unit_cost IS
    'What one unit cost when the stock came in. FROZEN for the life of the lot (ADR 0010): it is the '
    'basis every landed-cost allocation is computed against, so a second allocation divides the same '
    'lots in the same proportion as the first. Where a later invoice disagreed, ADR 0008 put the '
    'difference in Purchase price variance rather than here.';

COMMENT ON COLUMN inventory_lot.allocated_landed_unit_cost IS
    'Freight and duty allocated onto one unit of this lot (brief §4, ADR 0010). The lot is CARRIED at '
    'received_unit_cost + this, which is brief §5''s "unit cost includes allocated landed costs" and '
    'is what FIFO costs at. Computed on read; deliberately not a third column.';

-- What a valuation reconciles against when Inventory is higher than the invoices behind it, and what
-- answers "which stock is carrying freight". Partial, because the overwhelming majority of lots carry
-- none.
CREATE INDEX inventory_lot_with_landed_cost_idx
    ON inventory_lot (product_id, acquisition_date, id)
    WHERE allocated_landed_unit_cost > 0;

-- ---------------------------------------------------------------------------------------
-- Freight allocations
-- ---------------------------------------------------------------------------------------

CREATE TABLE freight_allocation (
    id                       bigserial     PRIMARY KEY,

    -- The cost being allocated: one expense line of a purchase invoice, pointed at
    -- `Freight / Landed Cost — Unallocated`. Checked in the service against the account's system key
    -- rather than here, because a CHECK cannot reach another table — the FK guarantees the line
    -- exists, the service guarantees it is the right kind of line.
    purchase_invoice_line_id bigint        NOT NULL,

    -- When the cost meets the goods, which is when the lots' cost actually changes. Not the carrier's
    -- invoice date: that is on the invoice, and the two are routinely weeks apart.
    allocation_date          date          NOT NULL,

    description              varchar(500),

    -- NOT NULL, unlike the goods receipt's. An allocation of nothing is refused before it gets here,
    -- so there is always an entry: something was always debited and something always credited.
    journal_entry_id         bigint        NOT NULL,

    -- Set on the document that CORRECTS another. It carries NO LINES of its own: everything a reader
    -- needs is on the original, which stays exactly as posted — PurchaseInvoice's stance, and what
    -- lets the reversal read the original's per-unit increments to take them back off.
    reversal_of_id           bigint,

    created_at               timestamptz   NOT NULL DEFAULT now(),
    created_by               varchar(100)  NOT NULL DEFAULT 'system',
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    updated_by               varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT freight_allocation_invoice_line_fk
        FOREIGN KEY (purchase_invoice_line_id) REFERENCES purchase_invoice_line (id),
    CONSTRAINT freight_allocation_journal_entry_fk
        FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),
    CONSTRAINT freight_allocation_reversal_of_fk
        FOREIGN KEY (reversal_of_id) REFERENCES freight_allocation (id),

    CONSTRAINT freight_allocation_entry_unique UNIQUE (journal_entry_id),
    CONSTRAINT freight_allocation_reversed_at_most_once UNIQUE (reversal_of_id),
    CONSTRAINT freight_allocation_not_its_own_reversal CHECK (
        reversal_of_id IS NULL OR reversal_of_id <> id),

    CONSTRAINT freight_allocation_date_is_plausible CHECK (
        allocation_date >= DATE '2000-01-01')
);

COMMENT ON TABLE freight_allocation IS
    'Brief §4''s landed cost, allocated out of Freight / Landed Cost — Unallocated and into the lots '
    'it delivered, proportionally by received value. Immutable once posted (ADR 0010); corrected by a '
    'reversing document, which refuses once any of the lots has moved.';

CREATE INDEX freight_allocation_invoice_line_idx
    ON freight_allocation (purchase_invoice_line_id, id);
CREATE INDEX freight_allocation_date_idx ON freight_allocation (allocation_date, id);

CREATE TABLE freight_allocation_line (
    id                                 bigserial     PRIMARY KEY,

    allocation_id                      bigint        NOT NULL,
    line_number                        integer       NOT NULL,

    lot_id                             bigint        NOT NULL,

    -- The one figure on this row a later read cannot reconstruct, and the reason it is stored: the
    -- lot's received quantity and received cost are both frozen, so the weight this lot carried in
    -- the split is recomputable — but what was left in it at that moment is not, and that is exactly
    -- what the split between the two halves below was made from. It is also what a reversal is
    -- checked against.
    quantity_remaining_at_allocation   numeric(19,6) NOT NULL,

    -- THE TWO POSTED HALVES. Q18's answer, per lot. The share is their sum and is deliberately not a
    -- column: a stored total beside its own parts is the second copy of a fact this schema keeps
    -- refusing to create.
    --
    -- capitalised: the part belonging to stock still on hand. Debited to Inventory against this lot,
    -- and the reason its unit cost rose.
    -- variance: the part belonging to stock already gone. Debited to Landed cost variance, because
    -- the cost of goods sold that took those units out has posted and is not reached back into.
    capitalised_amount                 numeric(19,2) NOT NULL,
    capitalised_amount_currency        char(3)       NOT NULL,
    variance_amount                    numeric(19,2) NOT NULL,
    variance_amount_currency           char(3)       NOT NULL,

    -- What this allocation added to one unit of the lot: the whole share over everything the lot
    -- received, rounded once at six decimals with the mode from `ledger.rounding.mode`. Stored rather
    -- than recomputed for goods_receipt_line.unit_cost's reason — that setting is operator-changeable
    -- — and because a reversal has to take back exactly what was applied rather than what would be
    -- computed today.
    landed_unit_cost                   numeric(19,6) NOT NULL,
    landed_unit_cost_currency          char(3)       NOT NULL,

    created_at                         timestamptz   NOT NULL DEFAULT now(),
    created_by                         varchar(100)  NOT NULL DEFAULT 'system',
    updated_at                         timestamptz   NOT NULL DEFAULT now(),
    updated_by                         varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT freight_allocation_line_allocation_fk
        FOREIGN KEY (allocation_id) REFERENCES freight_allocation (id),
    CONSTRAINT freight_allocation_line_lot_fk
        FOREIGN KEY (lot_id) REFERENCES inventory_lot (id),

    CONSTRAINT freight_allocation_line_number_unique UNIQUE (allocation_id, line_number),
    CONSTRAINT freight_allocation_line_number_sane CHECK (line_number >= 0),

    -- One lot appears once. Two rows for one lot would be two weights in the proportional split, so
    -- the lot would take double its share while each row looked individually correct.
    CONSTRAINT freight_allocation_line_lot_unique UNIQUE (allocation_id, lot_id),

    -- Zero is allowed on any of the three and is a real outcome: a lot whose stock has entirely sold
    -- capitalises nothing, a lot still untouched varies nothing, and a share too small to round to a
    -- cent moves nothing at all. Negative is not — the allocated amount is positive and the split of
    -- a positive amount has no negative part.
    CONSTRAINT freight_allocation_line_capitalised_not_negative CHECK (capitalised_amount >= 0),
    CONSTRAINT freight_allocation_line_variance_not_negative CHECK (variance_amount >= 0),
    CONSTRAINT freight_allocation_line_unit_cost_not_negative CHECK (landed_unit_cost >= 0),

    CONSTRAINT freight_allocation_line_remaining_not_negative CHECK (
        quantity_remaining_at_allocation >= 0),

    CONSTRAINT freight_allocation_line_capitalised_has_currency CHECK (
        (capitalised_amount IS NULL) = (capitalised_amount_currency IS NULL)),
    CONSTRAINT freight_allocation_line_variance_has_currency CHECK (
        (variance_amount IS NULL) = (variance_amount_currency IS NULL)),
    CONSTRAINT freight_allocation_line_unit_cost_has_currency CHECK (
        (landed_unit_cost IS NULL) = (landed_unit_cost_currency IS NULL)),

    -- One document, one currency (ADR 0005). The three amounts on a line are the same money split
    -- three ways, so they cannot disagree about which money it is.
    CONSTRAINT freight_allocation_line_one_currency CHECK (
        capitalised_amount_currency = variance_amount_currency
        AND capitalised_amount_currency = landed_unit_cost_currency)
);

COMMENT ON TABLE freight_allocation_line IS
    'One lot''s share of an allocated landed cost, split into the half that raised its carrying value '
    'and the half that could not because the stock had already gone (Q18, ADR 0010).';

CREATE INDEX freight_allocation_line_allocation_idx
    ON freight_allocation_line (allocation_id, line_number);

-- One lot's landed-cost history: what a reader holds when they ask why a lot is carried above what it
-- was invoiced at, and the same shape as stock_consumption_line's lot index.
CREATE INDEX freight_allocation_line_lot_idx ON freight_allocation_line (lot_id, id);
