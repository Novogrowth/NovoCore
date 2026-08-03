import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { AccessLevel, Section, type Me, type RoleView } from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { RoleCreate } from './role-create'
import { RoleDetail } from './role-detail'
import { RolesList } from './roles-list'

/**
 * Roles — the permission document, and the grid that edits it.
 *
 * The decisions these tests hold to, both taken before anything was built:
 *
 * - **a segmented three-state toggle per section**, and
 * - **every level the caller cannot confer is disabled with its reason, never hidden** — the
 *   `lockedReason` half of the distinction Customers established.
 *
 * Plus the one thing reading `sectionGrants` naively gets wrong: a **full-access role holds
 * everything with no grant rows at all**, so a grid built from the map alone states the opposite of
 * the truth about the two most privileged roles in the system.
 */

const owner: Me = aUser({
  id: 1,
  role: { id: 1, name: 'OWNER', fullAccess: true, systemRole: true },
  sections: everySectionAt(AccessLevel.FULL),
})

/** Holds USERS_AND_ROLES in full and SALES at VIEW — so it may confer VIEW on Sales, never FULL. */
const limitedAdmin: Me = aUser({
  id: 7,
  role: { id: 4, name: 'PROBE-ADMIN', fullAccess: false, systemRole: false },
  sections: [
    { section: Section.USERS_AND_ROLES, level: AccessLevel.FULL, available: true },
    { section: Section.SALES, level: AccessLevel.VIEW, available: true },
  ],
})

/** Can read the section and change nothing in it. */
const viewer: Me = aUser({
  id: 8,
  role: { id: 5, name: 'VIEWER', fullAccess: false, systemRole: false },
  sections: [{ section: Section.USERS_AND_ROLES, level: AccessLevel.VIEW, available: true }],
})

const ownerRole: RoleView = {
  id: 1,
  name: 'OWNER',
  description: 'The seeded system role',
  fullAccess: true,
  systemRole: true,
  active: true,
  sectionGrants: {},
  restrictedFields: [],
}

const custom: RoleView = {
  id: 3,
  name: 'TEST-ROLE-SHOP',
  description: 'The till',
  fullAccess: false,
  systemRole: false,
  active: true,
  sectionGrants: { [Section.PRODUCTS]: AccessLevel.VIEW },
  restrictedFields: [],
}

/** The account the limited administrator is signed in as holds this one. */
const ownRole: RoleView = { ...custom, id: 4, name: 'PROBE-ADMIN' }

let me: Me = owner

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/roles', () =>
    HttpResponse.json({ items: [ownerRole, custom] }),
  ),
  http.get('http://localhost/api/roles/1', () => HttpResponse.json(ownerRole)),
  http.get('http://localhost/api/roles/3', () => HttpResponse.json(custom)),
  http.get('http://localhost/api/roles/4', () => HttpResponse.json(ownRole)),
  http.get('http://localhost/api/roles/:id/users', () =>
    HttpResponse.json({
      items: [{ id: 1, username: 'kostas', displayName: 'Kostas', role: { id: 3 }, active: true }],
    }),
  ),
  http.get('http://localhost/api/sections', () =>
    HttpResponse.json({
      items: [
        { section: Section.PRODUCTS, available: true },
        { section: Section.SALES, available: true },
        { section: Section.SALES_ORDER_FULFILLMENT, available: false },
      ],
    }),
  ),
)

const requests = trackRequests(server)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  me = owner
  requests.reset()
})
afterAll(() => server.close())

