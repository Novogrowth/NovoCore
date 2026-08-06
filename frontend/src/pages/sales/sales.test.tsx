import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import {
  AccessLevel,
  Section,
  type ChargeTypeView,
  type CustomerView,
  type Me,
  type ProductView,
  type SalesDocumentSeriesView,
  type SalesInvoicePreview,
  type SalesInvoiceView,
} from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
// Imported for its side effect: without it every label renders as its raw key.
import '@/i18n'
import { OWNER_ROLE, aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { SalesInvoiceDetail } from './sales-invoice-detail'
import { SalesInvoiceRecord } from './sales-invoice-record'
import { SalesInvoicesList } from './sales-invoices-list'

/**
 * The sales invoice screens — **the first in this application over a SERVER-PAGED endpoint**.
 *
 * Most of what is asserted here is asserted nowhere else in the repository, because no earlier list
 * pages on the server: that a `page` block reaches the table, that only the three columns the
 * endpoint declares are sortable, and that the rest are plain text rather than dead buttons.
 *
 * ⚠️ **What a test over `msw` cannot tell you, and this file does not claim:** whether the backend
 * accepts what these screens build. The mock answers whatever it is given. That is F.1's
 * `F5WriteContractIT`, against a real server — and the record form's own body shape is proven there,
 * not here.
 *
 * <h2>⚠️ The record form is tested as a TEST HARNESS, and that shapes what is asserted</h2>
 *
 * The form is transitional by the owner's decision of 2026-08-05 — a sales invoice will never be
 * recorded by hand in real operation, and the screen has no production caller once the Go adapter
 * exists (`CLAUDE.md` §1b, and the reason is written at `sales-invoice-record.tsx`). So the tests
 * below cover **what would be a defect in the recording PATH or in a permanent convention** — the
 * preview-then-accept sequence, the absence of a channel field, the standing no-write-on-render
 * assertion, refusals being rendered — and deliberately **not** ergonomics of a screen scheduled for
 * deletion.
 */

const owner: Me = aUser({ id: 1, role: OWNER_ROLE, sections: everySectionAt(AccessLevel.FULL) })

/** A read-only role, to prove the record action is absent rather than present and doomed. */
const viewer: Me = aUser({
  id: 2,
  role: { id: 4, name: 'SALES_VIEWER', fullAccess: false, systemRole: false },
  sections: [{ section: Section.SALES, level: AccessLevel.VIEW, available: true }],
})

const line = {
  id: 900,
  lineNumber: 0,
  lineType: 'PRODUCT' as const,
  productId: 41,
  productSku: 'ESP-001',
  quantity: '2.000000',
  unitPrice: { amount: '18.500000', currency: 'EUR' },
  netAmount: { amount: '37.00', currency: 'EUR' },
  vatAmount: { amount: '8.88', currency: 'EUR' },
  vatClassId: 3,
  soldSerialNumbers: [],
  components: [],
  bundle: false,
  exempt: false,
}

const invoice: SalesInvoiceView = {
  id: 500,
  customerId: 7,
  customerName: 'Καφεκοπτεία Σινιόρ',
  channel: 'ECOMMERCE',
  settlementMethod: 'ON_ACCOUNT',
  documentNumber: 'ΑΛΠ-1042',
  invoiceDate: '2026-07-20',
  netTotal: { amount: '37.00', currency: 'EUR' },
  vatTotal: { amount: '8.88', currency: 'EUR' },
  grossTotal: { amount: '45.88', currency: 'EUR' },
  roundingAmount: { amount: '0.00', currency: 'EUR' },
  roundingNeededReview: false,
  journalEntryId: 88,
  // ⚠️ Absent, and that is the permanent correct state in this phase rather than missing data:
  // Novocore never obtains a ΜΑΡΚ, and no route on the surface writes one.
  transmissionStatus: 'UNKNOWN',
  seriesId: 3,
  seriesAbbreviation: 'ΑΛΠW',
  lines: [line],
  inForce: true,
  reversal: false,
  reversed: false,
}

/** A second row with no series — every invoice recorded before R1b looks like this. */
const legacy: SalesInvoiceView = {
  ...invoice,
  id: 501,
  documentNumber: 'TEST-SI-2026-0001',
  seriesAbbreviation: undefined,
  seriesId: undefined,
}

let me: Me = owner

// ---------------------------------------------------------------------------------------------
// Reference data the record form's four pickers are built from.
// ---------------------------------------------------------------------------------------------

/** The recordable series. `channel` is present, so recording against it is not the R3 refusal. */
const webSeries: SalesDocumentSeriesView = {
  id: 3,
  abbreviation: 'ΑΛΠW',
  description: 'Retail receipts, web',
  documentTypeId: 1,
  documentTypeDescription: 'Απόδειξη Λιανικής',
  channel: 'ECOMMERCE',
  getsMark: true,
  sortCode: 1000,
  inUse: true,
  active: true,
}

const customer: CustomerView = {
  id: 7,
  name: 'Καφεκοπτεία Σινιόρ',
  vatStatus: 'NORMAL',
  active: true,
  mergeable: true,
  systemRecord: false,
}

const product: ProductView = {
  id: 41,
  sku: 'ESP-001',
  name: 'Espresso blend 1kg',
  type: 'GOODS',
  unitOfMeasure: { id: 1, code: 'kg', name: 'Kilogram', fractionalQuantityAllowed: true, active: true },
  defaultVatClassId: 3,
  sellingPrice: { amount: '18.50', currency: 'EUR' },
  serialTracked: false,
  bundle: false,
  active: true,
  hiddenFields: [],
  redacted: false,
  stocked: true,
}

const chargeType: ChargeTypeView = {
  id: 12,
  name: 'Delivery',
  defaultVatClassId: 3,
  incomeAccountId: 7000,
  active: true,
}

/** A preview whose rounding difference is inside the threshold: nothing to accept. */
const settledPreview: SalesInvoicePreview = {
  lines: [],
  net: { amount: '37.00', currency: 'EUR' },
  vat: { amount: '8.88', currency: 'EUR' },
  gross: { amount: '45.88', currency: 'EUR' },
  roundingDifference: { amount: '0.00', currency: 'EUR' },
  roundingThreshold: { amount: '0.03', currency: 'EUR' },
  roundingNeedsAcceptance: false,
  receivable: { amount: '45.88', currency: 'EUR' },
}

/**
 * The same preview past the threshold.
 *
 * ⚠️ `roundingNeedsAcceptance` is the server's own verdict, not a comparison this screen makes. It
 * comes out of the same `compute(...)` the record path runs, which is the whole reason the preview
 * is structural rather than a courtesy.
 */
const needsAcceptancePreview: SalesInvoicePreview = {
  ...settledPreview,
  statedTotal: { amount: '46.00', currency: 'EUR' },
  roundingDifference: { amount: '0.12', currency: 'EUR' },
  roundingNeedsAcceptance: true,
  receivable: { amount: '46.00', currency: 'EUR' },
}

/**
 * Every URL the list asked for, query string included.
 *
 * ⚠️ `trackRequests` records `new URL(request.url).pathname` and therefore **drops the query**, so it
 * structurally cannot answer the two questions this screen most needs asked: whether a date range is
 * sent, and whether sorting goes to the server. It stays for what it is for — proving no writes —
 * and the query is captured here instead.
 */
let listUrls: string[] = []

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/sales-invoices', ({ request }) => {
    listUrls.push(request.url)
    return HttpResponse.json({
      items: [invoice, legacy],
      // The block that makes `DataTable` switch to server paging. No earlier screen sends one.
      page: { page: 0, size: 25, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false },
    })
  }),
  http.get('http://localhost/api/sales-invoices/500', () => HttpResponse.json(invoice)),
  http.get('http://localhost/api/sales-document-series', () =>
    HttpResponse.json({ items: [webSeries] }),
  ),
  http.get('http://localhost/api/customers', () => HttpResponse.json({ items: [customer] })),
  http.get('http://localhost/api/products', () => HttpResponse.json({ items: [product] })),
  http.get('http://localhost/api/charge-types', () => HttpResponse.json({ items: [chargeType] })),
)

