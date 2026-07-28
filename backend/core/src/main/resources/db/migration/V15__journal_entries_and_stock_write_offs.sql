-- Step 7: the journal engine. Brief §6's lower layer, and CLAUDE.md rule 6.
--
-- ---------------------------------------------------------------------------------------
-- "DEBITS MUST EQUAL CREDITS, STRUCTURALLY" — WHAT THAT MEANS HERE
-- ---------------------------------------------------------------------------------------
-- CLAUDE.md rule 6 says the invariant is enforced at the persistence layer, not merely validated in
-- a form. A CHECK constraint cannot express it: it spans rows. So it is a DEFERRED CONSTRAINT
-- TRIGGER, which is checked at COMMIT rather than per statement — because an entry is legitimately
-- unbalanced halfway through being written, and a per-statement check would make it impossible to
-- write one at all.
--
-- Deferred is also why this cannot be reduced to stored totals on `journal_entry` with a CHECK
-- comparing them: those totals would be a second copy of what the lines already say, free to
-- disagree with them, which is the argument that keeps `normal_balance_side` off `account` and a
-- quantity off a serial-tracked lot.
--
-- Two triggers, because one is not enough:
--   * on journal_line, so adding, changing or removing a line re-checks its entry;
--   * on journal_entry, because a line-level trigger NEVER FIRES for an entry that has no lines.
--     Without it, an empty entry — or a one-line entry, if the line trigger were the only
--     guard — would be perfectly storable. It also enforces the minimum of two lines: one line
--     cannot balance against anything.
--
-- ---------------------------------------------------------------------------------------
-- A SIDE AND A POSITIVE AMOUNT, NEVER A SIGNED ONE
-- ---------------------------------------------------------------------------------------
-- `side` plus `amount > 0`, not a signed amount and not a debit_amount/credit_amount pair.
--
-- Signed makes rule 6 a question of whether every producer of a line remembered to negate; the
-- named side makes the balance check a sum per side, which no accidental sign can satisfy.
--
-- The two-column pair was rejected for a schema reason as well: ADR 0005 requires every monetary
-- column to carry a char(3) currency companion, and two amount columns would mean either two
-- currency columns that must agree, or one shared one breaking the naming convention
-- SchemaConventionsIT enforces. One amount, one currency, one side.
--
-- ---------------------------------------------------------------------------------------
-- WHY A JOURNAL LINE CARRIES A VAT CLASS AND A TAXABLE BASE — Q14
-- ---------------------------------------------------------------------------------------
-- Q14: VAT is computed PER LINE and summed BY RATE for posting, into separate Output VAT and Input
-- VAT accounts (added in V14) that are never netted.
--
-- "Summed by rate" is what forces these two columns. An invoice with 13% lines and 24% lines posts
-- two Output VAT lines; without the rate on the line they are two indistinguishable amounts against
-- the same account, and one could have posted a single total instead and lost nothing. The rate is
-- therefore a dimension of the posted line, not only of the document behind it.
--
-- It also cannot live only on the invoice, because a Manual Journal Entry can post to a VAT account
-- directly — an accountant's period adjustment is exactly that — and a VAT figure assembled from
-- documents alone would silently omit it.
--
-- The CLASS is stored, never the rate: two seeded classes both charge 4% under different legal bases
-- and different myDATA codes (1040 and 1041), so a rate is ambiguous as an identity.
--
-- PERMITTED, NOT REQUIRED, on the two VAT accounts. Required would break the periodic settlement,
-- which debits Output VAT and credits Input VAT against the bank at no rate at all. Forbidden
-- everywhere else, by trigger, so a rate cannot be attached to a revenue line where nothing reads it.
--
-- An EXEMPT line posts no VAT line at all and its VatExemptionReason belongs on the invoice line
-- (step 9): there is no VAT line to hang it on, which is what being exempt means.
--
-- ---------------------------------------------------------------------------------------
-- Q13: CORRECTION POLICY, ENFORCED IN THE DATABASE
-- ---------------------------------------------------------------------------------------
-- Sales and Purchase Invoices — and by Q26 the credit note that corrects one — are immutable once
-- posted; correction is a reversing entry. Receipts, Payments, Bank Transfers and Manual Journal
-- Entries are editable in place, with the previous state written to the audit log.
--
-- That split is a trigger here rather than a service check alone, because "immutable" that only holds
-- for callers who came through the service is not immutability. The policy is stated once as the
-- function `journal_source_is_amendable`, and a test calls it for every value of the Java
-- `JournalSource` enum and compares against `isAmendable()`, so the two cannot drift.
--
-- NOTHING IS EVER DELETED, from either table, whatever the source. With no period locking (brief §6)
-- there is no point at which the ledger is finished with, and a deleted entry leaves a balance that
-- cannot be explained by anything.
--
-- ---------------------------------------------------------------------------------------
-- WHAT IS DELIBERATELY ABSENT
-- ---------------------------------------------------------------------------------------
-- * NO SOURCE DOCUMENT ID. `source` says WHAT KIND of transaction produced the entry, because the
--   correction policy above is keyed on that and needs it now. There is no `source_id`, because
--   there is no purchase_invoice or receipt table to point at until steps 8 and 9 — the same stance
--   V12 took on a lot's source document, and for the same reason: a nullable column that means
--   nothing for a whole step is worse than one added by the step that can fill it. Step 8 obligation.
-- * NO ENTRY NUMBER. The id is the handle. A human-facing sequential number is a real thing an
--   accountant asks for, and it carries a format decision (per-year reset? prefix per source?) that
--   nobody has been asked. Open question rather than a guessed scheme.
-- * NO STORED ACCOUNT BALANCE, here or on `account`. Step 3 settled that; a balance is the sum of
--   the lines, computed on read.
-- * NO PERIOD LOCKING and therefore no lock table (brief §6, explicit).
-- * NO REVERSED_BY column. One direction of the reversal link is stored and the other is a query.

