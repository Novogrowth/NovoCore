-- ---------------------------------------------------------------------------------------
-- V25 — Section.EMAIL_OUTBOX (Q44's section half)
-- ---------------------------------------------------------------------------------------
--
-- Step 11 built the email outbox and left one question open: who may see it, and does it need
-- a Section of its own? Step 14c answers yes, and this migration is what lets a grant for it
-- be stored.
--
-- WHY ITS OWN SECTION, NOT PART OF SETTINGS
--
-- SMTP configuration is a settings concern. Who was emailed, about what, when, and what
-- bounced is operational history about customers and suppliers — a correspondence trail.
-- Granting somebody the ability to change the SMTP password should not hand them that trail,
-- and granting them the trail should not let them change where mail comes from. Same argument
-- that separates JOURNAL from CHART_OF_ACCOUNTS and INVENTORY from PRODUCTS.
--
-- Message *bodies* are already absent from QueuedEmailView by design, so what this section
-- governs is recipients, subjects, delivery state, and attachments.
--
-- THIS SECTION IS NOT SUFFICIENT FOR A REFERENCED ATTACHMENT
--
-- Q44's other half, built in the same step: downloading an attachment that is also a document
-- on a core record re-checks that record's own section as well (AttachmentOwnerType). An
-- email having been sent to someone does not change who may see the source document
-- afterwards, and the outbox must not become a second, weaker way into it.
--
-- NO GRANTS ARE SEEDED. Access is default-deny, so this section is invisible to every
-- non-full-access role until somebody grants it, and the two full-access system roles reach
-- it without a grant. That is the safe direction to fail and needs no seed to be true.
--
-- WHY THIS MIGRATION EXISTS AT ALL, WHICH WAS A SURPRISE
--
-- The step 14 plan said "no migrations expected", on the reasoning that a Section is a Java
-- enum and grants are default-deny. That was wrong, and the test suite said so rather than
-- the reasoning: role_section_grant carries a CHECK listing every known section by name, so a
-- value that exists only in Java cannot be granted — the insert is refused by the database.
--
-- That constraint is doing exactly what it was built to do. It is the same pattern as
-- journal_entry_source_known and the consumption-source CHECK: the database states the value
-- list independently, so a section removed from the enum cannot leave orphaned grants behind
-- and a section added to the database alone cannot hide from the code. The cost is that
-- adding one is a migration, which is the correct price.

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
        'EMAIL_OUTBOX',
        'SALES_ORDER_FULFILLMENT',
        'BACK_IN_STOCK_REMINDERS'));