const requests = trackRequests(server)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  me = owner
  listUrls = []
  requests.reset()
})
afterAll(() => server.close())

function renderList() {
  return render(
    <AppQueryProvider>
      <MemoryRouter>
        <SalesInvoicesList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/sales/invoices/500']}>
        <Routes>
          <Route path="/sales/invoices/:id" element={<SalesInvoiceDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderRecord() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/sales/invoices/new']}>
        <Routes>
          <Route path="/sales/invoices/new" element={<SalesInvoiceRecord />} />
          <Route path="/sales/invoices/:id" element={<p>recorded</p>} />
        </Routes>
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

/**
 * Fills the record form to the point where it will talk to the server.
 *
 * ⚠️ The quantity is left alone: it starts at `1`, and a line with no quantity is one of the four
 * things `complete` checks for. Leaving the default in place is therefore part of what is under
 * test, not a shortcut.
 */
async function fillRecordForm(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('heading', { name: 'Record invoice' })

  await chooseFirstOption(user, 'Series')
  await chooseFirstOption(user, 'Customer')
  await user.type(screen.getByLabelText('Document number'), 'ΑΛΠ-1043')
  await chooseFirstOption(user, 'Product')
  await user.type(screen.getByLabelText('Unit price'), '18.50')
}

describe('the sales invoice list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByText('ΑΛΠ-1042')
    requests.expectNoWrites()
  })

  it('asks for a date range, because the endpoint refuses a call without one', async () => {
    renderList()
    await screen.findByText('ΑΛΠ-1042')

    // The screen cannot open on "everything": GET /api/sales-invoices answers 400 to a call with
    // neither a range nor a customerId. So the default is a precondition, not a convenience.
    expect(listUrls, 'the list must have asked for something').not.toHaveLength(0)
    expect(listUrls[0]).toContain('from=')
    expect(listUrls[0]).toContain('to=')
    // 1 January of the current year — the decided default.
    expect(listUrls[0]).toContain(`from=${new Date().getFullYear()}-01-01`)
  })

  it('sorts through the request, on the three keys the endpoint declares', async () => {
    const user = userEvent.setup()
    renderList()
    await screen.findByText('ΑΛΠ-1042')

    await user.click(screen.getByRole('button', { name: /Number/i }))

    // ⚠️ The assertion that matters is `sort=` reaching the SERVER. A server-paged list must never
    // sort in the browser: ordering the rows in hand and presenting them as the order of the whole
    // table is convincing and wrong.
    await waitFor(() => {
      expect(listUrls.some((url) => url.includes('sort=DOCUMENT_NUMBER'))).toBe(true)
    })
  })

  it('offers no sort on a column the endpoint cannot order by', async () => {
    renderList()
    await screen.findByText('ΑΛΠ-1042')

    // Customer is deliberately not a sort key — the endpoint filters by customerId instead, and a
    // CUSTOMER_NAME sort would drag the collation obligation in with it. It must render as plain
    // text, NOT as a disabled button: a control that is present and inert invites clicking.
    expect(screen.queryByRole('button', { name: /Customer/i })).toBeNull()
    expect(screen.getByText('Customer')).toBeInTheDocument()
  })

  it('shows an invoice with no series as unset rather than dropping it', async () => {
    renderList()
    // Every invoice recorded before R1b has a null series. It has to appear in its own list.
    expect(await screen.findByText('TEST-SI-2026-0001')).toBeInTheDocument()
  })

  it('hides the record action from a role that may only view', async () => {
    me = viewer
    renderList()
    await screen.findByText('ΑΛΠ-1042')
    expect(screen.queryByRole('link', { name: /Record invoice/i })).toBeNull()
  })
})