-- ---------------------------------------------------------------------------------------
-- The correction policy, as a function
-- ---------------------------------------------------------------------------------------
-- IMMUTABLE and STRICT so it can be called from a trigger cheaply, and so a test can call it
-- directly for every value of the Java enum.

CREATE FUNCTION journal_source_is_amendable(p_source varchar) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT AS $$
    SELECT p_source IN (
        -- Our own record of money moving. A mistyped amount is a correction, not a re-issue.
        'RECEIPT',
        'PAYMENT',
        'BANK_TRANSFER',
        'MANUAL_JOURNAL_ENTRY');
    -- Immutable, and absent above on purpose: PURCHASE_INVOICE, SALES_INVOICE, CREDIT_NOTE — each a
    -- document issued to or by somebody else, possibly transmitted to AADE, so its content is a
    -- matter of external record and editing it would make NovoCore disagree with what the
    -- counterparty holds. And INVENTORY_WRITE_OFF, which reduced a lot's quantity: editing the entry
    -- would change the loss recognised without changing the stock it came out of.
$$;

COMMENT ON FUNCTION journal_source_is_amendable(varchar) IS
    'Q13: may a posted entry from this source be edited in place? The single statement of the '
    'policy. JournalSource.isAmendable() in Java must agree, and a test proves it does.';

-- ---------------------------------------------------------------------------------------
-- Journal entries
-- ---------------------------------------------------------------------------------------

