-- Q14, answered: separate Output VAT and Input VAT accounts, never netted.
--
-- ---------------------------------------------------------------------------------------
-- WHAT Q14 SETTLED, AND WHAT THIS MIGRATION IS
-- ---------------------------------------------------------------------------------------
-- The answer to Q14 has four parts. Three of them are behaviour and belong to the journal engine
-- (V15) and to the invoice transactions (steps 8 and 9):
--
--   * VAT is computed PER LINE and summed BY RATE for posting. So a journal line carries the VAT
--     class it was computed at and the base it was computed on — V15's `journal_line.vat_class_id`
--     and `taxable_base`. Without them, two Output VAT lines at different rates are two
--     indistinguishable amounts and "summed by rate" buys nothing.
--   * REVERSE CHARGE (VatStatus.INTRA_EU_B2B) is its own path: self-assessed output and input
--     entries for the same base, netting to zero. Structurally that is two lines in one ordinary
--     entry, one to each of the accounts below, so it needs nothing beyond them.
--   * An EXEMPT line posts no VAT at all — the net amount only — and carries its
--     VatExemptionReason for documentation. That reason lives on the invoice line, not here: there
--     is no VAT line to hang it on, which is the whole point of an exemption.
--
-- The fourth part is the chart of accounts, and that is this migration.
--
-- ---------------------------------------------------------------------------------------
-- WHY `VAT payable` IS REPURPOSED RATHER THAN LEFT ALONGSIDE
-- ---------------------------------------------------------------------------------------
-- V4 seeded a single `VAT payable` account and said in its own comment that one was almost
-- certainly insufficient, pending this question. It was insufficient, and for a specific reason
-- rather than on general principle: netting output against input destroys the two figures a VAT
-- return is made of, and it hides the case that matters most — a period where reclaimable input VAT
-- exceeds output VAT is a refund position, and once the two have been added together that is
-- indistinguishable from a small liability.
--
-- So `VAT payable` becomes the output side and gains a system key, and the input side is added as a
-- new asset account. Repurposing rather than deactivating-and-replacing is safe here for exactly one
-- reason, and it is worth stating so nobody generalises from it: NOTHING HAS POSTED ANYWHERE. The
-- journal does not exist until V15. Once it does, an account that has been posted to is never
-- repurposed — it is deactivated and a new one added, because a rename would retroactively change
-- what past entries appear to have been about.
--
-- ---------------------------------------------------------------------------------------
-- WHY NEITHER IS expected_to_clear
-- ---------------------------------------------------------------------------------------
-- Both accounts do clear, each period, when the return is filed and the net is paid or reclaimed.
-- But `expected_to_clear` means "a residual balance here is a DISCREPANCY", and a balance on either
-- of these between filings is the ordinary state of affairs — it is the VAT accrued so far this
-- period. Flagging them would put a permanent false positive into phase 8's Clearing Checks, which
-- is how a check stops being read.
--
-- There is also deliberately NO THIRD "VAT payable to authorities" account. Settling a period is a
-- movement this chart already expresses: debit Output VAT, credit Input VAT, and the net against the
-- bank. A third account would only be needed if the return were accrued before it was paid, and
-- NovoCore has no filing duty (the external accountant does) so it never accrues one.

-- ---------------------------------------------------------------------------------------
-- Three new system keys
-- ---------------------------------------------------------------------------------------
-- Extending this list is deliberately a migration (V4's comment). Two of the three are Q14's; the
-- third discharges a step 5 obligation.
--
-- DEPRECIATION_EXPENSE: both fixed-asset control accounts already carry a key, so a depreciation
-- posting could find the asset side of itself and would have had to find the expense side BY NAME,
-- which breaks the first time somebody renames it — precisely the failure `system_key` exists to
-- prevent. NOTHING POSTS TO IT YET: whether the periodic depreciation run is Phase 1 scope is still
-- open, and the statutory rates are still with the accountant. This is the handle that run will
-- need, not the run.

ALTER TABLE account
    DROP CONSTRAINT account_system_key_known;

ALTER TABLE account
    ADD CONSTRAINT account_system_key_known CHECK (system_key IS NULL OR system_key IN (
        'ROUNDING_DIFFERENCES',
        'GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING',
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

-- ---------------------------------------------------------------------------------------
-- The output side: V4's `VAT payable`, renamed and keyed
-- ---------------------------------------------------------------------------------------
-- Stays LIABILITY / STANDARD in Current Liabilities at the position it already held. Named for what
-- it contains rather than for its balance: "VAT payable" describes a net position this account
-- deliberately no longer represents.

UPDATE account
SET name       = 'Output VAT — VAT on sales',
    system_key = 'OUTPUT_VAT'
WHERE name = 'VAT payable'
  AND group_id = (SELECT id FROM account_group WHERE name = 'Current Liabilities');

-- Fail loudly rather than migrating a chart that is not the one this file was written against.
-- Rule 8's stance applied to a migration: a seed edit that silently matched nothing is how an
-- environment ends up without the account a posting rule resolves at runtime.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM account WHERE system_key = 'OUTPUT_VAT') THEN
        RAISE EXCEPTION
            'V14 expected V4''s "VAT payable" account in Current Liabilities and did not find it. '
            'Output VAT has no home, so every sales invoice posting rule would fail at runtime.';
    END IF;
END $$;

-- ---------------------------------------------------------------------------------------
-- The input side: a new asset account
-- ---------------------------------------------------------------------------------------
-- ASSET rather than a negative liability, because that is what it is: a claim on the tax authority.
-- It is also what makes the output account's balance mean "VAT we collected" rather than "VAT we
-- happen to owe on balance".
--
-- Placed immediately after Accounts receivable — it is a receivable from the state, and it reads
-- with the other things owed to us rather than after the partner clearing accounts. Inserting by
-- POSITION rather than appending, as V7 established: the later Current Assets rows shift down one.

UPDATE account
SET display_order = display_order + 1
WHERE group_id = (SELECT id FROM account_group WHERE name = 'Current Assets')
  AND display_order >= 2;

INSERT INTO account (group_id, display_order, name, account_type, account_kind,
                     sub_ledger_type, system_key, expected_to_clear)
SELECT g.id, 2, 'Input VAT — VAT on purchases', 'ASSET', 'STANDARD', NULL, 'INPUT_VAT', false
FROM account_group g
WHERE g.name = 'Current Assets';

-- ---------------------------------------------------------------------------------------
-- The depreciation expense account gains its key
-- ---------------------------------------------------------------------------------------

UPDATE account
SET system_key = 'DEPRECIATION_EXPENSE'
WHERE name = 'Depreciation'
  AND group_id = (SELECT id FROM account_group WHERE name = 'Depreciation & Amortization');

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM account WHERE system_key = 'DEPRECIATION_EXPENSE') THEN
        RAISE EXCEPTION
            'V14 expected V4''s "Depreciation" account and did not find it.';
    END IF;
END $$;

COMMENT ON COLUMN account.system_key IS
    'Stable identifier for accounts NovoCore''s own posting rules locate. NULL for accounts a '
    'human chooses. Never key off code or name instead: code is blank and name is editable. '
    'OUTPUT_VAT and INPUT_VAT are Q14''s — separate and never netted.';