describe('the sales invoice detail', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: /ΑΛΠ-1042/ })
    requests.expectNoWrites()
  })

  /**
   * ⚠️ The absence test this screen most needs.
   *
   * A posted document is immutable (ADR 0006) and the backend has no route to change one — measured
   * at the wire, `PATCH` answers 404 and `DELETE` answers 405. So there must be **no Edit control at
   * all**, for a FULL-access role. Not a disabled one: `editable: false` means *"your role may not"*,
   * which would tell an owner something false.
   */
  it('offers no Edit control anywhere, for a FULL-access role', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: /ΑΛΠ-1042/ })
    expect(screen.queryAllByRole('button', { name: /^Edit /i })).toHaveLength(0)
  })

  it('says why the statutory identifiers are empty, rather than showing bare dashes', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: /ΑΛΠ-1042/ })
    expect(screen.getByText(/Novocore never obtains a ΜΑΡΚ/i)).toBeInTheDocument()
  })

  it('labels the channel as coming from the series', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: /ΑΛΠ-1042/ })
    // One fact, not two. A reader who saw them as independent would ask why they cannot be edited
    // separately, and the answer is that the channel IS the series'.
    expect(screen.getByText(/Comes from the series/i)).toBeInTheDocument()
  })

  it('will not send a reversal without a reason', async () => {
    const user = userEvent.setup()
    renderDetail()
    await screen.findByRole('heading', { name: /ΑΛΠ-1042/ })

    await user.click(screen.getByRole('button', { name: 'Reverse' }))

    // `ReversalCommand.reason` is guarded by Required.text, and the record argues why: a reversal
    // that says nothing about why leaves the ledger internally consistent and unexplainable.
    expect(screen.getByRole('button', { name: /Reverse this invoice/i })).toBeDisabled()
    requests.expectNoWrites()
  })
})

