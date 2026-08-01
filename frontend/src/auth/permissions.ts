import { AccessLevel, ProtectedField, type Me, type Section } from '@/api/generated/model'

import { useSession } from './session'

/**
 * What the signed-in user may do, derived from `/api/me` and nowhere else.
 *
 * This is the mechanism the navigation tree is filtered through and the route guard asks. It is
 * not a security control — the backend refuses what it refuses regardless of what the browser
 * believes — it is what stops the application offering an action that will be refused.
 */

/**
 * NONE < VIEW < FULL, stated once.
 *
 * The one place `AccessLevel` values are compared. Comparing them inline in a component is how a
 * screen ends up treating VIEW as edit rights on one page and not another.
 */
const RANK: Record<AccessLevel, number> = {
  [AccessLevel.NONE]: 0,
  [AccessLevel.VIEW]: 1,
  [AccessLevel.FULL]: 2,
}

export interface Permissions {
  /** The grant held on a section, `NONE` when there is none. Default-deny, as the backend is. */
  levelOf(section: Section): AccessLevel
  /** VIEW or FULL. */
  canView(section: Section): boolean
  /** FULL. A screen offering a save button on a VIEW grant is a screen full of 403s. */
  canEdit(section: Section): boolean
  /**
   * Whether anything is actually built behind a section.
   *
   * `Section.isAvailable` exists on the backend precisely so a UI can tell "you may not see this"
   * from "this does not exist yet" — two states that look identical to a user and have entirely
   * different fixes. `SALES_ORDER_FULFILLMENT` and `BACK_IN_STOCK_REMINDERS` are the two that are
   * granted but not built.
   */
  isAvailable(section: Section): boolean
  /**
   * OWNER and ADMIN. Not matched by name: both are seeded `full_access`, and a future full-access
   * role must behave the same way without anyone editing this file.
   */
  isFullAccess: boolean
  /**
   * Who the signed-in user is, when `/api/me` has answered.
   *
   * Here rather than read out of the session at a call site, because the only question these two
   * answer is a permission question: **is this me?** Two backend guards turn on it and nothing
   * else does. `RoleServiceImpl.refuseIfCallerHolds` refuses a caller editing the permissions of
   * their own role, and `UserServiceImpl.changeRole` refuses a caller changing their own — because
   * one person widening the role they hold, or stepping into one they built, is an escalation
   * nobody else approved. The two screens compare against these so they do not offer a change whose
   * refusal is already certain.
   */
  roleId: number | undefined
  userId: number | undefined
  /**
   * Whether this role has a field withheld from it, **across the application**.
   *
   * A withheld field is *absent* from the response rather than null — Jackson's `non_null`
   * inclusion — so this is what tells a component that a missing value means "not shown to you"
   * rather than "not set". Since V26 no role restricts anything, which is exactly why this is
   * built and tested now rather than discovered by the first role that does.
   *
   * **This is the role-level question — "should this column exist at all".** For "was this value
   * blanked out of *this record*", read the response's own `hiddenFields` through
   * {@link hiddenInResponse}, which is the backend's per-response report and cannot drift from it.
   */
  isFieldHidden(field: ProtectedField): boolean
  /** True while `/api/me` has not answered yet. Callers must not read a `false` as a refusal. */
  isLoading: boolean
}

/**
 * Restrictions the backend applies but does not list on `/api/me`.
 *
 * `MeController.restrictedFieldsOf` reports the fields the role cannot see, field by field, with no
 * derivation. `ProductView.redactedFor` then applies one on the way out:
 *
 * > `hideSupplierSku = hideSupplier || !role.canSee(PRODUCT_SUPPLIER_SKU)`
 *
 * — because a supplier code identifies the supplier indirectly, so returning it while blanking the
 * supplier would hand over the answer in a different column.
 *
 * The consequence for a client reading `/api/me` alone: a role restricting only `PRODUCT_SUPPLIER`
 * receives products with **both** fields blanked, while `restrictedFields` names only one. Asking
 * `isFieldHidden(PRODUCT_SUPPLIER_SKU)` would answer `false` about a value that was withheld — the
 * two states this convention exists to keep apart, collapsed into one, on the single field the
 * backend hides indirectly.
 *
 * ⚠️ **This mirrors backend logic, which is duplication and can drift.** It is here rather than in
 * the components because one mirror in one place is inspectable; the real fix is for `/api/me` to
 * report derived restrictions too, which is a backend change and is noted in PROGRESS.md. The
 * per-record answer never needs this — {@link hiddenInResponse} reads what the response itself
 * says was blanked.
 */
function withImpliedRestrictions(restricted: readonly ProtectedField[]): Set<ProtectedField> {
  const fields = new Set<ProtectedField>(restricted)
  if (fields.has(ProtectedField.PRODUCT_SUPPLIER)) {
    fields.add(ProtectedField.PRODUCT_SUPPLIER_SKU)
  }
  return fields
}

/**
 * Whether a field was blanked out of **this particular response**.
 *
 * The authoritative answer, and the one to prefer: `hiddenFields` is written by the backend for the
 * record it accompanies, after every rule — stored restrictions, section access and the
 * supplier-implies-SKU narrowing — has been applied. It cannot disagree with what was actually
 * sent, because it is part of what was sent.
 */
export function hiddenInResponse(
  view: { hiddenFields?: ProtectedField[] } | undefined,
  field: ProtectedField,
): boolean {
  return view?.hiddenFields?.includes(field) ?? false
}

export function permissionsOf(me: Me | undefined, isLoading = false): Permissions {
  const grants = new Map<Section, { level: AccessLevel; available: boolean }>()
  for (const access of me?.sections ?? []) {
    if (!access.section) continue
    grants.set(access.section, {
      level: access.level ?? AccessLevel.NONE,
      available: access.available ?? false,
    })
  }

  const fullAccess = me?.role?.fullAccess ?? false
  const restricted = withImpliedRestrictions(me?.restrictedFields ?? [])

  const levelOf = (section: Section): AccessLevel => {
    // A full-access role reaches every section without a grant row, including sections added
    // after the role was created. The backend behaves this way; so must the menu.
    if (fullAccess) return AccessLevel.FULL
    return grants.get(section)?.level ?? AccessLevel.NONE
  }

  return {
    levelOf,
    canView: (section) => RANK[levelOf(section)] >= RANK[AccessLevel.VIEW],
    canEdit: (section) => RANK[levelOf(section)] >= RANK[AccessLevel.FULL],
    isAvailable: (section) => grants.get(section)?.available ?? false,
    isFullAccess: fullAccess,
    roleId: me?.role?.id,
    userId: me?.id,
    isFieldHidden: (field) => restricted.has(field),
    isLoading,
  }
}

export function usePermissions(): Permissions {
  const { me, isLoading } = useSession()
  return permissionsOf(me, isLoading)
}
