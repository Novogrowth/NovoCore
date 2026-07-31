import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'

import {
  AccessLevel,
  CustomerSystemKey,
  Section,
  VatStatus,
  type CustomerView,
  type Me,
} from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { trackRequests } from '@/test/requests'

import { CustomerCreate } from './customer-create'
import { CustomerDetail } from './customer-detail'
import { CustomersList } from './customers-list'

/**
 * Customers — the supplier screens plus the one thing suppliers do not have: a **structural
 * record** whose VAT treatment is fixed and which cannot be deactivated.
 *
 * The decision these tests hold to: those controls are **shown disabled with the reason**, never
 * hidden and never left live to produce a refusal. Two of the three refusals are, today, a bare
 * `400` carrying nothing (backend item 4), so "let the backend say" was never an option here.
 */

const sections = (level: AccessLevel) =>
  Object.values(Section).map((section) => ({ section, level, available: true }))

const owner: Me = {
  id: 1,
  username: 'owner',
  role: { id: 1, name: 'OWNER', fullAccess: true, systemRole: true },
  sections: sections(AccessLevel.FULL),
  restrictedFields: [],
}

/** Customers at VIEW only — the case that must produce no affordance at all, locked or otherwise. */
const viewer: Me = {
  id: 3,
  username: 'viewer',
  role: { id: 5, name: 'VIEWER', fullAccess: false, systemRole: false },
  sections: [{ section: Section.CUSTOMERS, level: AccessLevel.VIEW, available: true }],
  restrictedFields: [],
}

const cafe: CustomerView = {
  id: 4,
  name: 'TEST-CUSTOMER-03 Cafe',
  email: 'cafe@test.invalid',
  phone: '2100000003',
  vatNumber: 'EL999100003',
  vatStatus: VatStatus.DOMESTIC,
  active: true,
}

/** The seeded retail record. `systemKey` is what the spec exposes; `systemRecord` is not in it. */
const retail: CustomerView = {
  id: 1,
  name: 'Πελάτης Λιανικής',
  vatStatus: VatStatus.DOMESTIC,
  systemKey: CustomerSystemKey.RETAIL_WALK_IN,
  active: true,
}

let me: Me = owner

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/customers', () => HttpResponse.json({ items: [retail, cafe] })),
  http.get('http://localhost/api/customers/4', () => HttpResponse.json(cafe)),
  http.get('http://localhost/api/customers/1', () => HttpResponse.json(retail)),
  http.get('http://localhost/api/vat-exemption-reasons', () =>
    HttpResponse.json({ items: [{ id: 3, code: 39, description: 'Article 39 — small business' }] }),
  ),
  http.patch('http://localhost/api/customers/4/name', async ({ request }) => {
    const body = (await request.json()) as { name: string }
    return HttpResponse.json({ ...cafe, name: body.name })
  }),
  http.patch('http://localhost/api/customers/1/name', async ({ request }) => {
    const body = (await request.json()) as { name: string }
    return HttpResponse.json({ ...retail, name: body.name })
  }),
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
        <CustomersList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail(id: number) {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={[`/customers/${id}`]}>
        <Routes>
          <Route path="/customers/:id" element={<CustomerDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderCreate() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/customers/new']}>
        <Routes>
          <Route path="/customers/new" element={<CustomerCreate />} />
          <Route path="/customers/:id" element={<p>created</p>} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the customer list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByText('TEST-CUSTOMER-03 Cafe')
    requests.expectNoWrites()
  })

  it('marks the structural record, so it is known before it is opened', async () => {
    renderList()
    expect(await screen.findByText('System record')).toBeInTheDocument()
  })
})