function renderList() {
  return render(
    <AppQueryProvider>
      <MemoryRouter>
        <RolesList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail(id: number) {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={[`/roles/${id}`]}>
        <Routes>
          <Route path="/roles/:id" element={<RoleDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderCreate() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/roles/new']}>
        <Routes>
          <Route path="/roles/new" element={<RoleCreate />} />
          <Route path="/roles/:id" element={<p>created</p>} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

/**
 * The segmented control for one row, found by the label it gives itself.
 *
 * `find`, not `get`: the catalogue behind the grid is a second query, so the rows arrive after the
 * heading does. A synchronous lookup here passes or fails on timing rather than on behaviour.
 */
const rowFor = (label: string) => screen.findByRole('group', { name: label })

describe('the role list', () => {
  it('sends the typed term as ?search=, alongside the active-only filter', async () => {
    const seen: URLSearchParams[] = []
    server.use(
      http.get('http://localhost/api/roles', ({ request }) => {
        seen.push(new URL(request.url).searchParams)
        return HttpResponse.json({ items: [] })
      }),
    )

    const user = userEvent.setup()
    renderList()

    await user.type(screen.getByLabelText('Search'), 'ware')

    await waitFor(() => expect(seen.at(-1)?.get('search')).toBe('ware'))
    expect(seen.at(-1)?.get('active')).toBe('true')
  })

  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByText('TEST-ROLE-SHOP')
    requests.expectNoWrites()
  })

  it('says a full-access role holds everything, rather than counting its zero grants', async () => {
    // Owner and Admin carry no grant rows at all. Counting the map would report "0 sections" for
    // the two roles that can see the most.
    renderList()
    expect(await screen.findByText('Everything')).toBeInTheDocument()
    expect(screen.getByText('1 section')).toBeInTheDocument()
  })
})

describe('the grant grid', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail(3)
    await screen.findByRole('heading', { name: 'TEST-ROLE-SHOP' })
    requests.expectNoWrites()
  })

  it('draws a row per section from the catalogue, not per grant the role happens to hold', async () => {
    // This role has one grant. The rows an administrator is looking for are the other two.
    renderDetail(3)
    await screen.findByRole('heading', { name: 'TEST-ROLE-SHOP' })

    expect(await rowFor('Products')).toBeInTheDocument()
    expect(await rowFor('Sales')).toBeInTheDocument()
    expect(await rowFor('Sales order fulfillment')).toBeInTheDocument()
  })

  it('marks a section that can be granted and leads nowhere', async () => {
    renderDetail(3)
    await screen.findByRole('heading', { name: 'TEST-ROLE-SHOP' })
    expect(await screen.findByText('Not built yet')).toBeInTheDocument()
  })

  it('sends one request for the cell that changed', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      http.put('http://localhost/api/roles/3/grants/SALES', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...custom, sectionGrants: { SALES: AccessLevel.FULL } })
      }),
    )

    const user = userEvent.setup()
    renderDetail(3)
    await screen.findByRole('heading', { name: 'TEST-ROLE-SHOP' })

    await user.click(within(await rowFor('Sales')).getByRole('button', { name: 'Full' }))

    await waitFor(() => expect(body).toBeDefined())
    expect(body).toEqual({ accessLevel: 'FULL' })
    expect(requests.writes()).toHaveLength(1)
  })

  it('shows a full-access role as Full everywhere, not as an empty grid', async () => {
    /*
     * The trap this exists for: `RoleView.sectionGrants` is `{}` for Owner and Admin, whose access
     * is the `fullAccess` flag. Reading the map alone would draw every row as None — the screen
     * stating the exact opposite of the truth about the most privileged role in the system.
     */
    renderDetail(1)
    await screen.findByRole('heading', { name: 'OWNER' })

    for (const label of ['Products', 'Sales']) {
      expect(within(await rowFor(label)).getByRole('button', { name: 'Full' })).toHaveAttribute(
        'aria-pressed',
        'true',
      )
    }
  })

  it('locks a system role entirely, and says why', async () => {
    renderDetail(1)
    await screen.findByRole('heading', { name: 'OWNER' })

    expect(within(await rowFor('Products')).getByRole('button', { name: 'None' })).toBeDisabled()
    expect(screen.getAllByRole('button', { name: /^Edit / })[0]).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Deactivate' })).toBeDisabled()

    // getAllByText, not getByText, and the change is the assertion getting STRONGER rather than
    // being relaxed to pass. Until 2026-08-03 the description was plain text with no editor, so
    // exactly one field carried this reason. It is a FieldEditor now (backend queue item 5), so
    // both name and description carry it — and "both, not one" is the thing worth asserting: the
    // new editor must be locked for a system role the same way the old one was.
    expect(screen.getAllByText(/is a system role and cannot be changed/i)).toHaveLength(2)
  })

  it('changes a description, which had no route until backend queue item 5', async () => {
    /*
     * ⚠️ **This screen test is over `msw` and therefore proves the wiring, not the contract.**
     * `CLAUDE.md`'s standing rule: a mock answers whatever it was told to. That the backend
     * ACCEPTS this body is proved by `UserRoleEndpointIT`, against the real server.
     *
     * What it does prove, and what the two deleted notes were about: the description is editable
     * at all, and the request goes to `PATCH /api/roles/{id}/description` rather than to the
     * rename route with a stray key — which the server would silently ignore.
     */
    const sent: { path: string; body: unknown }[] = []
    server.use(
      http.patch('http://localhost/api/roles/:id/description', async ({ request, params }) => {
        sent.push({ path: `/api/roles/${String(params.id)}/description`, body: await request.json() })
        return HttpResponse.json({ ...custom, description: 'The till, corrected' })
      }),
    )

    renderDetail(3)
    await screen.findByRole('heading', { name: 'TEST-ROLE-SHOP' })

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Edit Description' }))
    const field = screen.getByRole('textbox', { name: 'Description' })
    await user.clear(field)
    await user.type(field, 'The till, corrected')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(sent).toHaveLength(1))
    expect(sent[0]).toEqual({
      path: '/api/roles/3/description',
      body: { description: 'The till, corrected' },
    })
  })

  it('refuses to let you edit your own role, before sending anything', async () => {
    // `refuseIfCallerHolds` answers 422 for every cell of it. Offering an editor where every
    // change is refused is worse than saying so once.
    me = limitedAdmin
    renderDetail(4)
    await screen.findByRole('heading', { name: 'PROBE-ADMIN' })

    expect(within(await rowFor('Products')).getByRole('button', { name: 'View' })).toBeDisabled()
    expect(screen.getByText(/your own role/i)).toBeInTheDocument()
    requests.expectNoWrites()
  })

  it('offers only the levels the caller holds — and never locks None', async () => {
    /*
     * The rule is "no wider than you hold", not "nothing at all". This administrator holds SALES at
     * VIEW, so on Sales: VIEW yes, FULL no. Revoking is always allowed, including on a section the
     * caller cannot see at all — taking access away must not require the access being taken.
     */
    me = limitedAdmin
    renderDetail(3)
    await screen.findByRole('heading', { name: 'TEST-ROLE-SHOP' })

    const sales = await rowFor('Sales')
    expect(within(sales).getByRole('button', { name: 'View' })).toBeEnabled()
    expect(within(sales).getByRole('button', { name: 'Full' })).toBeDisabled()
    expect(within(sales).getByRole('button', { name: 'None' })).toBeEnabled()

    // Products: this role holds nothing there, so it may only revoke.
    const products = await rowFor('Products')
    expect(within(products).getByRole('button', { name: 'None' })).toBeEnabled()
    expect(within(products).getByRole('button', { name: 'View' })).toBeDisabled()
  })

  it('gives a VIEW role no controls at all, only the levels as text', async () => {
    // "Not yours to edit" gets no affordance — the standing distinction. A disabled grid would
    // invite somebody to keep trying at something their role will never allow.
    me = viewer
    renderDetail(3)
    await screen.findByRole('heading', { name: 'TEST-ROLE-SHOP' })

    expect(screen.queryByRole('group', { name: 'Products' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Edit / })).not.toBeInTheDocument()
    // The levels are still stated — read-only is not the same as blank.
    expect(await screen.findAllByText('View')).not.toHaveLength(0)
  })

  it('shows a refused grant against the row that caused it', async () => {
    server.use(
      http.put('http://localhost/api/roles/3/grants/SALES', () =>
        HttpResponse.json(
          {
            status: 422,
            title: 'Unprocessable Content',
            detail: 'You cannot grant FULL on SALES, because your own role has VIEW there.',
          },
          { status: 422, headers: { 'content-type': 'application/problem+json' } },
        ),
      ),
    )

    const user = userEvent.setup()
    renderDetail(3)
    await screen.findByRole('heading', { name: 'TEST-ROLE-SHOP' })

    await user.click(within(await rowFor('Sales')).getByRole('button', { name: 'Full' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'because your own role has VIEW there',
    )
  })
})

