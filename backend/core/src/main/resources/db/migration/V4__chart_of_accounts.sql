-- The chart of accounts: two tables and the real seed content.
--
-- ---------------------------------------------------------------------------------------
-- SHAPE
-- ---------------------------------------------------------------------------------------
-- Two levels, a group holding accounts, and no deeper. Not a self-referencing account tree:
-- arbitrary nesting would have to be rendered generically by every report, and no level
-- beyond these two has been asked for.
--
-- The group is a real table rather than a text label on `account` because ordering is
-- manual/drag-and-drop (brief §4) and a group's position has to be stored somewhere. That is
-- the whole justification — if ordering were alphabetical, a label would have done.
--
-- Enumerated values are varchar with a CHECK rather than a PostgreSQL enum type. Hibernate
-- maps a Java enum to varchar with EnumType.STRING and validates that mapping under
-- ddl-auto=validate; a native enum type reports through JDBC metadata as its own type name
-- and does not match, which is the same reason V1 rejected DOMAINs. The CHECK is what keeps
-- the values honest, and it deliberately fails loudly on an unknown one.
--
-- ---------------------------------------------------------------------------------------
-- WHAT IS NOT HERE
-- ---------------------------------------------------------------------------------------
-- No `normal_balance_side` column. It is derived from the account type in Java
-- (AccountType.normalBalance) and never stored. Two columns that must agree are two columns
-- that can disagree, and a fixed-asset account saved as credit-normal would misreport the
-- balance sheet with nothing to catch it.
--
-- No EBITDA, EBIT or net profit accounts. Manager.io carried them; they are computed
-- subtotals, and an account nothing can post to would show a balance nobody created.
--
-- No "Inter Account Transfers" (brief §4: a transfer between own bank accounts is two
-- Asset-account entries, and Manager had it under Equity — the error the brief corrects) and
-- no "DDP", superseded by Freight / Landed Cost — Unallocated. If either carries a balance in
-- Manager, the phase 2b migration needs a destination for it.
--
-- No account codes. The column exists and every value is NULL: codes and ΕΛΠ mapping come
-- from the accountant later. Nothing may key off `code` in the meantime — see `system_key`.

-- ---------------------------------------------------------------------------------------
-- Groups
-- ---------------------------------------------------------------------------------------

CREATE TABLE account_group (
    id             bigserial    PRIMARY KEY,
    name           varchar(120) NOT NULL,
    -- Sparse and hand-assigned, not contiguous. Reordering rewrites these.
    display_order  integer      NOT NULL,

    created_at     timestamptz  NOT NULL DEFAULT now(),
    created_by     varchar(100) NOT NULL DEFAULT 'system',
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    updated_by     varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT account_group_name_unique        UNIQUE (name),
    CONSTRAINT account_group_name_not_blank     CHECK (btrim(name) <> ''),
    CONSTRAINT account_group_display_order_sane CHECK (display_order >= 0)
);

COMMENT ON TABLE account_group IS
    'Top level of the two-level chart of accounts. An entity rather than a label because '
    'group ordering is manual and has to be stored.';

-- ---------------------------------------------------------------------------------------
-- Accounts
-- ---------------------------------------------------------------------------------------