describe('the structural retail record', () => {
  it('cannot be deactivated, and the button says so rather than vanishing', async () => {
    renderDetail(1)
    await screen.findByRole('heading', { name: 'Πελάτης Λιανικής' })

    // Present and disabled — not absent. A control that disappears reads as a bug; this is a rule.
    expect(screen.getByRole('button', { name: 'Deactivate' })).toBeDisabled()
    expect(screen.getByText(/every till sale with no identified buyer/i)).toBeInTheDocument()
  })

  it('locks the VAT status with the reason, rather than letting the backend refuse it', async () => {
    /*
     * Letting it through would answer `400 "Bad request."` and nothing else — the domain throws
     * IllegalArgumentException, so WebExceptionHandler correctly discards a message that is far
     * better than this one. Until backend item 4 lands, the screen is the only place the reason
     * can come from.
     */
    renderDetail(1)
    await screen.findByRole('heading', { name: 'Πελάτης Λιανικής' })

    const edits = screen.getAllByRole('button', { name: 'Edit' })
    // Name, VAT number, Contact, VAT status.
    expect(edits[1]).toBeDisabled()
    expect(edits[3]).toBeDisabled()
    expect(screen.getByText(/cannot make a claim about a party's VAT status/i)).toBeInTheDocument()
    expect(screen.getByText(/cannot hold one party's VAT number/i)).toBeInTheDocument()
  })

  it('still lets its name be edited, because the backend does', async () => {
    // Only the VAT treatment and deactivation are fixed. Locking the name too would be the screen
    // inventing a rule, which is the same error as ignoring one.
    const user = userEvent.setup()
    renderDetail(1)
    await screen.findByRole('heading', { name: 'Πελάτης Λιανικής' })

    const edits = screen.getAllByRole('button', { name: 'Edit' })
    expect(edits[0]).toBeEnabled()

    await user.click(edits[0]!)
    const field = screen.getByLabelText('Name')
    await user.clear(field)
    await user.type(field, 'Λιανική')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(requests.called('PATCH', '/api/customers/1/name')).toBe(true))
  })

  it('offers an ordinary customer everything', async () => {
    renderDetail(4)
    await screen.findByRole('heading', { name: 'TEST-CUSTOMER-03 Cafe' })

    expect(screen.getByRole('button', { name: 'Deactivate' })).toBeEnabled()
    for (const edit of screen.getAllByRole('button', { name: 'Edit' })) {
      expect(edit).toBeEnabled()
    }
  })

  it('shows a VIEW role no affordance at all, locked or otherwise', async () => {
    // The distinction FieldEditor draws on purpose: "not yours to edit" gets no button, where
    // "fixed on this record" gets a disabled one with a reason. A disabled button for a VIEW role
    // would invite somebody to keep trying.
    me = viewer
    renderDetail(1)
    await screen.findByRole('heading', { name: 'Πελάτης Λιανικής' })

    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
    expect(screen.queryByText(/every till sale/i)).not.toBeInTheDocument()
  })
})

describe('the customer detail', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail(4)
    await screen.findByRole('heading', { name: 'TEST-CUSTOMER-03 Cafe' })
    requests.expectNoWrites()
  })

  it('shows the backend’s reason when a deactivation is refused', async () => {
    server.use(
      http.post('http://localhost/api/customers/4/deactivate', () =>
        HttpResponse.json(
          { status: 422, title: 'Unprocessable Content', detail: 'Customer has unsettled invoices.' },
          { status: 422, headers: { 'content-type': 'application/problem+json' } },
        ),
      ),
    )
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()

    try {
      renderDetail(4)
      await screen.findByRole('heading', { name: 'TEST-CUSTOMER-03 Cafe' })
      await user.click(screen.getByRole('button', { name: 'Deactivate' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('unsettled invoices')
    } finally {
      confirm.mockRestore()
    }
  })

  it('does not offer the VAT class override, which is deferred', async () => {
    // Deferred out of F2 on purpose. Asserted so that adding it is a deliberate act with a test to
    // update, rather than something that drifts in with a copied screen.
    renderDetail(4)
    await screen.findByRole('heading', { name: 'TEST-CUSTOMER-03 Cafe' })

    expect(screen.queryByText(/VAT class/i)).not.toBeInTheDocument()
  })
})

describe('creating a customer', () => {
  it('never defaults the VAT status', async () => {
    const user = userEvent.setup()
    renderCreate()
    await screen.findByRole('button', { name: 'New customer' })

    await user.type(screen.getByLabelText('Name'), 'New Cafe')
    expect(screen.getByRole('button', { name: 'New customer' })).toBeDisabled()

    await user.click(screen.getByLabelText('VAT status'))
    await user.click(await screen.findByRole('option', { name: 'Domestic' }))
    expect(screen.getByRole('button', { name: 'New customer' })).toBeEnabled()
  })

  it('sends what was filled in, and no VAT class override', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      http.post('http://localhost/api/customers', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...cafe, id: 9 }, { status: 201 })
      }),
    )

    const user = userEvent.setup()
    renderCreate()
    await screen.findByRole('button', { name: 'New customer' })

    await user.type(screen.getByLabelText('Name'), 'New Cafe')
    await user.click(screen.getByLabelText('VAT status'))
    await user.click(await screen.findByRole('option', { name: 'Domestic' }))
    await user.click(screen.getByRole('button', { name: 'New customer' }))

    await waitFor(() => expect(body).toBeDefined())
    expect(body).toEqual({ name: 'New Cafe', vatStatus: 'DOMESTIC' })
    expect(Object.hasOwn(body!, 'vatClassOverrideId')).toBe(false)
  })
})