CREATE TABLE journal_entry (
    id             bigserial     PRIMARY KEY,

    -- The ACCOUNTING date, which is what every report slices by. Deliberately distinct from
    -- created_at: backdating is ordinary (a stock count finished on the 30th, entered on the 3rd)
    -- and there is no period locking to stop it.
    entry_date     date          NOT NULL,

    description    varchar(500)  NOT NULL,

    -- A gr.novotrade.novocore.core.api.ledger.JournalSource name. Six of the eight have no producer
    -- until steps 8 and 9; they are listed now because Q13's correction policy is already decided
    -- for them and this CHECK is what the policy function above is applied to.
    source         varchar(40)   NOT NULL,

    -- The entry this one reverses. One direction only — see the header.
    reversal_of_id bigint,

    created_at     timestamptz   NOT NULL DEFAULT now(),
    created_by     varchar(100)  NOT NULL DEFAULT 'system',
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    updated_by     varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT journal_entry_reversal_of_fk
        FOREIGN KEY (reversal_of_id) REFERENCES journal_entry (id),

    -- An entry is reversed AT MOST ONCE. Two reversals of one entry would double the correction and
    -- leave the ledger short by the original amount, with both halves looking individually correct.
    CONSTRAINT journal_entry_reversed_at_most_once UNIQUE (reversal_of_id),

    CONSTRAINT journal_entry_not_its_own_reversal CHECK (
        reversal_of_id IS NULL OR reversal_of_id <> id),

    CONSTRAINT journal_entry_description_not_blank CHECK (btrim(description) <> ''),

    -- A floor, not a range. There is no upper bound on purpose: an accrual dated forward is
    -- legitimate and no period locking exists to make it a problem. The floor catches the typo that
    -- a plain "is a date" check cannot — a transposed or two-digit year, which would otherwise sit
    -- quietly in a report nobody runs for the year 202. This is the same lesson V10 recorded about
    -- the VAT rate: a bound only earns its keep if it sits inside the plausible-typo range.
    CONSTRAINT journal_entry_date_is_plausible CHECK (entry_date >= DATE '2000-01-01'),

    CONSTRAINT journal_entry_source_known CHECK (source IN (
        'PURCHASE_INVOICE',
        'SALES_INVOICE',
        'CREDIT_NOTE',
        'RECEIPT',
        'PAYMENT',
        'BANK_TRANSFER',
        'MANUAL_JOURNAL_ENTRY',
        'INVENTORY_WRITE_OFF'))
);

COMMENT ON TABLE journal_entry IS
    'One balanced journal entry. Brief §6''s lower layer: typed transactions generate these. Debits '
    'equal credits by deferred constraint trigger (CLAUDE.md rule 6), never deleted, and editable '
    'in place only when journal_source_is_amendable(source) says so (Q13).';

COMMENT ON COLUMN journal_entry.entry_date IS
    'Accounting date. Reports slice by this, not by created_at, so a backdated entry lands in the '
    'period it belongs to.';

CREATE INDEX journal_entry_date_idx ON journal_entry (entry_date, id);
CREATE INDEX journal_entry_source_idx ON journal_entry (source, entry_date);

-- ---------------------------------------------------------------------------------------
-- Journal lines
-- ---------------------------------------------------------------------------------------

