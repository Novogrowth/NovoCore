import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'

import { AccessLevel, ProtectedField, Section, type Me, type ProductView } from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import i18n from '@/i18n'
import { OWNER_ROLE, aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { ProductCreate } from './product-create'
import { ProductDetail } from './product-detail'
import { ProductsList } from './products-list'

/**
 * Products — the first screen, and therefore the first real exercise of the foundations:
 * money at two scales, per-record redaction, enum labels, grant-gated lookups, and the per-field
 * PATCH pattern every master-data screen after this one inherits.
 */

/** OWNER: every section, so lookups resolve and editing is offered. */
const owner: Me = aUser({
  id: 1,
  role: OWNER_ROLE,
  sections: everySectionAt(AccessLevel.FULL),
})

/** The seeded operational role: Products at VIEW, nothing else, no field restrictions since V26. */
const orderStaff: Me = aUser({
  id: 2,
  role: { id: 3, name: 'REMOTE_ORDER_STAFF', fullAccess: false, systemRole: false },
  sections: [{ section: Section.PRODUCTS, level: AccessLevel.VIEW, available: true }],
})

const espresso: ProductView = {
  id: 41,
  sku: 'ESP-001',
  name: 'Espresso blend 1kg',
  type: 'GOODS',
  unitOfMeasure: { id: 1, code: 'kg', name: 'Kilogram', fractionalQuantityAllowed: true, active: true },
  defaultVatClassId: 3,
  sellingPrice: { amount: '18.50', currency: 'EUR' },
  supplierId: 7,
  supplierSku: 'S-ESP',
  serialTracked: false,
  bundle: false,
  // Six decimals. Rendering this as money would show 12.51 for a value nobody entered.
  lastPurchasePrice: { amount: '12.505000', currency: 'EUR' },
  active: true,
  hiddenFields: [],
}

let me: Me = owner

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/products', () => HttpResponse.json({ items: [espresso] })),
  http.get('http://localhost/api/products/41', () => HttpResponse.json(espresso)),
  http.get('http://localhost/api/products/41/stock', () =>
    HttpResponse.json({ productId: 41, byLocation: { INVENTORY: '12.000000' } }),
  ),
  http.get('http://localhost/api/products/41/in-bundles', () => HttpResponse.json({ items: [] })),
  http.get('http://localhost/api/units-of-measure', () =>
    HttpResponse.json({ items: [{ id: 1, code: 'kg', name: 'Kilogram' }] }),
  ),
  http.get('http://localhost/api/vat-classes', () =>
    HttpResponse.json({ items: [{ id: 3, description: 'Standard 24%' }] }),
  ),
  http.get('http://localhost/api/suppliers', () =>
    HttpResponse.json({ items: [{ id: 7, name: 'Coffee Importers SA' }] }),
  ),
  http.patch('http://localhost/api/products/41/name', async ({ request }) => {
    const body = (await request.json()) as { name: string }
    return HttpResponse.json({ ...espresso, name: body.name })
  }),
  http.patch('http://localhost/api/products/41/selling-price', () =>
    HttpResponse.json({ ...espresso, sellingPrice: { amount: '19.90', currency: 'EUR' } }),
  ),
)

/**
 * Every request this screen made, recorded at the server rather than in each handler.
 *
 * That difference matters: a per-handler counter can only see requests the test thought to set up,
 * so a screen firing something unexpected would be invisible to it. This sees everything.
 */
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
        <ProductsList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

/** Opens a select by its label and takes the first thing in it. */
async function chooseFirstOption(user: ReturnType<typeof userEvent.setup>, label: string) {
  await user.click(screen.getByLabelText(label))
  const options = await screen.findAllByRole('option')
  await user.click(options[0]!)
}

