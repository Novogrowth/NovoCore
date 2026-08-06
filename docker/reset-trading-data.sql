-- Reset the development database's trading data, and nothing else.
--
--   docker exec -i novocore-postgres-1 psql -U novocore -d novocore < docker/reset-trading-data.sql
--
-- This exists so that re-seeding does not mean `docker compose down -v`. On this stack that
-- command is far more expensive than it looks, and the expense is invisible until it is paid:
--
--   * The Google Drive OAuth client secrets and refresh tokens live in the `setting` table, put
--     there once during commissioning. They are NOT in docker/.env, which holds three keys — the
--     database password, the site address and the backup encryption key. Dropping the volume means
--     re-running the consent flow for two Google accounts, and (see HISTORY.md's closed incident)
--     creating fresh destination folders under the new grants, because a `drive.file` grant is
--     per-file and does not survive re-consent.
--   * NOVOCORE_BOOTSTRAP_OWNER_USERNAME/_PASSWORD are deliberately blank once an owner exists, so
--     the Owner account would not come back either.
--
-- After this runs, LiveSeedTest will accept the database again.
--
--
-- WHAT IS DELIBERATELY LEFT ALONE
--
--   setting                     the Drive credentials and everything else configured by hand
--   app_user, app_role, role_*  accounts, roles, grants and field restrictions
--   backup_run, backup_upload, restore_check    the backup history
--   vat_class, vat_exemption_reason, unit_of_measure, charge_type   Flyway's lookups; the quarter
--                               reads these heavily and creates none of them
--   flyway_schema_history       obviously
--
--   audit_log                   NOT in the TRUNCATE list below, and its absence is load-bearing.
--                               The table is append-only, enforced by a row trigger on DELETE and
--                               UPDATE — and TRUNCATE does not fire row triggers, so naming it here
--                               would silently do the one thing the trigger exists to forbid. An
--                               audit log that survives the records it describes is the correct
--                               outcome anyway: it is a record of what happened, not a view of what
--                               currently exists.
--
--
-- WHY THERE IS NO `RESTART IDENTITY`
--
-- It is the obvious thing to add and it would be wrong. audit_log stores entity_id as text and
-- survives this script; restarting the sequences would hand the same ids to different rows, so
-- "product 3 deactivated" would come to name a product that had never been deactivated. Sequence
-- numbers are cheap and a quietly wrong audit trail is not. A re-seed therefore produces the same
-- data with higher ids, which nothing depends on — the scenario references its own documents by
-- handle, never by literal id.

\set ON_ERROR_STOP on

BEGIN;

-- One statement, so foreign keys among these are satisfied by construction. No CASCADE: every
-- table that references one of these is itself in the list, and if that ever stops being true the
-- right outcome is a loud failure naming the table somebody forgot.
TRUNCATE TABLE
    asset,
    attachment,
    bank_transfer,
    bundle_component,
    credit_note,
    credit_note_line,
    customer_credit,
    email_outbox,
    email_outbox_attachment,
    freight_allocation,
    freight_allocation_line,
    goods_receipt,
    goods_receipt_line,
    gr_ir_match,
    inventory_lot,
    journal_entry,
    journal_line,
    open_item_allocation,
    product,
    purchase_invoice,
    purchase_invoice_line,
    sales_invoice,
    sales_invoice_line,
    sales_invoice_line_component,
    serialized_unit,
    settlement,
    stock_consumption,
    stock_consumption_line,
    stock_write_off,
    supplier;

-- The retail walk-in customer is seeded and structural (ADR 0009 / Q10) — a shared record the
-- domain resolves by system key, not a party anybody created. Every other customer goes. This is a
-- DELETE rather than a TRUNCATE for exactly that reason, and it runs after the truncation above so
-- nothing still references the rows being removed.
DELETE FROM customer WHERE system_key IS NULL;

-- The chart of accounts is not trading data and is mostly not touched — but the quarter adds one
-- group and one account to it, and both carry unique constraints, so a re-seed would be refused if
-- they were left behind. `created_by = 'system'` is what a Flyway insert leaves (it is the column
-- default and no migration overrides it); anything else was created through the API by a logged-in
-- user. Accounts before groups: account.group_id references it.
--
-- Note this also removes an account a person created through the UI. That is intended: this script
-- restores the chart to what the migrations define, and a half-restored chart is the state that
-- would make the next refusal hard to explain.
DELETE FROM account WHERE created_by <> 'system';
DELETE FROM account_group WHERE created_by <> 'system';

COMMIT;

-- What is left, so the script reports rather than merely returning. The first three are the exact
-- counts LiveSeedTest's gate reads; the rest are what must have survived.
SELECT 'product'         AS table_name, count(*) AS rows, 'must be 0'  AS expected FROM product
UNION ALL SELECT 'supplier',        count(*), 'must be 0'  FROM supplier
UNION ALL SELECT 'journal_entry',   count(*), 'must be 0'  FROM journal_entry
UNION ALL SELECT 'customer',        count(*), 'the seeded retail walk-in only' FROM customer
UNION ALL SELECT 'account',         count(*), 'the Flyway chart'   FROM account
UNION ALL SELECT 'account_group',   count(*), 'the Flyway chart'   FROM account_group
UNION ALL SELECT 'setting',         count(*), 'untouched'          FROM setting
UNION ALL SELECT 'app_user',        count(*), 'untouched'          FROM app_user
UNION ALL SELECT 'app_role',        count(*), 'untouched'          FROM app_role
UNION ALL SELECT 'audit_log',       count(*), 'untouched, append-only' FROM audit_log;