CREATE TABLE journal_line (
    id                    bigserial     PRIMARY KEY,

    entry_id              bigint        NOT NULL,
    -- Position within the entry, so a listing is stable rather than depending on insertion order
    -- surviving. The lesson from V12's serialized units, where one projection returned two orders.
    line_number           integer       NOT NULL,

    account_id            bigint        NOT NULL,

    -- A gr.novotrade.novocore.core.api.account.BalanceSide name.
    side                  varchar(6)    NOT NULL,
    amount                numeric(19,2) NOT NULL,
    amount_currency       char(3)       NOT NULL,

    -- Optional per-line narrative; the entry carries the general explanation.
    description           varchar(500),

    -- Brief §6's sub-ledger linkage. Polymorphic, so NO FOREIGN KEY is possible — a trigger checks
    -- the referenced row exists in the right table instead, because a dangling reference is exactly
    -- what makes a control account unreconcilable, which is the whole reason control accounts exist.
    -- A gr.novotrade.novocore.core.api.shared.SubLedgerType name.
    sub_ledger_type       varchar(20),
    sub_ledger_id         bigint,

    -- Q14. Permitted only on the two VAT accounts; see the header and the trigger.
    vat_class_id          bigint,
    taxable_base          numeric(19,2),
    taxable_base_currency char(3),

    created_at            timestamptz   NOT NULL DEFAULT now(),
    created_by            varchar(100)  NOT NULL DEFAULT 'system',
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    updated_by            varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT journal_line_entry_fk
        FOREIGN KEY (entry_id) REFERENCES journal_entry (id),
    CONSTRAINT journal_line_account_fk
        FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT journal_line_vat_class_fk
        FOREIGN KEY (vat_class_id) REFERENCES vat_class (id),

    -- DEFERRABLE, and this one is not decoration either. Q13's amendment replaces an entry's whole
    -- line list, so line_number 0 is inserted while the old line_number 0 is still present until the
    -- delete lands. Hibernate happens to order orphan removals before inserts today, which would make
    -- an immediate constraint pass; relying on that is relying on an implementation detail of a
    -- library to keep a schema constraint satisfiable. Deferred to commit, the intermediate state is
    -- simply not something anybody has to reason about.
    CONSTRAINT journal_line_number_unique_in_entry UNIQUE (entry_id, line_number)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT journal_line_number_sane CHECK (line_number >= 0),

    CONSTRAINT journal_line_side_known CHECK (side IN ('DEBIT', 'CREDIT')),

    -- Strictly positive. Which way a line moves is said by its side, so a negative amount is a
    -- credit written as a debit; and a zero line states nothing while still balancing, so an entry
    -- padded with them would satisfy rule 6 and mean nothing.
    CONSTRAINT journal_line_amount_positive CHECK (amount > 0),

    -- ADR 0005's biconditional. Both sides are NOT NULL here so it can only hold — stated anyway,
    -- because the convention is what the next monetary column is read against.
    CONSTRAINT journal_line_amount_has_currency CHECK (
        (amount IS NULL) = (amount_currency IS NULL)),

    CONSTRAINT journal_line_sub_ledger_type_known CHECK (sub_ledger_type IS NULL OR sub_ledger_type IN (
        'CUSTOMER', 'SUPPLIER', 'INVENTORY_LOT', 'ASSET')),

    -- A type with no id names a class of thing and not a thing; an id with no type names a number.
    CONSTRAINT journal_line_sub_ledger_parts_go_together CHECK (
        (sub_ledger_type IS NULL) = (sub_ledger_id IS NULL)),
    CONSTRAINT journal_line_sub_ledger_id_positive CHECK (
        sub_ledger_id IS NULL OR sub_ledger_id > 0),

    -- The VAT dimension is a pair. A class with no base cannot be reported on and a base with no
    -- class is a number against nothing.
    CONSTRAINT journal_line_vat_parts_go_together CHECK (
        (vat_class_id IS NULL) = (taxable_base IS NULL)),
    CONSTRAINT journal_line_taxable_base_has_currency CHECK (
        (taxable_base IS NULL) = (taxable_base_currency IS NULL)),
    -- A base of zero yields no VAT and therefore no line at all.
    CONSTRAINT journal_line_taxable_base_positive CHECK (
        taxable_base IS NULL OR taxable_base > 0),
    -- ADR 0005 again: a base in one currency and the VAT in another is not a rate applied to a base.
    CONSTRAINT journal_line_taxable_base_same_currency CHECK (
        taxable_base_currency IS NULL OR taxable_base_currency = amount_currency)
);

COMMENT ON TABLE journal_line IS
    'One side of one line. A named side and a positive amount, never a signed one: that is what makes '
    'the debits=credits check a sum per side, which no accidental sign can satisfy.';

COMMENT ON COLUMN journal_line.sub_ledger_id IS
    'Polymorphic reference, so no FK is possible; journal_line_agrees_with_its_account() checks the '
    'row exists. NovoCore''s own id, never an external system''s (CLAUDE.md rule 2).';

COMMENT ON COLUMN journal_line.vat_class_id IS
    'Q14: the class this line''s VAT was computed at, permitted only on the OUTPUT_VAT and INPUT_VAT '
    'accounts. The class rather than the rate, because 1040 and 1041 both charge 4%.';

