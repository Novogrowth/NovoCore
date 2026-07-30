import type { AccessLevel, Section } from '@/api/generated/model'

/** A grant a navigation item needs. All of an item's requirements must hold for it to appear. */
export interface Requirement {
  readonly section: Section
  readonly level: AccessLevel
}

/**
 * What the software can do today.
 *
 * `BUILT` means there is a working screen behind the item. `NOT_BUILT` means the item is a
 * placeholder: it renders disabled, and only to a full-access role, so an Owner can see the shape
 * of the system while nobody else is shown a door that does not open.
 */
export type BuildStatus = 'BUILT' | 'NOT_BUILT'

/** Keys for the ten adapters. Frontend-owned: no adapter exists on the backend yet. */
export type AdapterKey =
  | 'PROSVASIS_GO'
  | 'WOOCOMMERCE'
  | 'SKROUTZ'
  | 'ACS_COURIER'
  | 'FILE_IMPORT'
  | 'AADE_VIES_LOOKUP'
  | 'AADE_MYDATA'
  | 'BANK_AGGREGATOR'
  | 'AADE_PAROCHOS'
  | 'POS_PROVIDER'

/** Keys for the eleven modules. Two of them are also backend `Section`s — see `registry.ts`. */
export type ModuleKey =
  | 'SALES_ORDER_FULFILLMENT'
  | 'CLEARING_CHECKS'
  | 'PRICE_TAG_PRINTING'
  | 'PURCHASE_ORDERS'
  | 'BACK_IN_STOCK_REMINDERS'
  | 'SERVICE_TECHNICIAN_MANAGEMENT'
  | 'REPORTS'
  | 'ACCOUNTANT_MONTHLY_PACKAGE'
  | 'AI_ANALYSIS'
  | 'EMPLOYEE_MANUAL_VOICE_ASSISTANT'
  | 'ERGANI'

/**
 * One entry in the navigation tree.
 *
 * The tree is data. Reordering the menu, moving an item between groups or adding a placeholder is
 * an edit to `tree.ts` and nothing else — no component decides what appears where.
 */
export interface NavNode {
  /** Stable identifier. The label is `nav.<id>`, in English and Greek; it is never text here. */
  readonly id: string
  /** The route. Absent on a grouping node, which is a heading rather than a destination. */
  readonly path?: string
  /** Grants required. Empty means authenticated is enough. */
  readonly requires: readonly Requirement[]
  readonly status: BuildStatus
  /**
   * The route this screen is built on, as `METHOD /path` in the OpenAPI spec.
   *
   * Present on every built item, and checked by `tree.test.ts` against the spec's own
   * `x-novocore-section` / `x-novocore-level`. That is what stops this tree and the backend
   * disagreeing about what a screen needs: the requirement above is stated here, and verified
   * there.
   */
  readonly endpoint?: string
  /**
   * Why a built item has no endpoint. Required when one is absent, so "no API" is a decision on
   * the record rather than an omission nobody noticed.
   */
  readonly noApi?: string
  readonly adapter?: AdapterKey
  readonly module?: ModuleKey
  /** A separating rule after this item. Used once, inside Accounting. */
  readonly dividerAfter?: boolean
  readonly children?: readonly NavNode[]
}