function renderCreate() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/products/new']}>
        <Routes>
          <Route path="/products/new" element={<ProductCreate />} />
          <Route path="/products/:id" element={<p>created</p>} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/products/41']}>
        <Routes>
          <Route path="/products/:id" element={<ProductDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the product list', () => {
  it('shows money at two decimals and a unit cost at six', async () => {
    renderList()
    // Wait for the supplier lookup too: it re-renders the row, which detaches nodes found
    // before it landed. Everything below is re-queried after the screen has settled.
    await screen.findByText('Coffee Importers SA')

    expect(screen.getByText('ESP-001')).toBeInTheDocument()
    // The distinction the review pass split formatMoney and formatUnitCost to preserve.
    expect(screen.getByText(/18.50/)).toBeInTheDocument()
    expect(screen.getByText(/12.505000/)).toBeInTheDocument()
  })

  it('resolves a supplier id to a name when the role may read suppliers', async () => {
    renderList()
    expect(await screen.findByText('Coffee Importers SA')).toBeInTheDocument()
  })

  it('refetches without the filter when active-only is unticked, and survives it', async () => {
    /*
     * The interaction that wedged the browser. Changing this filter changes the query key, so the
     * query holds no data while it refetches — and that was the state the table re-rendered itself
     * out of, permanently, in both Chrome and Firefox.
     *
     * The guard against the loop itself is `data-table-loop.test.tsx`, which needs a response that
     * does not arrive inside a microtask to reproduce it. This one asserts the plain thing the
     * operator wanted: unticking the box shows the inactive products.
     */
    const retired: ProductView = {
      ...espresso,
      id: 42,
      sku: 'OLD-001',
      name: 'Discontinued blend',
      active: false,
    }
    server.use(
      http.get('http://localhost/api/products', ({ request }) => {
        const active = new URL(request.url).searchParams.get('active')
        return HttpResponse.json({ items: active === 'true' ? [espresso] : [espresso, retired] })
      }),
    )

    const user = userEvent.setup()
    renderList()
    await screen.findByText('ESP-001')
    expect(screen.queryByText('OLD-001')).not.toBeInTheDocument()

    await user.click(screen.getByRole('checkbox'))

    expect(await screen.findByText('OLD-001')).toBeInTheDocument()
  })

  it('never asks for reference data the role has no grant for', async () => {
    me = orderStaff
    renderList()
    await screen.findByText('ESP-001')

    /*
     * Standing policy. REMOTE_ORDER_STAFF holds PRODUCTS and nothing else, so the supplier and VAT
     * class lookups would both be 403 — error toasts about something the operator never asked for.
     * The requests must not be made at all, and the id is shown instead of a name.
     */
    await waitFor(() => expect(requests.called('GET', '/api/products')).toBe(true))
    expect(requests.called('GET', '/api/suppliers')).toBe(false)
    expect(requests.called('GET', '/api/vat-classes')).toBe(false)
    expect(screen.getByText('#7')).toBeInTheDocument()
  })
})

describe('creating a product', () => {
  it('sends serialTracked, which the generated type calls optional and the backend requires', async () => {
    /*
     * **Creating a product failed for every user, every time, and no test could see it.**
     *
     * `NewProduct.serialTracked` is a primitive `boolean` on a Java record. Jackson hands an
     * absent creator property to the constructor as null, `FAIL_ON_NULL_FOR_PRIMITIVES` refuses
     * it, and the route answers `400 "Malformed request body: Cannot map null into type boolean"`
     * before any handler runs. The spec declares no required fields, so the generated type says
     * `serialTracked?: boolean` and the form was written correctly against a contract that was
     * wrong.
     *
     * ⚠️ **A mock server cannot catch this**, which is exactly how it survived a browser check: a
     * handler answers whatever body it is given. What this test can do — and what makes it fail
     * against the code that shipped — is assert the field is on the wire. The contract half is
     * pinned in `spec-hygiene.test.ts`.
     */
    let body: Record<string, unknown> | undefined
    server.use(
      http.post('http://localhost/api/products', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...espresso, id: 99 }, { status: 201 })
      }),
    )

    const user = userEvent.setup()
    renderCreate()
    await screen.findByRole('button', { name: 'New product' })

    await user.type(screen.getByLabelText('SKU'), 'NEW-001')
    await user.type(screen.getByLabelText('Name'), 'New blend')
    await user.type(screen.getByLabelText('Selling price'), '9.99')
    await chooseFirstOption(user, 'Unit')
    await chooseFirstOption(user, 'VAT class')

    await user.click(screen.getByRole('button', { name: 'New product' }))

    await waitFor(() => expect(body).toBeDefined())
    // Present, and false — not merely present.
    expect(Object.hasOwn(body!, 'serialTracked')).toBe(true)
    expect(body!.serialTracked).toBe(false)
  })
})