CREATE INDEX journal_line_entry_idx ON journal_line (entry_id, line_number);
-- The account ledger and every balance read.
CREATE INDEX journal_line_account_idx ON journal_line (account_id);
-- One customer's ledger, one lot's history: the sub-ledger reconciliation brief §6 requires.
CREATE INDEX journal_line_sub_ledger_idx ON journal_line (sub_ledger_type, sub_ledger_id)
    WHERE sub_ledger_id IS NOT NULL;
-- The VAT totals query. Partial, because only VAT lines carry one and they are a small minority.
CREATE INDEX journal_line_vat_class_idx ON journal_line (vat_class_id)
    WHERE vat_class_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------
-- Rule 6, as a deferred constraint trigger
-- ---------------------------------------------------------------------------------------

CREATE FUNCTION journal_entry_must_balance(p_entry_id bigint) RETURNS void
    LANGUAGE plpgsql AS $$
DECLARE
    line_count   integer;
    currencies   integer;
    total_debit  numeric(19,2);
    total_credit numeric(19,2);
BEGIN
    SELECT count(*),
           count(DISTINCT amount_currency),
           coalesce(sum(amount) FILTER (WHERE side = 'DEBIT'), 0),
           coalesce(sum(amount) FILTER (WHERE side = 'CREDIT'), 0)
      INTO line_count, currencies, total_debit, total_credit
      FROM journal_line
     WHERE entry_id = p_entry_id;

    IF line_count < 2 THEN
        RAISE EXCEPTION
            'Journal entry % has % line(s). An entry needs at least two: one line cannot balance '
            'against anything, and an entry with none is a balance nobody can explain.',
            p_entry_id, line_count;
    END IF;

    -- ADR 0005: NovoCore never converts and never silently picks a currency, so an entry spanning
    -- two of them has no meaningful total and the balance question cannot even be asked.
    IF currencies > 1 THEN
        RAISE EXCEPTION
            'Journal entry % has lines in % different currencies. NovoCore does not convert '
            'between them (ADR 0005), so such an entry has no total to balance.',
            p_entry_id, currencies;
    END IF;

    IF total_debit <> total_credit THEN
        RAISE EXCEPTION
            'Journal entry % does not balance: debits % against credits %, a difference of %. '
            'Debits must equal credits (CLAUDE.md rule 6).',
            p_entry_id, total_debit, total_credit, total_debit - total_credit;
    END IF;
END;
$$;

COMMENT ON FUNCTION journal_entry_must_balance(bigint) IS
    'CLAUDE.md rule 6, structurally. Called by two deferred constraint triggers — one on the lines '
    'and one on the entry, because a line-level trigger never fires for an entry with no lines.';

-- TG_OP is branched on explicitly rather than coalescing NEW and OLD: PL/pgSQL leaves OLD unassigned
-- on INSERT and NEW unassigned on DELETE, and reading a field of an unassigned record is an error
-- rather than a null.
CREATE FUNCTION journal_line_entry_must_balance() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM journal_entry_must_balance(OLD.entry_id);
    ELSE
        PERFORM journal_entry_must_balance(NEW.entry_id);
        -- A line moved between entries leaves the one it left to be re-checked as well.
        IF TG_OP = 'UPDATE' AND OLD.entry_id <> NEW.entry_id THEN
            PERFORM journal_entry_must_balance(OLD.entry_id);
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

CREATE FUNCTION journal_entry_itself_must_balance() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    PERFORM journal_entry_must_balance(NEW.id);
    RETURN NULL;
END;
$$;

-- DEFERRABLE INITIALLY DEFERRED is the load-bearing part: an entry is unbalanced between its first
-- line and its last, so checking per statement would make writing one impossible. Checked at COMMIT,
-- which means it also holds for a hand-written psql transaction.
CREATE CONSTRAINT TRIGGER journal_line_keeps_its_entry_balanced
    AFTER INSERT OR UPDATE OR DELETE ON journal_line
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION journal_line_entry_must_balance();

CREATE CONSTRAINT TRIGGER journal_entry_balances
    AFTER INSERT OR UPDATE ON journal_entry
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION journal_entry_itself_must_balance();