describe('the sales invoice record form', () => {
  it('sends no write merely by rendering', async () => {
    renderRecord()
    await screen.findByRole('heading', { name: 'Record invoice' })
    // ⚠️ Worth more here than on a read screen: this form fires TWO mutations, and `preview` is a
    // POST that reads. A hook wired as a query would send it on mount and on every window focus.
    requests.expectNoWrites()
  })

  it('will not talk to the server until a series is chosen', async () => {
    const user = userEvent.setup()
    renderRecord()
    await screen.findByRole('heading', { name: 'Record invoice' })

    await chooseFirstOption(user, 'Customer')
    await user.type(screen.getByLabelText('Document number'), 'ΑΛΠ-1043')
    await chooseFirstOption(user, 'Product')
    await user.type(screen.getByLabelText('Unit price'), '18.50')

    /*
     * The series is mandatory on `NewSalesInvoice` and carries the channel and the document type
     * with it — R1b made it the one reference that decides what kind of document this is. A form
     * that could submit without one would be asking for a refusal it can see coming.
     */
    expect(screen.getByRole('button', { name: 'Record invoice' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Preview' })).toBeDisabled()
    requests.expectNoWrites()
  })

  it('⚠️ has no channel field and no document-type field, because the series supplies both', async () => {
    renderRecord()
    await screen.findByRole('heading', { name: 'Record invoice' })

    /*
     * An ABSENCE test, and the reason it is worth one: `NewSalesInvoice` has no `channel` and no
     * `documentTypeId` component at all, so a field for either could not be sent and two settable
     * references could disagree about what kind of document a row is. Nothing on this screen says
     * so on its own — "there is no control" and "the control silently does nothing" look identical
     * from the outside, which is why the absence is asserted rather than assumed.
     */
    expect(screen.queryByLabelText('Sales channel')).toBeNull()
    expect(screen.queryByLabelText(/document type/i)).toBeNull()
    expect(screen.getByLabelText('Series')).toBeInTheDocument()
  })

  it('offers a settlement method the server may well refuse, rather than hiding it', async () => {
    renderRecord()
    await screen.findByRole('heading', { name: 'Record invoice' })

    /*
     * R2b's `active` guard refuses recording against a deactivated payment method with a sentence
     * naming it. This form deliberately offers every method: an option quietly missing from a list
     * is a worse answer than a refusal that explains itself, and it is the R2b finding in reverse —
     * a screen's filter standing in for a rule. The picker must therefore not be empty.
     */
    expect(screen.getByLabelText('Settlement method')).toHaveTextContent('On account')
  })

  /**
   * ⚠️ The sequence this file exists for, and the one the handover singled out.
   *
   * Whether a rounding difference needs accepting is **not derivable from anything this form
   * knows** — it is a comparison between figures the server computes from the lines and a threshold
   * held in Settings. So the acceptance control must appear only when the preview says so, and the
   * Record button must stay disabled until a name is typed, because the server refuses the record
   * otherwise.
   */
  it('shows the acceptance control only when the preview asks, and blocks Record until it is filled', async () => {
    server.use(
      http.post('http://localhost/api/sales-invoices/preview', () =>
        HttpResponse.json(needsAcceptancePreview),
      ),
    )

    const user = userEvent.setup()
    renderRecord()
    await fillRecordForm(user)

    // Before the preview: no acceptance fields, because nothing has said one is needed.
    expect(screen.queryByLabelText('Accepted by')).toBeNull()

    await user.click(screen.getByRole('button', { name: 'Preview' }))

    expect(await screen.findByLabelText('Accepted by')).toBeInTheDocument()
    expect(screen.getByText(/larger than the rounding threshold/i)).toBeInTheDocument()
    // Both figures, because "there is a difference" without the threshold is not actionable.
    expect(screen.getByText('€0.12')).toBeInTheDocument()
    expect(screen.getByText('€0.03')).toBeInTheDocument()

    // The form is otherwise complete, so this disablement is the acceptance and nothing else.
    expect(screen.getByRole('button', { name: 'Record invoice' })).toBeDisabled()

    await user.type(screen.getByLabelText('Accepted by'), 'Kostas')
    expect(screen.getByRole('button', { name: 'Record invoice' })).toBeEnabled()
  })

  it('offers no acceptance control when the difference is inside the threshold', async () => {
    server.use(
      http.post('http://localhost/api/sales-invoices/preview', () =>
        HttpResponse.json(settledPreview),
      ),
    )

    const user = userEvent.setup()
    renderRecord()
    await fillRecordForm(user)
    await user.click(screen.getByRole('button', { name: 'Preview' }))

    // Gross and receivable, which are the same figure when nothing was rounded — and that they are
    // the same is the point of showing both.
    expect(await screen.findAllByText('€45.88')).toHaveLength(2)
    /*
     * ⚠️ Measured on the real server, not assumed: below the threshold `roundingAcceptedBy` is
     * SILENTLY DROPPED. A permanently visible field would collect a name that goes nowhere.
     */
    expect(screen.queryByLabelText('Accepted by')).toBeNull()
    expect(screen.getByRole('button', { name: 'Record invoice' })).toBeEnabled()
  })

  it('sends what the preview was asked about, and navigates to the recorded invoice', async () => {
    let previewBody: Record<string, unknown> | undefined
    let recordBody: Record<string, unknown> | undefined
    server.use(
      http.post('http://localhost/api/sales-invoices/preview', async ({ request }) => {
        previewBody = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(settledPreview)
      }),
      http.post('http://localhost/api/sales-invoices', async ({ request }) => {
        recordBody = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...invoice, id: 777 }, { status: 201 })
      }),
    )

    const user = userEvent.setup()
    renderRecord()
    await fillRecordForm(user)

    await user.click(screen.getByRole('button', { name: 'Preview' }))
    await waitFor(() => expect(previewBody).toBeDefined())

    await user.click(screen.getByRole('button', { name: 'Record invoice' }))
    await waitFor(() => expect(recordBody).toBeDefined())

    /*
     * ⭐ The two bodies are built by one function, deliberately, so a preview cannot answer about a
     * document different from the one submitted. Asserting they are equal is the only thing on this
     * side that can notice if that stops being true.
     */
    expect(recordBody).toEqual(previewBody)

    // The series reaches the wire, and the two fields that must not exist do not.
    expect(recordBody!.seriesId).toBe(3)
    expect(Object.hasOwn(recordBody!, 'channel')).toBe(false)
    expect(Object.hasOwn(recordBody!, 'documentTypeId')).toBe(false)
    // ⚠️ `documentNumber` is mandatory in fact AND now declared (A.2), so `tsc` refuses a form that
    // omits it. This asserts the value actually travels rather than the type permitting it.
    expect(recordBody!.documentNumber).toBe('ΑΛΠ-1043')

    expect(await screen.findByText('recorded')).toBeInTheDocument()
  })

  it('renders the server’s refusal instead of failing silently', async () => {
    server.use(
      http.post('http://localhost/api/sales-invoices', () =>
        HttpResponse.json(
          {
            status: 422,
            detail:
              'Series ΑΛΠW has no sales channel, so the revenue leg has no candidate account.',
          },
          { status: 422 },
        ),
      ),
    )

    const user = userEvent.setup()
    renderRecord()
    await fillRecordForm(user)

    await user.click(screen.getByRole('button', { name: 'Record invoice' }))

    /*
     * Every refusal on this path is a 422 with a written sentence — the channel-less series, the
     * inactive series, the deactivated payment method, the €500 cash limit. Deactivating a product
     * once shipped with an `onSuccess` and nothing else, so a 422 explaining exactly what was wrong
     * produced no visible change and the button read as dead. One `<Refusal>` covers all of them.
     */
    expect(await screen.findByText(/has no sales channel/i)).toBeInTheDocument()
  })
})