describe('the product detail', () => {
  it('sends no write merely by rendering', async () => {
    /*
     * The defect this exists for: the generated client had every POST/PATCH/DELETE emitted as a
     * useQuery, so mounting a screen that used one would have sent the write on mount — and again
     * on every refetch and window focus. Rendering must be a read.
     */
    renderDetail()
    await screen.findByRole('heading', { name: 'Espresso blend 1kg' })

    requests.expectNoWrites()
  })

  it('saves one field with one request and shows the server’s answer', async () => {
    const user = userEvent.setup()
    renderDetail()
    await screen.findByRole('heading', { name: 'Espresso blend 1kg' })

    await user.click(screen.getAllByRole('button', { name: 'Edit' })[0]!)
    const field = screen.getByLabelText('Name')
    await user.clear(field)
    await user.type(field, 'Espresso blend 500g')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('heading', { name: 'Espresso blend 500g' })).toBeInTheDocument()
    // Exactly one write, and nothing else was touched.
    expect(requests.writes().map((request) => `${request.method} ${request.path}`)).toEqual([
      'PATCH /api/products/41/name',
    ])
  })

  it('keeps a refused change on screen with the reason', async () => {
    server.use(
      http.patch('http://localhost/api/products/41/name', () =>
        HttpResponse.json(
          { status: 422, title: 'Unprocessable', detail: 'A product name cannot be blank.' },
          { status: 422, headers: { 'content-type': 'application/problem+json' } },
        ),
      ),
    )
    const user = userEvent.setup()
    renderDetail()
    await screen.findByRole('heading', { name: 'Espresso blend 1kg' })

    await user.click(screen.getAllByRole('button', { name: 'Edit' })[0]!)
    await user.type(screen.getByLabelText('Name'), ' XL')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    // The message is the backend's own, and the field stays open holding what was typed — closing
    // it would leave the old value on screen as though the change had been accepted.
    expect(await screen.findByRole('alert')).toHaveTextContent('A product name cannot be blank.')
    expect(screen.getByLabelText('Name')).toHaveValue('Espresso blend 1kg XL')
  })

  it('shows the backend’s reason when a deactivation is refused', async () => {
    /*
     * The defect: `deactivate.mutate` was given an `onSuccess` and nothing else, so a refusal
     * rendered nothing whatsoever and the button read as dead. The backend's message is complete
     * and actionable — it names the bundle and says what to do — and it was being thrown away on
     * this side. Real response, from the running stack, on the seeded data.
     */
    server.use(
      http.post('http://localhost/api/products/41/deactivate', () =>
        HttpResponse.json(
          {
            status: 422,
            title: 'Unprocessable Content',
            detail:
              "Product 'ESP-001' is a component of active bundle(s) TEST-PRODUCT-KIT-01, which " +
              'could not be assembled without it. Dissolve or re-define the bundle first, or ' +
              'deactivate it too.',
          },
          { status: 422, headers: { 'content-type': 'application/problem+json' } },
        ),
      ),
    )
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()

    try {
      renderDetail()
      await screen.findByRole('heading', { name: 'Espresso blend 1kg' })
      await user.click(screen.getByRole('button', { name: 'Deactivate' }))

      expect(await screen.findByRole('alert')).toHaveTextContent(
        /component of active bundle\(s\) TEST-PRODUCT-KIT-01/,
      )
      // And the product is still shown as active, because it is.
      expect(screen.queryByText('Inactive')).not.toBeInTheDocument()
    } finally {
      confirm.mockRestore()
    }
  })

  it('says something even when the refusal carries no detail', async () => {
    // A 403 carries no `detail` by design — step 14 made a permission refusal say nothing — and
    // reading `error.detail` directly rendered an empty alert, which is the same silence.
    server.use(
      http.post('http://localhost/api/products/41/deactivate', () =>
        new HttpResponse(null, { status: 403 }),
      ),
    )
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()

    try {
      renderDetail()
      await screen.findByRole('heading', { name: 'Espresso blend 1kg' })
      await user.click(screen.getByRole('button', { name: 'Deactivate' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('The change was refused.')
    } finally {
      confirm.mockRestore()
    }
  })

  it('shows the unit by name while editing it, not by id', async () => {
    // Base UI's `Select.Value` renders `String(value)` unless the root is given the options, so
    // this read "1" — the id — on a screen whose whole job is to be readable.
    const user = userEvent.setup()
    renderDetail()
    await screen.findByRole('heading', { name: 'Espresso blend 1kg' })

    // Name, EAN, Selling price, Unit — the fields in the order the card lays them out.
    await user.click(screen.getAllByRole('button', { name: 'Edit' })[3]!)

    expect(screen.getByLabelText('Unit')).toHaveTextContent('Kilogram')
    expect(screen.getByLabelText('Unit')).not.toHaveTextContent('1')
  })

  it('offers no editing at all to a view-only role', async () => {
    me = orderStaff
    renderDetail()
    await screen.findByRole('heading', { name: 'Espresso blend 1kg' })

    // Not disabled buttons that produce 403s — no affordance.
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
  })

  it('distinguishes a withheld value from an unset one', async () => {
    server.use(
      http.get('http://localhost/api/products/41', () =>
        HttpResponse.json({
          ...espresso,
          // As the backend sends it: the values are ABSENT, and hiddenFields says why.
          supplierId: undefined,
          supplierSku: undefined,
          lastPurchasePrice: undefined,
          ean: undefined,
          hiddenFields: [ProtectedField.PRODUCT_SUPPLIER, ProtectedField.PRODUCT_SUPPLIER_SKU],
        }),
      ),
    )
    renderDetail()
    await screen.findByRole('heading', { name: 'Espresso blend 1kg' })

    // The supplier was withheld and says so on hover; the EAN is simply not set, and does not.
    const withheld = await screen.findByTitle('Hidden from your role')
    expect(withheld).toBeInTheDocument()
  })

  it('shows stock by location with the enum label in the active language', async () => {
    renderDetail()
    expect(await screen.findByText('Inventory')).toBeInTheDocument()
    expect(screen.getByText('12.000000')).toBeInTheDocument()
  })

  it('renders the Greek labels when the language is Greek', async () => {
    await i18n.changeLanguage('el')
    try {
      renderDetail()
      // Greek renders 18,50 rather than 18.50 — the locale-aware separator, from the exact digits.
      expect(await screen.findByText(/18,50/)).toBeInTheDocument()
      expect(screen.getByText('Απόθεμα')).toBeInTheDocument()
    } finally {
      await i18n.changeLanguage('en')
    }
  })
})