-- ---------------------------------------------------------------------------------------
-- A line must agree with the account it posts to
-- ---------------------------------------------------------------------------------------
-- Brief §6: a sub-ledger reference is REQUIRED on Control-kind account lines and its type must match
-- that account's declared sub-ledger. Not deferred — this is a property of the single row, knowable
-- the moment it is written.
--
-- Note what is NOT required: a sub-ledger reference is PERMITTED on a non-Control account, and is
-- genuinely used there. Cost of goods sold is a Standard account whose lines carry lot references,
-- because brief §6 wants one line per lot consumed. Control-ness governs whether a reference is
-- required, not whether one may be present.

CREATE FUNCTION journal_line_agrees_with_its_account() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    acct_kind       varchar(20);
    acct_sub_ledger varchar(20);
    acct_system_key varchar(60);
    acct_name       varchar(160);
    referenced      text;
    exists_flag     boolean;
BEGIN
    SELECT account_kind, sub_ledger_type, system_key, name
      INTO acct_kind, acct_sub_ledger, acct_system_key, acct_name
      FROM account
     WHERE id = NEW.account_id;

    IF acct_kind = 'CONTROL' THEN
        IF NEW.sub_ledger_type IS NULL THEN
            RAISE EXCEPTION
                'Journal line on control account "%" must carry a % sub-ledger reference '
                '(brief §6). A control account whose lines do not name what they concern cannot '
                'be reconciled against the sub-ledger it exists to summarise.',
                acct_name, acct_sub_ledger;
        END IF;
        IF NEW.sub_ledger_type <> acct_sub_ledger THEN
            RAISE EXCEPTION
                'Journal line on control account "%" carries a % reference, but that account''s '
                'sub-ledger is %. This is what stops a customer reference landing on an accounts '
                'payable line.',
                acct_name, NEW.sub_ledger_type, acct_sub_ledger;
        END IF;
    END IF;

    -- The referenced row has to exist. Dynamic SQL because the reference is polymorphic and
    -- therefore cannot be a foreign key; enforced here rather than in Java because validating it
    -- there would make the ledger depend on the inventory service, which already depends on the
    -- ledger to post a write-off — a bean cycle for a check the database can make directly.
    IF NEW.sub_ledger_id IS NOT NULL THEN
        referenced := CASE NEW.sub_ledger_type
                          WHEN 'CUSTOMER'      THEN 'customer'
                          WHEN 'SUPPLIER'      THEN 'supplier'
                          WHEN 'INVENTORY_LOT' THEN 'inventory_lot'
                          WHEN 'ASSET'         THEN 'asset'
                      END;
        EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I WHERE id = $1)', referenced)
           INTO exists_flag USING NEW.sub_ledger_id;
        IF NOT exists_flag THEN
            RAISE EXCEPTION
                'Journal line references %#%, which does not exist. A dangling sub-ledger '
                'reference is a control account balance that can never be explained.',
                NEW.sub_ledger_type, NEW.sub_ledger_id;
        END IF;
    END IF;

    -- Q14: the VAT dimension belongs only on the two VAT accounts. Anywhere else it is a rate
    -- attached to a line nothing will ever read it from.
    IF NEW.vat_class_id IS NOT NULL
       AND coalesce(acct_system_key, '') NOT IN ('OUTPUT_VAT', 'INPUT_VAT') THEN
        RAISE EXCEPTION
            'Journal line on "%" carries a VAT class, but only the OUTPUT_VAT and INPUT_VAT '
            'accounts may (Q14). The taxable base of a revenue line belongs on the invoice line, '
            'not on the posting.',
            acct_name;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER journal_line_agrees_with_its_account
    BEFORE INSERT OR UPDATE ON journal_line
    FOR EACH ROW EXECUTE FUNCTION journal_line_agrees_with_its_account();

-- ---------------------------------------------------------------------------------------
-- Q13, as triggers: no delete ever, and no edit unless the source allows it
-- ---------------------------------------------------------------------------------------

