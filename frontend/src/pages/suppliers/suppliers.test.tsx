import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'

import {
  AccessLevel,
  Section,
  VatStatus,
  type Me,
  type SupplierView,
} from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import i18n from '@/i18n'
import { OWNER_ROLE, aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { SupplierCreate } from './supplier-create'
import { SupplierDetail } from './supplier-detail'
import { SuppliersList } from './suppliers-list'

/**
 * Suppliers — the second master-data screen, and the first built on the foundations *after* the
 * bugfix pass that found what a one-row fixture hides.
 *
 * So the three guards that pass established are asserted here from the start, not added later:
 * a filter change must not wedge the table, a refused write must say why, and a select must show
 * a label rather than the value behind it.
 */

const owner: Me = aUser({
  id: 1,
  role: OWNER_ROLE,
  sections: everySectionAt(AccessLevel.FULL),
})

/** Holds SUPPLIERS but not TAX_AND_CHARGES — so exemption reasons are not readable. */
const buyer: Me = aUser({
  id: 2,
  role: { id: 4, name: 'BUYER', fullAccess: false, systemRole: false },
  sections: [{ section: Section.SUPPLIERS, level: AccessLevel.FULL, available: true }],
})

const importer: SupplierView = {
  id: 7,
  name: 'Coffee Importers SA',
  email: 'orders@coffee-importers.example',
  phone: '+30 210 0000000',
  vatNumber: 'EL123456789',
  vatStatus: VatStatus.DOMESTIC,
  active: true,
}

const retired: SupplierView = { ...importer, id: 8, name: 'Old Roasters Ltd', active: false }

let me: Me = owner

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/suppliers', ({ request }) => {
    const active = new URL(request.url).searchParams.get('active')
    return HttpResponse.json({ items: active === 'true' ? [importer] : [importer, retired] })
  }),
  http.get('http://localhost/api/suppliers/7', () => HttpResponse.json(importer)),
  http.get('http://localhost/api/vat-exemption-reasons', () =>
    HttpResponse.json({
      items: [
        { id: 3, code: 39, description: 'Article 39 — small business' },
        { id: 4, code: 27, description: 'Article 27 — exports' },
      ],
    }),
  ),
  http.patch('http://localhost/api/suppliers/7/name', async ({ request }) => {
    const body = (await request.json()) as { name: string }
    return HttpResponse.json({ ...importer, name: body.name })
  }),
  http.patch('http://localhost/api/suppliers/7/contact-details', async ({ request }) => {
    const body = (await request.json()) as { email?: string; phone?: string }
    return HttpResponse.json({ ...importer, ...body })
  }),
  http.patch('http://localhost/api/suppliers/7/vat-status', async ({ request }) => {
    const body = (await request.json()) as Record<string, unknown>
    return HttpResponse.json({ ...importer, ...body })
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
        <SuppliersList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/suppliers/7']}>
        <Routes>
          <Route path="/suppliers/:id" element={<SupplierDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderCreate() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/suppliers/new']}>
        <Routes>
          <Route path="/suppliers/new" element={<SupplierCreate />} />
          <Route path="/suppliers/:id" element={<p>created</p>} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

async function chooseOption(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  option: string,
) {
  await user.click(screen.getByLabelText(label))
  await user.click(await screen.findByRole('option', { name: option }))
}

describe('the supplier list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByText('Coffee Importers SA')
    requests.expectNoWrites()
  })

  it('shows the VAT status by its label, not the enum constant', async () => {
    renderList()
    expect(await screen.findByText('Domestic')).toBeInTheDocument()
    expect(screen.queryByText('DOMESTIC')).not.toBeInTheDocument()
  })

  it('refetches without the filter when active-only is unticked, and survives it', async () => {
    // The interaction that wedged the whole tab on Products. The loop guard itself lives in
    // data-table-loop.test.tsx, which needs a delayed response to reproduce it; this asserts the
    // plain outcome on the real screen.
    const user = userEvent.setup()
    renderList()
    await screen.findByText('Coffee Importers SA')
    expect(screen.queryByText('Old Roasters Ltd')).not.toBeInTheDocument()

    await user.click(screen.getByRole('checkbox'))

    expect(await screen.findByText('Old Roasters Ltd')).toBeInTheDocument()
  })
})

