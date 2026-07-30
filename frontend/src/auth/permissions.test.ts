import { describe, expect, it } from 'vitest'

import { AccessLevel, ProtectedField, Section, type Me } from '@/api/generated/model'

import { hiddenInResponse, permissionsOf } from './permissions'

/**
 * The seeded Remote/Order Staff role, as V6 grants it: Sales Order Fulfillment, Customers and
 * Back-in-Stock in full, Products view-only, everything else invisible by default-deny.
 */
const remoteOrderStaff: Me = {
  id: 2,
  username: 'staff',
  role: { id: 3, name: 'REMOTE_ORDER_STAFF', fullAccess: false, systemRole: false },
  sections: [
    { section: Section.SALES_ORDER_FULFILLMENT, level: AccessLevel.FULL, available: false },
    { section: Section.CUSTOMERS, level: AccessLevel.FULL, available: true },
    { section: Section.BACK_IN_STOCK_REMINDERS, level: AccessLevel.FULL, available: false },
    { section: Section.PRODUCTS, level: AccessLevel.VIEW, available: true },
  ],
  restrictedFields: [],
}

/**
 * An owner, shaped the way `/api/me` actually answers.
 *
 * `MeController.describe` builds `EnumSet.allOf(Section.class)` and sends **one row per section,
 * always** — with `NONE` where the role has no grant. An earlier version of this fixture used
 * `sections: []` on the theory that a full-access role carries no grant rows; that is true of the
 * database table and false of the response, and it made this file and `tree.test.ts` disagree
 * about the shape of the thing they both draw conclusions from.
 */
const owner: Me = {
  id: 1,
  username: 'owner',
  role: { id: 1, name: 'OWNER', fullAccess: true, systemRole: true },
  sections: Object.values(Section).map((section) => ({
    section,
    level: AccessLevel.FULL,
    available:
      section !== Section.SALES_ORDER_FULFILLMENT && section !== Section.BACK_IN_STOCK_REMINDERS,
  })),
  restrictedFields: [],
}

