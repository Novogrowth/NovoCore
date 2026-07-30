import { AccessLevel, Section } from '@/api/generated/model'

import type { NavNode, Requirement } from './types'

/**
 * The navigation, as data.
 *
 * This array is the only statement of what the menu contains, in what order, under which headings,
 * and what each item requires. Reordering or regrouping is an edit here; no component contains a
 * list of screens, and no screen decides whether it should be reachable.
 *
 * Labels are absent on purpose: each node has an `id`, and `nav.<id>` resolves it in English and
 * Greek. A tree holding English strings is a tree that has to be restructured to translate.
 *
 * `endpoint` is checked against the OpenAPI spec by `tree.test.ts`, so an item claiming the wrong
 * grant fails the build rather than 403-ing in front of an operator.
 */

const view = (section: Section): Requirement[] => [{ section, level: AccessLevel.VIEW }]

/** Authenticated is enough — placeholders for features with no section of their own yet. */
const NONE: readonly Requirement[] = []

export const NAV_TREE: readonly NavNode[] = [
  {
    id: 'operations',
    requires: NONE,
    status: 'BUILT',
    noApi: 'A heading. Its visibility is decided by its children.',
    children: [
      {
        // The Sales Order Fulfillment module. Its section exists and can be granted; nothing is
        // built behind it, which /api/me reports as `available: false`.
        id: 'orders',
        path: '/orders',
        requires: view(Section.SALES_ORDER_FULFILLMENT),
        status: 'NOT_BUILT',
        module: 'SALES_ORDER_FULFILLMENT',
      },
      {
        // Products, Bundles and Roast Dates are tabs within this screen rather than three menu
        // items — Roast Dates being the one not built yet. Tabs are a screen's business.
        id: 'products',
        path: '/products',
        requires: view(Section.PRODUCTS),
        status: 'BUILT',
        endpoint: 'GET /api/products',
      },
      {
        id: 'inventory',
        requires: NONE,
        status: 'BUILT',
        noApi: 'A heading.',
        children: [
          {
            id: 'inventory.stock',
            path: '/inventory/stock',
            requires: view(Section.INVENTORY),
            status: 'BUILT',
            endpoint: 'GET /api/inventory/lots',
          },
          {
            id: 'inventory.lots',
            path: '/inventory/lots',
            requires: view(Section.INVENTORY),
            status: 'BUILT',
            endpoint: 'GET /api/inventory/units',
          },
          {
            id: 'inventory.writeOffs',
            path: '/inventory/write-offs',
            requires: view(Section.INVENTORY),
            status: 'BUILT',
            endpoint: 'GET /api/inventory/write-offs',
          },
          {
            id: 'inventory.counts',
            path: '/inventory/counts',
            requires: view(Section.INVENTORY),
            status: 'NOT_BUILT',
          },
        ],
      },
      {
        // Customers and the Back-in-Stock queue are two tabs; the queue is not built.
        id: 'customers',
        path: '/customers',
        requires: view(Section.CUSTOMERS),
        status: 'BUILT',
        endpoint: 'GET /api/customers',
      },
      {
        id: 'suppliers',
        path: '/suppliers',
        requires: view(Section.SUPPLIERS),
        status: 'BUILT',
        endpoint: 'GET /api/suppliers',
      },
      {
        // Repair tickets. No section of its own on the backend yet, so a placeholder visible to
        // full access only — which is what `requires: NONE` plus NOT_BUILT amounts to.
        id: 'service',
        path: '/service',
        requires: NONE,
        status: 'NOT_BUILT',
        module: 'SERVICE_TECHNICIAN_MANAGEMENT',
      },
      {
        id: 'tools',
        path: '/tools',
        requires: NONE,
        status: 'NOT_BUILT',
        noApi: 'A launcher page. More tools land under it as they are built.',
        children: [
          {
            id: 'tools.priceTags',
            path: '/tools/price-tags',
            requires: NONE,
            status: 'NOT_BUILT',
            module: 'PRICE_TAG_PRINTING',
          },
        ],
      },
    ],
  },

  {
    id: 'finance',
    requires: NONE,
    status: 'BUILT',
    noApi: 'A heading.',
    children: [
      {
        id: 'sales',
        requires: NONE,
        status: 'BUILT',
        noApi: 'A heading.',
        children: [
          {
            id: 'sales.invoices',
            path: '/sales/invoices',
            requires: view(Section.SALES),
            status: 'BUILT',
            endpoint: 'GET /api/sales-invoices',
          },
          {
            id: 'sales.creditNotes',
            path: '/sales/credit-notes',
            requires: view(Section.SALES),
            status: 'BUILT',
            endpoint: 'GET /api/credit-notes',
          },
        ],
      },
      {
        id: 'purchasing',
        requires: NONE,
        status: 'BUILT',
        noApi: 'A heading.',
        children: [
          {
            id: 'purchasing.invoices',
            path: '/purchasing/invoices',
            requires: view(Section.PURCHASING),
            status: 'BUILT',
            endpoint: 'GET /api/purchase-invoices',
          },
          {
            id: 'purchasing.goodsReceipts',
            path: '/purchasing/goods-receipts',
            requires: view(Section.PURCHASING),
            status: 'BUILT',
            endpoint: 'GET /api/goods-receipts',
          },
          {
            id: 'purchasing.landedCost',
            path: '/purchasing/landed-cost',
            requires: view(Section.PURCHASING),
            status: 'BUILT',
            endpoint: 'GET /api/freight-allocations',
          },
          {
            id: 'purchasing.orders',
            path: '/purchasing/orders',
            requires: view(Section.PURCHASING),
            status: 'NOT_BUILT',
            module: 'PURCHASE_ORDERS',
          },
        ],
      },
      {
        id: 'accounting',
        requires: NONE,
        status: 'BUILT',
        noApi: 'A heading.',
        children: [
          {
            id: 'accounting.chartOfAccounts',
            path: '/accounting/chart-of-accounts',
            requires: view(Section.CHART_OF_ACCOUNTS),
            status: 'BUILT',
            endpoint: 'GET /api/chart-of-accounts',
          },
          {
            id: 'accounting.journal',
            path: '/accounting/journal',
            requires: view(Section.JOURNAL),
            status: 'BUILT',
            endpoint: 'GET /api/journal-entries',
          },
          {
            // Read-only, and that is the state of the API rather than a design choice: there is
            // no route that writes a manual journal entry. The screen lists the entries whose
            // source is MANUAL and offers no way to add one, which is honest about what exists.
            id: 'accounting.manualEntries',
            path: '/accounting/manual-entries',
            requires: view(Section.JOURNAL),
            status: 'BUILT',
            endpoint: 'GET /api/journal-entries',
          },
          {
            id: 'accounting.fixedAssets',
            path: '/accounting/fixed-assets',
            requires: view(Section.FIXED_ASSETS),
            status: 'BUILT',
            endpoint: 'GET /api/assets',
            dividerAfter: true,
          },
          {
            // A receipt and a payment are one settlement in two directions, which is why they
            // share a section and an endpoint and are still two menu items: nobody recording
            // money coming in thinks of it as the same screen as money going out.
            id: 'accounting.receipts',
            path: '/accounting/receipts',
            requires: view(Section.SETTLEMENTS),
            status: 'BUILT',
            endpoint: 'GET /api/settlements',
          },
          {
            id: 'accounting.payments',
            path: '/accounting/payments',
            requires: view(Section.SETTLEMENTS),
            status: 'BUILT',
            endpoint: 'GET /api/settlements',
          },
          {
            id: 'accounting.bankTransfers',
            path: '/accounting/bank-transfers',
            requires: view(Section.SETTLEMENTS),
            status: 'BUILT',
            endpoint: 'GET /api/bank-transfers',
          },
          {
            id: 'accounting.bankBalances',
            path: '/accounting/bank-balances',
            requires: view(Section.SETTLEMENTS),
            status: 'NOT_BUILT',
          },
          {
            id: 'accounting.clearingChecks',
            path: '/accounting/clearing-checks',
            requires: view(Section.SETTLEMENTS),
            status: 'NOT_BUILT',
            module: 'CLEARING_CHECKS',
          },
        ],
      },
      {
        id: 'reports',
        path: '/reports',
        requires: NONE,
        status: 'NOT_BUILT',
        module: 'REPORTS',
      },
    ],
  },

  {
    id: 'system',
    requires: NONE,
    status: 'BUILT',
    noApi: 'A heading.',
    children: [
      {
        id: 'settings',
        requires: NONE,
        status: 'BUILT',
        noApi: 'A heading.',
        children: [
          {
            id: 'settings.general',
            path: '/settings/general',
            requires: view(Section.SETTINGS),
            status: 'BUILT',
            endpoint: 'GET /api/settings',
          },
          {
            id: 'settings.email',
            path: '/settings/email',
            requires: view(Section.SETTINGS),
            status: 'BUILT',
            endpoint: 'GET /api/settings',
          },
          {
            id: 'settings.documents',
            path: '/settings/documents',
            requires: view(Section.SETTINGS),
            status: 'BUILT',
            endpoint: 'GET /api/settings',
          },
          {
            // Reference data is two items rather than one page, because it is two sections:
            // VAT classes are TAX_AND_CHARGES and units of measure are PRODUCTS. A single page
            // would show half its content to a role holding one grant and look broken.
            id: 'settings.vatClasses',
            path: '/settings/vat-classes',
            requires: view(Section.TAX_AND_CHARGES),
            status: 'BUILT',
            endpoint: 'GET /api/vat-classes',
          },
          {
            id: 'settings.unitsOfMeasure',
            path: '/settings/units-of-measure',
            requires: view(Section.PRODUCTS),
            status: 'BUILT',
            endpoint: 'GET /api/units-of-measure',
          },
          {
            id: 'settings.retention',
            path: '/settings/retention',
            requires: view(Section.SETTINGS),
            status: 'BUILT',
            endpoint: 'GET /api/settings',
          },
          {
            id: 'settings.adapters',
            path: '/settings/adapters',
            requires: view(Section.SETTINGS),
            status: 'BUILT',
            noApi: 'Renders the adapter registry read-only. Nothing is stored: there is no toggle API.',
          },
          {
            id: 'settings.modules',
            path: '/settings/modules',
            requires: view(Section.SETTINGS),
            status: 'BUILT',
            noApi: 'Renders the module registry read-only. Nothing is stored: there is no toggle API.',
          },
        ],
      },
      {
        id: 'users',
        path: '/users',
        requires: view(Section.USERS_AND_ROLES),
        status: 'BUILT',
        endpoint: 'GET /api/users',
      },
      {
        id: 'emailOutbox',
        path: '/email-outbox',
        requires: view(Section.EMAIL_OUTBOX),
        status: 'BUILT',
        endpoint: 'GET /api/email/outbox',
      },
      {
        // No status or history API exists, so there is nothing to show yet — and no Section
        // governs backups either, which is a question for whoever builds the screen.
        id: 'backups',
        path: '/backups',
        requires: NONE,
        status: 'NOT_BUILT',
      },
      {
        id: 'systemHealth',
        path: '/system-health',
        requires: NONE,
        status: 'NOT_BUILT',
      },
    ],
  },
]

/**
 * Two things deliberately absent from this tree:
 *
 * **`AUDIT_LOG`** is a grantable section with zero routes. It is not the Journal — the Journal is
 * the ledger, the audit log is who-changed-what — and giving it a menu item would recreate exactly
 * the permanent placeholder that step 16b existed to remove.
 *
 * **Appearance** was to hold the font choice. The font is Manrope, hardcoded, so the page would
 * have nothing on it. There is also nowhere to put such a preference: `SettingsCatalog` is a fixed
 * enum with no font key, and the only per-user field the backend stores is `language`.
 */