describe('the supplier detail', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: 'Coffee Importers SA' })
    requests.expectNoWrites()
  })

  it('saves email and phone in one request, because they are one route', async () => {
    const user = userEvent.setup()
    renderDetail()
    await screen.findByRole('heading', { name: 'Coffee Importers SA' })

    // Name, VAT number, Contact — the order the card lays them out.
    await user.click(screen.getAllByRole('button', { name: 'Edit' })[2]!)
    const phone = screen.getByLabelText('Phone')
    await user.clear(phone)
    await user.type(phone, '+30 211 1111111')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(requests.writes().map((r) => `${r.method} ${r.path}`)).toEqual([
        'PATCH /api/suppliers/7/contact-details',
      ]),
    )
  })

  it('asks for an exemption reason only once the status needs one', async () => {
    const user = userEvent.setup()
    renderDetail()
    await screen.findByRole('heading', { name: 'Coffee Importers SA' })

    await user.click(screen.getAllByRole('button', { name: 'Edit' })[3]!)
    // DOMESTIC needs nothing, so there is no reason control at all.
    expect(screen.queryByLabelText('Exemption reason')).not.toBeInTheDocument()

    await chooseOption(user, 'VAT status', 'Exempt')

    // EXEMPT requires one — the backend's rule, applied before the request rather than reported
    // after it.
    expect(await screen.findByLabelText('Exemption reason')).toBeInTheDocument()
    expect(
      screen.getByText('This VAT status needs an exemption reason before it can be saved.'),
    ).toBeInTheDocument()
  })

  it('will not save a status that requires a reason without one', async () => {
    const user = userEvent.setup()
    renderDetail()
    await screen.findByRole('heading', { name: 'Coffee Importers SA' })

    await user.click(screen.getAllByRole('button', { name: 'Edit' })[3]!)
    await chooseOption(user, 'VAT status', 'Exempt')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    // Nothing goes to a route that would refuse it.
    requests.expectNoWrites()
  })

  it('sends both values together once the reason is chosen', async () => {
    const user = userEvent.setup()
    renderDetail()
    await screen.findByRole('heading', { name: 'Coffee Importers SA' })

    await user.click(screen.getAllByRole('button', { name: 'Edit' })[3]!)
    await chooseOption(user, 'VAT status', 'Exempt')
    await chooseOption(user, 'Exemption reason', 'Article 39 — small business')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(requests.called('PATCH', '/api/suppliers/7/vat-status')).toBe(true),
    )
  })

  it('shows the reason it cannot save an intra-EU status without a VAT number', async () => {
    server.use(
      http.get('http://localhost/api/suppliers/7', () =>
        HttpResponse.json({ ...importer, vatNumber: undefined }),
      ),
    )
    const user = userEvent.setup()
    renderDetail()
    await screen.findByRole('heading', { name: 'Coffee Importers SA' })

    await user.click(screen.getAllByRole('button', { name: 'Edit' })[3]!)
    await chooseOption(user, 'VAT status', 'Intra-EU B2B')

    // A VAT number is a different route, so this cannot be fixed from here and the message says
    // where to go rather than leaving Save to do nothing without explanation.
    expect(await screen.findByText(/needs a VAT number/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Save' }))
    requests.expectNoWrites()
  })

  it('shows the backend’s reason when a deactivation is refused', async () => {
    server.use(
      http.post('http://localhost/api/suppliers/7/deactivate', () =>
        HttpResponse.json(
          {
            status: 422,
            title: 'Unprocessable Content',
            detail: 'Supplier has open purchase invoices and cannot be deactivated.',
          },
          { status: 422, headers: { 'content-type': 'application/problem+json' } },
        ),
      ),
    )
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()

    try {
      renderDetail()
      await screen.findByRole('heading', { name: 'Coffee Importers SA' })
      await user.click(screen.getByRole('button', { name: 'Deactivate' }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/open purchase invoices/)
    } finally {
      confirm.mockRestore()
    }
  })

  it('does not ask for reference data the role has no grant for', async () => {
    me = buyer
    renderDetail()
    await screen.findByRole('heading', { name: 'Coffee Importers SA' })

    // BUYER holds SUPPLIERS and nothing else, so the exemption-reason lookup would be a 403 about
    // something the operator never asked for. Standing policy: it is not requested at all.
    await waitFor(() => expect(requests.called('GET', '/api/suppliers/7')).toBe(true))
    expect(requests.called('GET', '/api/vat-exemption-reasons')).toBe(false)
  })

  it('renders the Greek labels when the language is Greek', async () => {
    await i18n.changeLanguage('el')
    try {
      renderDetail()
      expect(await screen.findByText('Στοιχεία')).toBeInTheDocument()
    } finally {
      await i18n.changeLanguage('en')
    }
  })
})

describe('creating a supplier', () => {
  it('sends no write merely by rendering', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'New supplier' })
    requests.expectNoWrites()
  })

  it('never defaults the VAT status — it cannot be submitted without one', async () => {
    // NewSupplier's javadoc says so in as many words: assuming DOMESTIC for an import supplier is
    // the guess CLAUDE.md rule 7 exists to prevent.
    const user = userEvent.setup()
    renderCreate()
    await screen.findByRole('button', { name: 'New supplier' })

    await user.type(screen.getByLabelText('Name'), 'New Importers SA')
    expect(screen.getByRole('button', { name: 'New supplier' })).toBeDisabled()

    await chooseOption(user, 'VAT status', 'Domestic')
    expect(screen.getByRole('button', { name: 'New supplier' })).toBeEnabled()
  })

  it('sends what was filled in, and nothing that was not', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      http.post('http://localhost/api/suppliers', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...importer, id: 9 }, { status: 201 })
      }),
    )

    const user = userEvent.setup()
    renderCreate()
    await screen.findByRole('button', { name: 'New supplier' })

    await user.type(screen.getByLabelText('Name'), 'New Importers SA')
    await chooseOption(user, 'VAT status', 'Domestic')
    await user.click(screen.getByRole('button', { name: 'New supplier' }))

    await waitFor(() => expect(body).toBeDefined())
    expect(body).toEqual({ name: 'New Importers SA', vatStatus: 'DOMESTIC' })
    // An empty optional field is absent, not an empty string: "" is a value somebody typed.
    expect(Object.hasOwn(body!, 'email')).toBe(false)
  })
})