describe('permissions', () => {
  it('grants view on a VIEW section and refuses edit', () => {
    const permissions = permissionsOf(remoteOrderStaff)
    expect(permissions.canView(Section.PRODUCTS)).toBe(true)
    expect(permissions.canEdit(Section.PRODUCTS)).toBe(false)
  })

  it('grants both on a FULL section', () => {
    const permissions = permissionsOf(remoteOrderStaff)
    expect(permissions.canView(Section.CUSTOMERS)).toBe(true)
    expect(permissions.canEdit(Section.CUSTOMERS)).toBe(true)
  })

  it('is default-deny for a section with no grant', () => {
    const permissions = permissionsOf(remoteOrderStaff)
    // The ledger. Granting it is close to granting everything, and this role has no row for it.
    expect(permissions.levelOf(Section.JOURNAL)).toBe(AccessLevel.NONE)
    expect(permissions.canView(Section.JOURNAL)).toBe(false)
  })

  it('gives a full-access role everything', () => {
    const permissions = permissionsOf(owner)
    expect(permissions.canEdit(Section.JOURNAL)).toBe(true)
    expect(permissions.canEdit(Section.SETTINGS)).toBe(true)
    expect(permissions.isFullAccess).toBe(true)
  })

  it('gives a full-access role a section that is missing from the response entirely', () => {
    // Defensive rather than expected: `/api/me` always sends every section. But a section added
    // to the backend and not yet known here must not read as "denied" to an owner, which is the
    // direction that would lock somebody out of a feature that shipped.
    const withoutRows = permissionsOf({ ...owner, sections: [] })
    expect(withoutRows.canEdit(Section.JOURNAL)).toBe(true)

    // `available` gets no such bypass, deliberately: whether something is BUILT is a fact about
    // the software, not about the role, and an owner must not be shown a working link to a
    // feature that does not exist.
    expect(withoutRows.isAvailable(Section.JOURNAL)).toBe(false)
  })

  it('reads full access from the flag rather than the role name', () => {
    // The point: a role named something else, with full_access true, must behave identically.
    const auditor: Me = {
      ...owner,
      role: { id: 9, name: 'EXTERNAL_ACCOUNTANT', fullAccess: true, systemRole: false },
    }
    expect(permissionsOf(auditor).canEdit(Section.JOURNAL)).toBe(true)
  })

  it('separates "not granted" from "not built"', () => {
    const permissions = permissionsOf(remoteOrderStaff)
    // Granted in full, and there is nothing behind it yet. Both facts are true at once, and a
    // menu that could not tell them apart would show this role a working link to nothing.
    expect(permissions.canEdit(Section.SALES_ORDER_FULFILLMENT)).toBe(true)
    expect(permissions.isAvailable(Section.SALES_ORDER_FULFILLMENT)).toBe(false)
    expect(permissions.isAvailable(Section.CUSTOMERS)).toBe(true)
  })

  it('reports a restricted field as hidden', () => {
    // No role restricts anything since V26, so this is tested against a role built here — the
    // same choice the backend's own redaction tests made, and for the same reason.
    const restricted = permissionsOf({
      ...remoteOrderStaff,
      restrictedFields: [ProtectedField.PRODUCT_LAST_PURCHASE_PRICE],
    })
    expect(restricted.isFieldHidden(ProtectedField.PRODUCT_LAST_PURCHASE_PRICE)).toBe(true)
    expect(restricted.isFieldHidden(ProtectedField.PRODUCT_SUPPLIER)).toBe(false)
    expect(permissionsOf(remoteOrderStaff).isFieldHidden(ProtectedField.PRODUCT_SUPPLIER)).toBe(false)
  })

  it('hides the supplier SKU whenever the supplier is hidden', () => {
    /*
     * The backend applies this implication on the way out — `ProductView.redactedFor` computes
     * `hideSupplierSku = hideSupplier || !canSee(PRODUCT_SUPPLIER_SKU)`, because a supplier code
     * identifies the supplier indirectly — but `/api/me` reports the stored restrictions with no
     * derivation. So a role restricting only PRODUCT_SUPPLIER receives products with BOTH fields
     * blanked while `restrictedFields` names one.
     *
     * Without this, a screen asking about the SKU is told `false` about a value that was withheld,
     * and renders "not set" for "not shown to you" — the two states this convention exists to keep
     * apart, on the one field the backend hides indirectly.
     */
    const supplierHidden = permissionsOf({
      ...remoteOrderStaff,
      restrictedFields: [ProtectedField.PRODUCT_SUPPLIER],
    })
    expect(supplierHidden.isFieldHidden(ProtectedField.PRODUCT_SUPPLIER)).toBe(true)
    expect(supplierHidden.isFieldHidden(ProtectedField.PRODUCT_SUPPLIER_SKU)).toBe(true)

    // Not the other way round: the SKU can be hidden on its own.
    const skuHidden = permissionsOf({
      ...remoteOrderStaff,
      restrictedFields: [ProtectedField.PRODUCT_SUPPLIER_SKU],
    })
    expect(skuHidden.isFieldHidden(ProtectedField.PRODUCT_SUPPLIER)).toBe(false)
  })

  it('reads what a single response says was blanked, which cannot drift', () => {
    // The per-record question, answered by the backend's own report rather than by mirroring its
    // rules. This is what a screen should prefer.
    const product = {
      id: 7,
      hiddenFields: [ProtectedField.PRODUCT_SUPPLIER, ProtectedField.PRODUCT_SUPPLIER_SKU],
    }
    expect(hiddenInResponse(product, ProtectedField.PRODUCT_SUPPLIER_SKU)).toBe(true)
    expect(hiddenInResponse(product, ProtectedField.PRODUCT_LAST_PURCHASE_PRICE)).toBe(false)

    // An unredacted response carries an empty list; a response not yet loaded carries nothing.
    expect(hiddenInResponse({ hiddenFields: [] }, ProtectedField.PRODUCT_SUPPLIER)).toBe(false)
    expect(hiddenInResponse(undefined, ProtectedField.PRODUCT_SUPPLIER)).toBe(false)
  })

  it('denies everything when nobody is signed in', () => {
    const permissions = permissionsOf(undefined)
    for (const section of Object.values(Section)) {
      expect(permissions.canView(section)).toBe(false)
    }
    expect(permissions.isFullAccess).toBe(false)
  })
})