describe('field restrictions', () => {
  it('sends the boxed boolean the route requires', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      http.put(
        'http://localhost/api/roles/3/field-restrictions/PRODUCT_SUPPLIER',
        async ({ request }) => {
          body = (await request.json()) as Record<string, unknown>
          return HttpResponse.json({ ...custom, restrictedFields: ['PRODUCT_SUPPLIER'] })
        },
      ),
    )

    const user = userEvent.setup()
    renderDetail(3)
    await screen.findByRole('heading', { name: 'TEST-ROLE-SHOP' })

    await user.click(within(await rowFor('Supplier')).getByRole('button', { name: 'Hidden' }))

    await waitFor(() => expect(body).toBeDefined())
    // An omitted `restricted` would silently REMOVE a restriction nobody mentioned, which is why
    // the backend boxed it. The client must always state it.
    expect(body).toEqual({ restricted: true })
  })
})

describe("a role's holders", () => {
  it('names the people, because the deactivation refusal names only a count', async () => {
    renderDetail(3)
    await screen.findByRole('heading', { name: 'TEST-ROLE-SHOP' })
    expect(await screen.findByText('kostas')).toBeInTheDocument()
  })
})

describe('creating a role', () => {
  it('sends a name and a description, and cannot send grants', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      http.post('http://localhost/api/roles', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...custom, id: 9 }, { status: 201 })
      }),
    )

    const user = userEvent.setup()
    renderCreate()
    await screen.findByRole('button', { name: 'New role' })

    await user.type(screen.getByLabelText('Name'), 'TEST-ROLE-NEW')
    await user.type(screen.getByLabelText('Description'), 'A probe')
    await user.click(screen.getByRole('button', { name: 'New role' }))

    await waitFor(() => expect(body).toBeDefined())
    expect(body).toEqual({ name: 'TEST-ROLE-NEW', description: 'A probe' })
    // `NewRole` has no room for them, and the screen must not imply otherwise.
    expect(Object.hasOwn(body!, 'sectionGrants')).toBe(false)
    expect(Object.hasOwn(body!, 'fullAccess')).toBe(false)
  })

  it('says that a new role grants nothing until somebody grants it', async () => {
    renderCreate()
    expect(await screen.findByText(/A new role grants nothing at all/i)).toBeInTheDocument()
  })
})
