import { describe, expect, it } from 'vitest'

import spec from '../../../docs/api/openapi.json'
import { AccessLevel, Section, type Me } from '@/api/generated/model'
import { permissionsOf } from '@/auth/permissions'
import en from '@/i18n/locales/en/nav.json'
import el from '@/i18n/locales/el/nav.json'

import { MODULES } from './registry'
import { NAV_TREE } from './tree'
import { flatten, reachablePaths, visibleNav } from './visibility'

/**
 * The navigation tree, checked against the API it claims to be built on.
 *
 * The spec carries `x-novocore-section` and `x-novocore-level` on every route, written from the
 * live handler mapping. So the grant a menu item needs is not something this frontend gets to have
 * an opinion about — it is a fact, and these tests are where the tree is held to it. A screen
 * claiming the wrong grant otherwise shows up as a 403 in front of an operator.
 */

interface SpecOperation {
  'x-novocore-section'?: string | null
  'x-novocore-level'?: string
}

const OPERATIONS = new Map<string, SpecOperation>()
for (const [path, verbs] of Object.entries(spec.paths as Record<string, unknown>)) {
  for (const [verb, operation] of Object.entries(verbs as Record<string, SpecOperation>)) {
    OPERATIONS.set(`${verb.toUpperCase()} ${path}`, operation)
  }
}

const nodes = flatten(NAV_TREE)
const labels = { en, el } as Record<string, Record<string, string>>

