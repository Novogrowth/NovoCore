import { AccessLevel, type Me, type ProtectedField, type Section } from '@/api/generated/model'

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
   * Whether the role has this field withheld from it.
   *
   * A withheld field is *absent* from the response rather than null — Jackson's `non_null`
   * inclusion — so this is what tells a component that a missing value means "not shown to you"
   * rather than "not set". Since V26 no role restricts anything, which is exactly why this is
   * built and tested now rather than discovered by the first role that does.
   */
  isFieldHidden(field: ProtectedField): boolean
  /** True while `/api/me` has not answered yet. Callers must not read a `false` as a refusal. */
  isLoading: boolean
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
  const restricted = new Set<ProtectedField>(me?.restrictedFields ?? [])

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
    isFieldHidden: (field) => restricted.has(field),
    isLoading,
  }
}

export function usePermissions(): Permissions {
  const { me, isLoading } = useSession()
  return permissionsOf(me, isLoading)
}