CREATE FUNCTION journal_entry_respects_correction_policy() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'Journal entry % cannot be deleted. Nothing in the ledger is ever deleted, whatever '
            'its source: with no period locking there is no point at which the ledger is finished '
            'with, and a removed entry leaves a balance nothing can explain. Reverse it instead.',
            OLD.id;
    END IF;

    IF NOT journal_source_is_amendable(OLD.source) THEN
        RAISE EXCEPTION
            'Journal entry % came from a %, which is immutable once posted (Q13). Correct it with '
            'a reversing entry and then the corrected one.',
            OLD.id, OLD.source;
    END IF;

    IF NEW.source <> OLD.source THEN
        RAISE EXCEPTION
            'Journal entry % cannot change source from % to %. The source decides which '
            'correction policy applies, so changing it would let an immutable entry be relabelled '
            'into an editable one.',
            OLD.id, OLD.source, NEW.source;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER journal_entry_respects_correction_policy
    BEFORE UPDATE OR DELETE ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION journal_entry_respects_correction_policy();

CREATE FUNCTION journal_line_respects_correction_policy() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    parent_source varchar(40);
BEGIN
    -- OLD is assigned for both UPDATE and DELETE, which are the only events this trigger covers, so
    -- there is no need to branch on TG_OP to find the entry.
    SELECT source INTO parent_source FROM journal_entry WHERE id = OLD.entry_id;

    IF NOT journal_source_is_amendable(parent_source) THEN
        RAISE EXCEPTION
            'Line % of journal entry % cannot be changed or removed: the entry came from a %, '
            'which is immutable once posted (Q13). Correct it with a reversing entry.',
            OLD.line_number, OLD.entry_id, parent_source;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER journal_line_respects_correction_policy
    BEFORE UPDATE OR DELETE ON journal_line
    FOR EACH ROW EXECUTE FUNCTION journal_line_respects_correction_policy();

-- ---------------------------------------------------------------------------------------
-- Stock write-offs — the step 3 and step 6 obligation
-- ---------------------------------------------------------------------------------------
-- The chart of accounts has ONE `Inventory write-off / shrinkage` account rather than three, on the
-- explicit grounds that which of shrinkage, damage or expiry a write-off was belongs on the
-- transaction. This is that transaction. Without it the single account is strictly less informative
-- than three would have been, and the argument for choosing it disappears.
--
-- WHY IT IS A TABLE AND NOT A FIELD ON THE JOURNAL ENTRY: the reason is domain-specific, and the
-- ledger is generic. A `write_off_reason` column on journal_entry would be NULL on every other kind
-- of entry, and the next transaction type with its own field would add another.
--
-- THE JOURNAL ENTRY IS NULLABLE, and the case is real rather than defensive. A lot may have a unit
-- cost of zero — a supplier's free sample, promotional stock, a warranty replacement received at no
-- charge, all of which UnitCost explicitly allows. Writing that off derecognises nothing, because
-- nothing was carried, and a zero-amount journal entry is refused by journal_line_amount_positive
-- for good reason. So the stock leaves and no entry exists, which is the honest record.
--
-- NO AMOUNT COLUMN. What was posted is in the entry. Storing it again would be a second copy that
-- diverges the day step 10 allocates freight onto a lot and changes its unit cost.