describe('the navigation tree', () => {
  it('has a unique id for every node', () => {
    const ids = nodes.map((node) => node.id)
    expect(ids.length).toBe(new Set(ids).size)
  })

  it('has a unique path for every routable node', () => {
    const paths = nodes.filter((node) => node.path).map((node) => node.path)
    expect(paths.length).toBe(new Set(paths).size)
  })

  it.each(['en', 'el'])('has a %s label for every node', (language) => {
    const missing = nodes.filter((node) => !labels[language]?.[node.id])
    expect(missing.map((node) => node.id)).toEqual([])
  })

  it('has no label for a node that no longer exists', () => {
    // The other direction. Dead labels accumulate silently and are then translated, reviewed and
    // maintained for years after the screen they named was removed.
    const ids = new Set(nodes.map((node) => node.id))
    for (const language of ['en', 'el']) {
      const orphans = Object.keys(labels[language] ?? {}).filter((key) => !ids.has(key))
      expect(orphans, `${language} has labels for nodes that are not in the tree`).toEqual([])
    }
  })

  it('names an endpoint on every built item, or says why there is none', () => {
    const unexplained = nodes.filter(
      (node) => node.status === 'BUILT' && !node.endpoint && !node.noApi,
    )
    expect(
      unexplained.map((node) => node.id),
      'a built screen with no API behind it has to say so — set noApi with the reason',
    ).toEqual([])
  })

  it('names no endpoint on an item that is not built', () => {
    const contradictory = nodes.filter((node) => node.status === 'NOT_BUILT' && node.endpoint)
    expect(contradictory.map((node) => node.id)).toEqual([])
  })

  it('points every endpoint at a route that exists', () => {
    const dangling = nodes
      .filter((node) => node.endpoint)
      .filter((node) => !OPERATIONS.has(node.endpoint!))
    expect(dangling.map((node) => `${node.id} → ${node.endpoint!}`)).toEqual([])
  })

  it('requires exactly the grant its endpoint requires', () => {
    // The check this file exists for. The tree states a requirement; the spec states the truth.
    const mismatched: string[] = []

    for (const node of nodes) {
      if (!node.endpoint) continue
      const operation = OPERATIONS.get(node.endpoint)
      if (!operation) continue

      const section = operation['x-novocore-section']
      const level = operation['x-novocore-level']

      if (!section) {
        // An authenticated-only route needs no grant, so the item must not claim one.
        if (node.requires.length > 0) {
          mismatched.push(`${node.id} requires a grant, but ${node.endpoint} needs none`)
        }
        continue
      }

      const required = node.requires.find((requirement) => requirement.section === section)
      if (!required) {
        mismatched.push(`${node.id} does not require ${section}, which ${node.endpoint} needs`)
        continue
      }
      if (required.level !== level) {
        mismatched.push(
          `${node.id} requires ${section}/${required.level}, but ${node.endpoint} needs ${section}/${level}`,
        )
      }

      // "Exactly", not "at least". A node requiring a section its endpoint does not need hides
      // itself from a role that should see it — a permission bug that presents as a missing menu
      // item, which nobody reports as a bug.
      const surplus = node.requires.filter((requirement) => requirement.section !== section)
      if (surplus.length > 0) {
        mismatched.push(
          `${node.id} also requires ${surplus.map((r) => r.section).join(', ')}, which ${node.endpoint} does not need`,
        )
      }
    }

    expect(mismatched).toEqual([])
  })

  it('gives the AUDIT_LOG section no item, because it has no routes', () => {
    // It is a grantable section with zero endpoints, and it is not the Journal. If routes ever
    // appear for it, this test is the reminder that a screen is now possible.
    const auditRoutes = [...OPERATIONS.values()].filter(
      (operation) => operation['x-novocore-section'] === Section.AUDIT_LOG,
    )
    expect(auditRoutes).toEqual([])
    expect(
      nodes.some((node) =>
        node.requires.some((requirement) => requirement.section === Section.AUDIT_LOG),
      ),
    ).toBe(false)
  })

  it('keeps the module registry and the tree telling the same story', () => {
    /*
     * This replaced an assertion that could not fail: it checked that `RegistryEntry.section` was
     * one of `Section`'s values, which TypeScript already guarantees, so it passed by construction
     * and tested nothing.
     *
     * What is actually worth checking is that the two data structures agree. A module the registry
     * calls NOT_BUILT must not have a tree item claiming to be built, and an item that names a
     * module must require that module's section where one exists — otherwise the menu and the
     * registry can drift, and the Settings grid says "not built" beside a working link.
     *
     * What NO test here can check is whether the registry agrees with the *backend* about
     * `available`: that flag arrives at runtime on `/api/me` and is absent from the spec. The
     * design does not rely on a test for it — `visibility.ts` treats the backend's answer as
     * authoritative and downgrades a stale claim, which is asserted separately below.
     */
    const notBuilt = new Set(MODULES.filter((m) => m.status === 'NOT_BUILT').map((m) => m.key))

    const contradictions = nodes
      .filter((node) => node.module && notBuilt.has(node.module))
      .filter((node) => node.status !== 'NOT_BUILT')
    expect(contradictions.map((node) => node.id)).toEqual([])

    const mismatchedSections = nodes
      .filter((node) => node.module)
      .filter((node) => {
        const module = MODULES.find((m) => m.key === node.module)
        if (!module?.section) return false
        return !node.requires.some((requirement) => requirement.section === module.section)
      })
    expect(mismatchedSections.map((node) => node.id)).toEqual([])
  })
})