CREATE TABLE account (
    id                bigserial    PRIMARY KEY,

    -- Always NULL for now; see the header. Unique when present, so the constraint is already
    -- in place for when the accountant supplies codes. UNIQUE permits many NULLs, and the
    -- blank check below is what stops '' being used as "no code" — two such rows would
    -- collide on the unique index for a reason nobody would guess from the error.
    code              varchar(20),
    name              varchar(160) NOT NULL,

    account_type      varchar(20)  NOT NULL,
    account_kind      varchar(20)  NOT NULL,
    -- Present exactly when the kind is CONTROL; see account_control_iff_sub_ledger below.
    sub_ledger_type   varchar(20),

    -- Stable machine identifier for the accounts NovoCore's own posting rules must locate:
    -- the rounding account (brief §6), GR/IR clearing (ADR 0004), Freight — Unallocated
    -- (brief §4), Unclassified — Needs Review (brief §7), COGS, and the control accounts.
    -- Needed because `code` is blank and `name` is operator-editable, so a posting rule that
    -- looked either up would break the first time someone renamed an account.
    system_key        varchar(60),

    group_id          bigint       NOT NULL,
    display_order     integer      NOT NULL,

    active            boolean      NOT NULL DEFAULT true,
    -- True when a residual balance here is a real discrepancy rather than a normal standing
    -- balance. A flag rather than a fifth kind, because it is orthogonal to how an account
    -- behaves: GR/IR clearing is a Control account that must also clear. Clearing Checks in
    -- phase 8 reads this instead of carrying a hardcoded list of accounts to watch.
    expected_to_clear boolean      NOT NULL DEFAULT false,

    -- ΕΛΠ (Ν. 4308/2014) mapping. NULL until the accountant supplies it.
    elp_code          varchar(20),

    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        varchar(100) NOT NULL DEFAULT 'system',
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        varchar(100) NOT NULL DEFAULT 'system',

    CONSTRAINT account_group_fk FOREIGN KEY (group_id) REFERENCES account_group (id),

    -- Unique within a group, not globally: "Other selling expenses" and "Other general
    -- expenses" are distinct here only because they were named distinctly, and a global
    -- constraint would block a legitimate future addition for no gain.
    CONSTRAINT account_name_unique_in_group UNIQUE (group_id, name),
    CONSTRAINT account_code_unique          UNIQUE (code),
    CONSTRAINT account_system_key_unique    UNIQUE (system_key),

    CONSTRAINT account_name_not_blank       CHECK (btrim(name) <> ''),
    CONSTRAINT account_code_not_blank       CHECK (code IS NULL OR btrim(code) <> ''),
    CONSTRAINT account_elp_code_not_blank   CHECK (elp_code IS NULL OR btrim(elp_code) <> ''),
    CONSTRAINT account_display_order_sane   CHECK (display_order >= 0),

    -- CONTRA_ASSET and CONTRA_INCOME are not decoration. Accumulated depreciation is an
    -- asset-classified account with a credit normal balance: typed as ASSET it would derive
    -- debit-normal, flipping every depreciation posting and reporting fixed assets at roughly
    -- double carrying value. Sales returns are the same problem one statement down — they must
    -- reduce revenue while staying visible as returns, which neither INCOME nor EXPENSE gives.
    CONSTRAINT account_type_known CHECK (account_type IN (
        'ASSET', 'CONTRA_ASSET', 'LIABILITY', 'EQUITY',
        'INCOME', 'CONTRA_INCOME', 'EXPENSE')),

    CONSTRAINT account_kind_known CHECK (account_kind IN (
        'STANDARD', 'BANK_CASH', 'PARTNER_CLEARING', 'CONTROL')),

    CONSTRAINT account_sub_ledger_type_known CHECK (sub_ledger_type IS NULL OR sub_ledger_type IN (
        'CUSTOMER', 'SUPPLIER', 'INVENTORY_LOT', 'ASSET')),

    -- Biconditional, not two one-way checks. A Control account with no sub-ledger has nothing
    -- to reconcile against; a sub-ledger declared on a non-Control account is a rule that
    -- would never be enforced on its lines. Both directions are wrong, so both are refused.
    CONSTRAINT account_control_iff_sub_ledger CHECK (
        (account_kind = 'CONTROL') = (sub_ledger_type IS NOT NULL)),

    -- Extending this list is deliberately a migration. A system key is a promise that the
    -- account exists, is active, and cannot be deleted, so gaining one should be a considered
    -- act rather than a string someone typed into a seed.
    CONSTRAINT account_system_key_known CHECK (system_key IS NULL OR system_key IN (
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
        'COST_OF_GOODS_SOLD'))
);

COMMENT ON COLUMN account.system_key IS
    'Stable identifier for accounts NovoCore''s own posting rules locate. NULL for accounts a '
    'human chooses. Never key off code or name instead: code is blank and name is editable.';

COMMENT ON COLUMN account.expected_to_clear IS
    'A residual balance here is a discrepancy, not a standing balance. Read by Clearing Checks.';

CREATE INDEX account_group_display_idx ON account (group_id, display_order);
-- Partial: the overwhelmingly common read is "the accounts someone may post to now".
CREATE INDEX account_active_idx ON account (active) WHERE active;

-- ---------------------------------------------------------------------------------------
-- Seed: groups
-- ---------------------------------------------------------------------------------------
-- Order runs down the balance sheet, then down the profit and loss. Finance Costs sits last,
-- below Depreciation & Amortization, so that EBITDA and EBIT remain meaningful subtotals:
-- interest above them would make both wrong by definition.

INSERT INTO account_group (name, display_order) VALUES
    ('Cash & Cash Equivalents',    0),
    ('Current Assets',             1),
    ('Non-Current Assets',         2),
    ('Current Liabilities',        3),
    ('Non-Current Liabilities',    4),
    ('Equity',                     5),
    ('Income',                     6),
    ('COGS',                       7),
    ('Selling Expenses',           8),
    ('General Expenses',           9),
    ('Administrative Expenses',   10),
    ('Depreciation & Amortization', 11),
    ('Finance Costs',             12);

