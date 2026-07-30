import { Section } from '@/api/generated/model'

import type { AdapterKey, BuildStatus, ModuleKey } from './types'

/**
 * The adapters and modules the system is planned to have, and whether each exists yet.
 *
 * Settings renders both grids from this and from nothing else. Every row is disabled and marked
 * not-built, and **nothing is persisted**: there is no settings key and no API behind these
 * toggles, so a switch that appeared to work would silently forget — which is worse than one that
 * says plainly it does nothing.
 *
 * An adapter translates an external system into the core's model; a module is internally-driven
 * functionality. Which is which is a real architectural distinction, not a display grouping.
 */

export interface RegistryEntry<K extends string> {
  readonly key: K
  readonly status: BuildStatus
  /**
   * The backend `Section` that governs this module, where one exists.
   *
   * Only two do. Both are declared `available(false)` on the backend, so `/api/me` reports them
   * as granted-but-not-built, and the navigation treats that flag as authoritative — see
   * `visibility.ts`. The frontend therefore never gets to disagree with the backend about whether
   * something is built; it can only fail to know, and the backend's answer wins.
   */
  readonly section?: Section
}

export const ADAPTERS: readonly RegistryEntry<AdapterKey>[] = [
  { key: 'PROSVASIS_GO', status: 'NOT_BUILT' },
  { key: 'WOOCOMMERCE', status: 'NOT_BUILT' },
  { key: 'SKROUTZ', status: 'NOT_BUILT' },
  { key: 'ACS_COURIER', status: 'NOT_BUILT' },
  { key: 'FILE_IMPORT', status: 'NOT_BUILT' },
  { key: 'AADE_VIES_LOOKUP', status: 'NOT_BUILT' },
  { key: 'AADE_MYDATA', status: 'NOT_BUILT' },
  { key: 'BANK_AGGREGATOR', status: 'NOT_BUILT' },
  { key: 'AADE_PAROCHOS', status: 'NOT_BUILT' },
  { key: 'POS_PROVIDER', status: 'NOT_BUILT' },
]

export const MODULES: readonly RegistryEntry<ModuleKey>[] = [
  { key: 'SALES_ORDER_FULFILLMENT', status: 'NOT_BUILT', section: Section.SALES_ORDER_FULFILLMENT },
  { key: 'CLEARING_CHECKS', status: 'NOT_BUILT' },
  { key: 'PRICE_TAG_PRINTING', status: 'NOT_BUILT' },
  { key: 'PURCHASE_ORDERS', status: 'NOT_BUILT' },
  { key: 'BACK_IN_STOCK_REMINDERS', status: 'NOT_BUILT', section: Section.BACK_IN_STOCK_REMINDERS },
  { key: 'SERVICE_TECHNICIAN_MANAGEMENT', status: 'NOT_BUILT' },
  { key: 'REPORTS', status: 'NOT_BUILT' },
  { key: 'ACCOUNTANT_MONTHLY_PACKAGE', status: 'NOT_BUILT' },
  { key: 'AI_ANALYSIS', status: 'NOT_BUILT' },
  { key: 'EMPLOYEE_MANUAL_VOICE_ASSISTANT', status: 'NOT_BUILT' },
  { key: 'ERGANI', status: 'NOT_BUILT' },
]

/**
 * Deliberately in neither grid: **core-owned invoice issuing**.
 *
 * Retiring Prosvasis Go as the issuer is a one-way cutover, not something to be switched on and
 * off — a toggle would imply invoices could be issued by one system on Monday and the other on
 * Tuesday, which is not a state the ledger or the tax authority recognises. It needs its own
 * control when that step arrives, and adding it here would be the wrong shape by default.
 */