describe('navigation visibility', () => {
  const owner: Me = {
    role: { name: 'OWNER', fullAccess: true, systemRole: true },
    sections: Object.values(Section).map((section) => ({
      section,
      level: AccessLevel.FULL,
      // As the backend reports it: the two module sections are granted but not built.
      available:
        section !== Section.SALES_ORDER_FULFILLMENT && section !== Section.BACK_IN_STOCK_REMINDERS,
    })),
  }

  const settlementsOnly: Me = {
    role: { name: 'BOOKKEEPER', fullAccess: false, systemRole: false },
    sections: [{ section: Section.SETTLEMENTS, level: AccessLevel.VIEW, available: true }],
  }

  it('shows an owner the placeholders, disabled', () => {
    const visible = visibleNav(permissionsOf(owner))
    const operations = visible.find((entry) => entry.node.id === 'operations')
    const orders = operations?.children.find((entry) => entry.node.id === 'orders')

    expect(orders, 'an owner sees what is not built yet').toBeDefined()
    expect(orders?.enabled, 'and cannot click into it').toBe(false)
  })

  it('hides placeholders from everyone else', () => {
    const visible = visibleNav(permissionsOf(settlementsOnly))
    const ids = new Set<string>()
    const walk = (entries: ReturnType<typeof visibleNav>): void => {
      for (const entry of entries) {
        ids.add(entry.node.id)
        walk(entry.children)
      }
    }
    walk(visible)

    expect(ids.has('accounting.clearingChecks')).toBe(false)
    expect(ids.has('accounting.bankBalances')).toBe(false)
    // But the built items under the same grant are there.
    expect(ids.has('accounting.receipts')).toBe(true)
  })

  it('shows a group when any descendant is visible, not just the first', () => {
    // Finance's first child is Sales, and this role has no Sales grant at all. The heading has to
    // appear anyway, because Accounting → Receipts below it does.
    const visible = visibleNav(permissionsOf(settlementsOnly))
    const finance = visible.find((entry) => entry.node.id === 'finance')

    expect(finance, 'Finance appears for a role with no Sales grant').toBeDefined()
    expect(finance?.children.some((entry) => entry.node.id === 'sales')).toBe(false)
    expect(finance?.children.some((entry) => entry.node.id === 'accounting')).toBe(true)
  })

  it('hides a group entirely when nothing inside it is visible', () => {
    const visible = visibleNav(permissionsOf(settlementsOnly))
    expect(visible.some((entry) => entry.node.id === 'operations')).toBe(false)
    expect(visible.some((entry) => entry.node.id === 'system')).toBe(false)
  })

  it('lets the backend downgrade a built item, never the other way round', () => {
    // The tree says Customers is built. The backend says nothing is available behind it — which
    // would be the state during a rollback, or if a section were retired. The item must degrade
    // to a placeholder rather than offering a link that 404s.
    const backendDisagrees: Me = {
      role: { name: 'OWNER', fullAccess: true, systemRole: true },
      sections: [{ section: Section.CUSTOMERS, level: AccessLevel.FULL, available: false }],
    }
    const visible = visibleNav(permissionsOf(backendDisagrees))
    const customers = visible
      .find((entry) => entry.node.id === 'operations')
      ?.children.find((entry) => entry.node.id === 'customers')

    expect(customers).toBeDefined()
    expect(customers?.enabled).toBe(false)
  })

  it('never lets the backend upgrade a placeholder into a working link', () => {
    // The other direction, which the test above did not check. If the backend reports a section
    // as available while this tree still says NOT_BUILT, the item stays a placeholder: there is no
    // screen behind it here, whatever the server has. Enabling it would route to nothing.
    const backendIsAhead: Me = {
      role: { name: 'OWNER', fullAccess: true, systemRole: true },
      sections: [
        { section: Section.SALES_ORDER_FULFILLMENT, level: AccessLevel.FULL, available: true },
      ],
    }
    const visible = visibleNav(permissionsOf(backendIsAhead))
    const orders = visible
      .find((entry) => entry.node.id === 'operations')
      ?.children.find((entry) => entry.node.id === 'orders')

    expect(orders).toBeDefined()
    expect(orders?.enabled).toBe(false)
    expect(reachablePaths(visible).has('/orders')).toBe(false)
  })

  it('makes only enabled items reachable', () => {
    const reachable = reachablePaths(visibleNav(permissionsOf(owner)))
    expect(reachable.has('/customers')).toBe(true)
    // Granted in full to an owner, and still not built.
    expect(reachable.has('/orders')).toBe(false)
    expect(reachable.has('/system-health')).toBe(false)
  })

  it('shows nothing at all to nobody', () => {
    expect(visibleNav(permissionsOf(undefined))).toEqual([])
  })
})