-- ---------------------------------------------------------------------------------------
-- Seed: accounts
-- ---------------------------------------------------------------------------------------
-- Sourced from the real Manager.io export, plus the accounts that map from nothing in
-- Manager. Joined to the group by name rather than hardcoding generated ids, which would
-- break the moment this file were run against a database whose sequence had moved.
--
-- display_order restarts within each group and is written out explicitly rather than derived
-- from row position: the ordering is a deliberate editorial choice ("Cash" above "PayPal"
-- regardless of spelling), so it is stated, not inferred from the order of a VALUES list.

INSERT INTO account (
    group_id, display_order, name, account_type, account_kind,
    sub_ledger_type, system_key, expected_to_clear)
SELECT g.id, seed.display_order, seed.name, seed.account_type, seed.account_kind,
       seed.sub_ledger_type, seed.system_key, seed.expected_to_clear
FROM (VALUES
    -- Cash & Cash Equivalents ------------------------------------------------------------
    -- PayPal and Stripe sit here but are PARTNER_CLEARING, not BANK_CASH: the balance is held
    -- by a processor and clears on their remittance, not against a bank statement line. This
    -- placement was an explicit decision after the alternative was raised. The consequence is
    -- that processor fees post as expense on receipt. The accountant may prefer processor
    -- balances presented as receivables rather than cash equivalents; that is presentation,
    -- and moving them later is a group change, not a restructuring.
    ('Cash & Cash Equivalents',  0, 'Cash',                            'ASSET',  'BANK_CASH',        NULL,            NULL, false),
    ('Cash & Cash Equivalents',  1, 'Alpha Bank',                      'ASSET',  'BANK_CASH',        NULL,            NULL, false),
    ('Cash & Cash Equivalents',  2, 'Piraeus Bank',                    'ASSET',  'BANK_CASH',        NULL,            NULL, false),
    ('Cash & Cash Equivalents',  3, 'National Bank of Greece',         'ASSET',  'BANK_CASH',        NULL,            NULL, false),
    ('Cash & Cash Equivalents',  4, 'PayPal',                          'ASSET',  'PARTNER_CLEARING', NULL,            NULL, false),
    ('Cash & Cash Equivalents',  5, 'Stripe',                          'ASSET',  'PARTNER_CLEARING', NULL,            NULL, false),

    -- Current Assets ---------------------------------------------------------------------
    ('Current Assets',           0, 'Inventory',                       'ASSET',  'CONTROL',          'INVENTORY_LOT', 'INVENTORY',                      false),
    ('Current Assets',           1, 'Accounts receivable',             'ASSET',  'CONTROL',          'CUSTOMER',      'ACCOUNTS_RECEIVABLE',            false),
    -- Asset-side, not COGS (brief §4): freight debits here, then allocates proportionally by
    -- value into the relevant lots' unit cost, crediting this back to zero.
    ('Current Assets',           2, 'Freight / Landed Cost — Unallocated', 'ASSET', 'STANDARD',      NULL,            'FREIGHT_LANDED_COST_UNALLOCATED', true),
    -- Replaces Manager's "Suspense". Named for what it demands rather than what it is.
    ('Current Assets',           3, 'Unclassified — Needs Review',      'ASSET',  'STANDARD',         NULL,            'UNCLASSIFIED_NEEDS_REVIEW',      true),
    ('Current Assets',           4, 'Partner Clearing — Skroutz',       'ASSET',  'PARTNER_CLEARING', NULL,            NULL, false),
    ('Current Assets',           5, 'Partner Clearing — ACS Courier',   'ASSET',  'PARTNER_CLEARING', NULL,            NULL, false),
    ('Current Assets',           6, 'Partner Clearing — POS provider',  'ASSET',  'PARTNER_CLEARING', NULL,            NULL, false),

    -- Non-Current Assets -----------------------------------------------------------------
    ('Non-Current Assets',       0, 'Fixed assets at cost',            'ASSET',  'CONTROL',          'ASSET',         'FIXED_ASSETS_AT_COST',           false),
    -- The reason CONTRA_ASSET exists. Asset-classified, credit-normal, and also a Control
    -- account — which is why kind and type are separate dimensions rather than one enum.
    ('Non-Current Assets',       1, 'Fixed assets accumulated depreciation', 'CONTRA_ASSET', 'CONTROL', 'ASSET',      'FIXED_ASSETS_ACCUMULATED_DEPRECIATION', false),
    ('Non-Current Assets',       2, 'Security deposits',               'ASSET',  'STANDARD',         NULL,            NULL, false),

    -- Current Liabilities ----------------------------------------------------------------
    ('Current Liabilities',      0, 'Accounts payable',                'LIABILITY', 'CONTROL',       'SUPPLIER',      'ACCOUNTS_PAYABLE',               false),
    -- ADR 0004: a Goods Receipt creates lots before its invoice exists, so this absorbs the
    -- timing gap in either direction and must return to zero once both sides have arrived.
    ('Current Liabilities',      1, 'Goods Received / Invoice Received clearing', 'LIABILITY', 'CONTROL', 'SUPPLIER', 'GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING', true),
    -- A single VAT account is almost certainly insufficient. Left as one pending the VAT
    -- posting design (PROGRESS.md Q14), which is a real gap rather than a clarification.
    ('Current Liabilities',      2, 'VAT payable',                     'LIABILITY', 'STANDARD',      NULL,            NULL, false),
    ('Current Liabilities',      3, 'Income tax payable',              'LIABILITY', 'STANDARD',      NULL,            NULL, false),
    ('Current Liabilities',      4, 'Social security payable',         'LIABILITY', 'STANDARD',      NULL,            NULL, false),
    ('Current Liabilities',      5, 'Other taxes payable',             'LIABILITY', 'STANDARD',      NULL,            NULL, false),
    ('Current Liabilities',      6, 'Dividends payable',               'LIABILITY', 'STANDARD',      NULL,            NULL, false),
    ('Current Liabilities',      7, 'Employee clearing account',       'LIABILITY', 'STANDARD',      NULL,            NULL, false),

    -- Non-Current Liabilities ------------------------------------------------------------
    -- No current-portion split. Proper practice moves the next twelve months of principal into
    -- Current Liabilities; that needs the repayment schedule and was not requested.
    ('Non-Current Liabilities',  0, 'NBG loan',                        'LIABILITY', 'STANDARD',      NULL,            NULL, false),

    -- Equity -----------------------------------------------------------------------------
    ('Equity',                   0, 'Common stock',                    'EQUITY', 'STANDARD',         NULL,            NULL, false),
    ('Equity',                   1, 'Retained earnings',               'EQUITY', 'STANDARD',         NULL,            NULL, false),

    -- Income -----------------------------------------------------------------------------
    -- Split by channel per brief §4. The three channels mirror the per-channel customer
    -- matching in brief §5: walk-in/phone, website, Skroutz. "Store & Phone" is named for what
    -- it contains — phone orders are neither walk-in nor eCommerce, and leaving that to an
    -- unwritten convention is how they end up counted twice or not at all.
    ('Income',                   0, 'Sales — Store & Phone',           'INCOME', 'STANDARD',         NULL,            NULL, false),
    ('Income',                   1, 'Sales — eCommerce',               'INCOME', 'STANDARD',         NULL,            NULL, false),
    ('Income',                   2, 'Sales — Skroutz',                 'INCOME', 'STANDARD',         NULL,            NULL, false),
    -- Contra-revenue, one per channel. Credit notes debit these rather than netting into the
    -- Sales accounts, so return rate stays visible per channel instead of being absorbed into
    -- net revenue. One shared returns account would have collapsed exactly that.
    ('Income',                   3, 'Sales returns — Store & Phone',   'CONTRA_INCOME', 'STANDARD',  NULL,            NULL, false),
    ('Income',                   4, 'Sales returns — eCommerce',       'CONTRA_INCOME', 'STANDARD',  NULL,            NULL, false),
    ('Income',                   5, 'Sales returns — Skroutz',         'CONTRA_INCOME', 'STANDARD',  NULL,            NULL, false),
    ('Income',                   6, 'Services',                        'INCOME', 'STANDARD',         NULL,            NULL, false),
    ('Income',                   7, 'Subsidies',                       'INCOME', 'STANDARD',         NULL,            NULL, false),
    -- Kept in Income, above EBITDA, as Manager has it. EBITDA is therefore approximate.
    -- Reversible: moving it under Finance Costs is a group change.
    ('Income',                   8, 'Interest received',               'INCOME', 'STANDARD',         NULL,            NULL, false),
    ('Income',                   9, 'Other income',                    'INCOME', 'STANDARD',         NULL,            NULL, false),

    -- COGS -------------------------------------------------------------------------------
    -- Cost of goods sold is not a Control account, but its lines still carry Inventory-Lot
    -- sub-ledger references: brief §6 requires one line per lot consumed, because FIFO needs
    -- to know which lot a cost came from. Control-ness governs whether a reference is
    -- *required* on every line; it does not govern whether one may be present.
    ('COGS',                     0, 'Cost of goods sold',              'EXPENSE', 'STANDARD',        NULL,            'COST_OF_GOODS_SOLD',             false),
    ('COGS',                     1, 'Cost of service sold',            'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    -- Inventory derecognised without a sale: shrinkage, damage, expiry. In the COGS group so
    -- that gross margin reflects the loss honestly, but a separate account so sale-driven COGS
    -- stays uncontaminated. One account rather than three — which of the three a given
    -- write-off was belongs on the transaction as a reason, not in the chart of accounts.
    -- That reason field is a step 6 obligation, recorded in PROGRESS.md.
    --
    -- Distinct from the "Damaged Goods" inventory Location (brief §6). The Location says stock
    -- is unsellable but still an asset; this account is where it goes when it is written off
    -- and leaves the balance sheet. Moving a lot to Damaged Goods posts nothing.
    ('COGS',                     2, 'Inventory write-off / shrinkage', 'EXPENSE', 'STANDARD',        NULL,            'INVENTORY_WRITE_OFF',            false),

    -- Selling Expenses -------------------------------------------------------------------
    ('Selling Expenses',         0, 'Sales commissions',               'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('Selling Expenses',         1, 'Advertising budget',              'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('Selling Expenses',         2, 'Marketing software',              'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('Selling Expenses',         3, 'Transportation costs',            'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('Selling Expenses',         4, 'Packaging materials',             'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('Selling Expenses',         5, 'Bank commissions',                'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('Selling Expenses',         6, 'Other selling expenses',          'EXPENSE', 'STANDARD',        NULL,            NULL, false),

    -- General Expenses -------------------------------------------------------------------
    ('General Expenses',         0, 'Salaries',                        'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('General Expenses',         1, 'Social security',                 'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('General Expenses',         2, 'Rent',                            'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('General Expenses',         3, 'Electricity',                     'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('General Expenses',         4, 'Phone and internet',              'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('General Expenses',         5, 'AI stack',                        'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('General Expenses',         6, 'ERP',                             'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    -- Stock consumed by the business itself — staff coffee, demos, tastings. Deliberately not
    -- a write-off: it is a real cost of operating, not a loss.
    ('General Expenses',         7, 'Internal consumption',            'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    -- Where sub-threshold rounding residuals post automatically (brief §6, and the
    -- ledger.rounding.threshold setting seeded in V2, which until now named an account that
    -- did not exist). An expense account rather than an asset or liability because it
    -- legitimately carries a balance on either side depending on which way the residuals fell.
    ('General Expenses',         8, 'Rounding differences',            'EXPENSE', 'STANDARD',        NULL,            'ROUNDING_DIFFERENCES',           false),
    ('General Expenses',         9, 'Other general expenses',          'EXPENSE', 'STANDARD',        NULL,            NULL, false),

    -- Administrative Expenses ------------------------------------------------------------
    ('Administrative Expenses',  0, 'Management fees',                 'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('Administrative Expenses',  1, 'Accounting fees',                 'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('Administrative Expenses',  2, 'Legal fees',                      'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('Administrative Expenses',  3, 'Business trips',                  'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    ('Administrative Expenses',  4, 'Miscellaneous',                   'EXPENSE', 'STANDARD',        NULL,            NULL, false),

    -- Depreciation & Amortization --------------------------------------------------------
    ('Depreciation & Amortization', 0, 'Depreciation',                 'EXPENSE', 'STANDARD',        NULL,            NULL, false),
    -- Kept although nothing can currently post to it: the Asset entity has no intangibles
    -- concept, so amortisation has no source. Present so the statement layout is right when it
    -- does, and cheap to deactivate if it never does.
    ('Depreciation & Amortization', 1, 'Amortization',                 'EXPENSE', 'STANDARD',        NULL,            NULL, false),

    -- Finance Costs ----------------------------------------------------------------------
    -- Its own group rather than an expense line, so it falls below EBIT and leaves both EBITDA
    -- and EBIT meaningful.
    ('Finance Costs',            0, 'Interest expense',                'EXPENSE', 'STANDARD',        NULL,            NULL, false)
) AS seed (group_name, display_order, name, account_type, account_kind,
           sub_ledger_type, system_key, expected_to_clear)
JOIN account_group g ON g.name = seed.group_name;
