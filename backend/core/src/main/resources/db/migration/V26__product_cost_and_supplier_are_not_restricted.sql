-- Remote/Order Staff may see a product's cost and its supplier.
--
-- V6 seeded three field restrictions against that role — PRODUCT_LAST_PURCHASE_PRICE,
-- PRODUCT_SUPPLIER and PRODUCT_SUPPLIER_SKU — on the reasoning that "an order picker has no need
-- to know what it cost us or who supplies it". That was a confidentiality judgement, and the
-- business does not have the confidentiality need behind it: there is nothing sensitive about
-- purchase price here. A bank balance might reasonably stay hidden from a home-based worker; what
-- a bag of beans cost does not.
--
-- So this is a POLICY change and not a mechanism change, and it is deliberately expressed as
-- deleting data rather than as deleting code:
--
--   * ProtectedField keeps all three values, and role_field_restriction_field_known keeps listing
--     them. Restricting one again is an INSERT, not a rebuild.
--   * RoleView.canSee, ProductView.redactedFor and the three ArchUnit rules that force controllers
--     through the redacting reads all stay exactly as they were. They are correct; they simply have
--     nothing to hide today.
--
-- ⚠️ CONSEQUENCE WORTH STATING PLAINLY: these were the only field restrictions in the system, and
-- those three enum values are the only fields the mechanism knows about. After this migration NO
-- ROLE HAS ANY FIELD RESTRICTION, so nothing in seeded data exercises the inner layer of brief §7's
-- two-layer permission model. That is a stated decision, not an oversight.
--
-- Because of it, the tests that used to prove redaction against the SEEDED role now create a role
-- and restrict a field at runtime (RoleService.restrictField) instead. Keeping that proof matters
-- more than it looks: with no restriction anywhere, a change that stopped ProductService.requireFor
-- consulting the role at all would pass every test while silently removing the guarantee — which is
-- exactly the shape of the audit-log defect step 12 found.

DELETE FROM role_field_restriction
WHERE  protected_field IN (
           'PRODUCT_LAST_PURCHASE_PRICE',
           'PRODUCT_SUPPLIER',
           'PRODUCT_SUPPLIER_SKU')
AND    role_id IN (SELECT id FROM app_role WHERE name = 'REMOTE_ORDER_STAFF');

COMMENT ON TABLE role_field_restriction IS
    'Fields hidden from a role inside a section it can otherwise see — the inner layer of brief '
    'section 7''s two-layer permission model. Empty as of V26: Remote/Order Staff''s product cost '
    'and supplier restrictions were removed because the business has no confidentiality need '
    'behind them. The mechanism is intact and unused; adding a restriction is an INSERT here.';