CREATE TABLE stock_write_off (
    id                 bigserial     PRIMARY KEY,

    lot_id             bigint        NOT NULL,
    -- Present for a serialized write-off. Brief §5: it names the specific unit and uses that unit's
    -- own actual cost, with no FIFO logic, because a serialized unit is not pooled stock.
    serialized_unit_id bigint,

    -- 1 for a serialized write-off, by CHECK below. One column serves both shapes because for pooled
    -- stock the quantity is the whole content of the request.
    quantity           numeric(19,6) NOT NULL,

    -- A gr.novotrade.novocore.core.api.inventory.WriteOffReason name. An enum and not free text
    -- (Q25): reportability is the entire reason one account was chosen over three, and free text
    -- gives "damaged", "Damaged" and "ΦΘΟΡΑ" as three categories.
    reason             varchar(20)   NOT NULL,

    write_off_date     date          NOT NULL,

    -- Optional narrative: which units were missing, which courier dropped the box. Deliberately not
    -- a substitute for a reason — WriteOffReason says that if OTHER starts appearing often the fix
    -- is a new value with a name rather than free text, and that stands. Optional for every reason
    -- including OTHER, so nothing is ever blocked on prose.
    note               varchar(500),

    -- NULL when the lot was carried at zero; see the header.
    journal_entry_id   bigint,

    -- Set on a row that RESTORES stock rather than removing it: the correction of a mistaken
    -- write-off. Mirrors journal_entry.reversal_of_id, including being reversible at most once.
    reversal_of_id     bigint,

    created_at         timestamptz   NOT NULL DEFAULT now(),
    created_by         varchar(100)  NOT NULL DEFAULT 'system',
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    updated_by         varchar(100)  NOT NULL DEFAULT 'system',

    CONSTRAINT stock_write_off_lot_fk
        FOREIGN KEY (lot_id) REFERENCES inventory_lot (id),
    CONSTRAINT stock_write_off_unit_fk
        FOREIGN KEY (serialized_unit_id) REFERENCES serialized_unit (id),
    CONSTRAINT stock_write_off_journal_entry_fk
        FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),
    CONSTRAINT stock_write_off_reversal_of_fk
        FOREIGN KEY (reversal_of_id) REFERENCES stock_write_off (id),

    -- One entry belongs to one write-off. Two write-offs claiming the same entry would each look
    -- individually accounted for while only one loss had been recognised.
    CONSTRAINT stock_write_off_entry_unique UNIQUE (journal_entry_id),
    CONSTRAINT stock_write_off_reversed_at_most_once UNIQUE (reversal_of_id),
    CONSTRAINT stock_write_off_not_its_own_reversal CHECK (
        reversal_of_id IS NULL OR reversal_of_id <> id),

    CONSTRAINT stock_write_off_quantity_positive CHECK (quantity > 0),
    -- A serialized write-off is one unit. Anything else means the unit and the quantity disagree.
    CONSTRAINT stock_write_off_serialized_is_one CHECK (
        serialized_unit_id IS NULL OR quantity = 1),

    CONSTRAINT stock_write_off_reason_known CHECK (
        reason IN ('SHRINKAGE', 'DAMAGE', 'EXPIRY', 'OTHER')),

    CONSTRAINT stock_write_off_note_not_blank CHECK (note IS NULL OR btrim(note) <> ''),

    CONSTRAINT stock_write_off_date_is_plausible CHECK (write_off_date >= DATE '2000-01-01')
);

COMMENT ON TABLE stock_write_off IS
    'Stock derecognised without a sale, carrying the WriteOffReason the single Inventory write-off '
    'account was chosen against three for. journal_entry_id is NULL exactly when the lot was carried '
    'at zero cost, which derecognises nothing.';

CREATE INDEX stock_write_off_lot_idx ON stock_write_off (lot_id, id);
CREATE INDEX stock_write_off_date_idx ON stock_write_off (write_off_date, id);
-- Shrinkage against expiry over a quarter: the report that justifies the reason being an enum.
CREATE INDEX stock_write_off_reason_idx ON stock_write_off (reason, write_off_date);

-- ---------------------------------------------------------------------------------------
-- A permission section for the ledger
-- ---------------------------------------------------------------------------------------
-- Separate from CHART_OF_ACCOUNTS for the reason INVENTORY is separate from PRODUCTS: seeing the
-- LIST of accounts is close to harmless, while seeing what has posted to them is every financial
-- figure in the business — and an account's lines carry customer, supplier and lot references, so
-- they carry the cost of every purchase too. Someone maintaining the chart is not necessarily
-- someone who should read the ledger.
--
-- NO GRANTS ARE SEEDED. Access is default-deny, so JOURNAL is invisible to Remote/Order Staff
-- without saying so, and reachable by Owner and Admin at once through the full_access flag.

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
        'SALES_ORDER_FULFILLMENT',
        'BACK_IN_STOCK_REMINDERS'));
