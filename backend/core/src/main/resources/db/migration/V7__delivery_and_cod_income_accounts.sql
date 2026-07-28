-- Q27 resolved: dedicated income accounts for the two chargeable fees, and the ChargeType seed.
--
-- ---------------------------------------------------------------------------------------
-- WHY DEDICATED ACCOUNTS RATHER THAN 'Other income'
-- ---------------------------------------------------------------------------------------
-- Delivery and cash-on-delivery fees appear on most invoices. Routing them to 'Other income'
-- would make a residual bucket the largest income line in the business, which destroys the one
-- thing a residual bucket is for — being small enough that whatever lands in it is worth
-- looking at.
--
-- Separately, 'Delivery income' has to be comparable against the existing 'Transportation
-- costs' expense account to answer "is shipping costing us money?". Merged into a bucket that
-- also holds scrap sales and rebates, that question is unanswerable.
--
-- NOT a blanket policy. charge_type.income_account_id is per-type precisely so that a
-- low-volume future fee can still point at 'Other income' without a migration.
--
-- No channel split for these two. The channel split was a brief §4 mandate for Sales
-- specifically; a delivery fee is a delivery fee whichever channel the order came through.
--
-- No AccountSystemKey either. A system key exists for accounts NovoCore's own posting rules
-- must *locate*; these are located through charge_type.income_account_id, which is an operator
-- decision per fee rather than a rule compiled into the software.

-- ---------------------------------------------------------------------------------------
-- Open the two display-order slots
-- ---------------------------------------------------------------------------------------
-- The new accounts belong immediately after the Sales and Sales-returns block, so that
-- everything arising from a sale reads together and 'Other income' stays last — a residual
-- bucket in the middle of a list invites postings that should have gone somewhere specific.
--
-- Shifting by POSITION rather than by name, so that whatever currently sits at 6 and beyond
-- keeps its relative order. Ordering in this chart is manual and drag-and-drop (brief §4), so a
-- migration must open a gap rather than assert an absolute arrangement it did not author.

UPDATE account
SET    display_order = display_order + 2
WHERE  group_id = (SELECT id FROM account_group WHERE name = 'Income')
  AND  display_order >= 6;

-- ---------------------------------------------------------------------------------------
-- The two accounts
-- ---------------------------------------------------------------------------------------

INSERT INTO account (group_id, display_order, name, account_type, account_kind)
SELECT g.id, seed.display_order, seed.name, 'INCOME', 'STANDARD'
FROM (VALUES
    ('Income', 6, 'Delivery income'),
    ('Income', 7, 'COD fee income')
) AS seed (group_name, display_order, name)
JOIN account_group g ON g.name = seed.group_name;

COMMENT ON TABLE charge_type IS
    'Chargeable fees appearing as sales invoice lines (COD, delivery). Income side only. '
    'Seeded against dedicated income accounts (Q27); nothing consumes it until step 9.';

-- ---------------------------------------------------------------------------------------
-- Seed: the two charge types
-- ---------------------------------------------------------------------------------------
-- Both default to the standard 24% rate ('1410').
--
-- SETTLED (Q33): a fee's VAT rate is INDEPENDENT of the products on the invoice. It was raised as
-- a possible defect — Greek practice treats an ancillary charge as following the rate of the main
-- supply — and answered explicitly: these fees are not treated as ancillary to the goods here, so
-- a 13% order still carries 24% delivery. That means the default below is the operative rate
-- rather than a placeholder, and nothing should later be built to derive a fee's rate from the
-- lines around it.
--
-- The per-line override still exists (invoice line beats customer beats product — see
-- VatClassPrecedence), so a specific invoice can state something else deliberately. What is
-- deliberately absent is anything automatic.
--
-- Joined by code and name rather than by generated id, for the same reason V4's seed is: these
-- statements must not depend on where a sequence happens to have got to.

INSERT INTO charge_type (name, default_vat_class_id, income_account_id)
SELECT seed.name, v.id, a.id
FROM (VALUES
    ('Delivery', '1410', 'Delivery income'),
    ('COD fee',  '1410', 'COD fee income')
) AS seed (name, vat_class_code, account_name)
JOIN vat_class v ON v.code = seed.vat_class_code
JOIN account a ON a.name = seed.account_name
    AND a.group_id = (SELECT id FROM account_group WHERE name = 'Income');
