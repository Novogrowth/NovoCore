import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'

import { AccessLevel, Section, type Me, type PaymentMethodView } from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { PaymentMethodCreate, PaymentMethodDetail, PaymentMethodsList } from './payment-methods'

/**
 * Payment methods — **a business list the owner authors.**
 *
 * <h2>⚠️ THREE TESTS WERE DELETED RATHER THAN ADAPTED, AND THAT IS THE POINT</h2>
 *
 * This file used to test the **seed-only convention's second instance**: *no Add control, a
 * permanent line saying who authors the rows, and an absence test naming the omission as permanent
 * rather than "not yet"*. Three tests carried that, and **all three are gone with their subject**:
 *
 * - *"offers NO create control, permanently"* — **deleted.** There is a create control now.
 *   Replaced by its exact opposite below: a FULL role sees it, a VIEW role does not.
 * - *"says why nobody can add one, so the missing button reads as a decision"* — **deleted**, and
 *   the banner it asserted with it. **Nothing replaces it:** a list with an Add button needs no
 *   explanation for a button it has.
 * - *"draws an absent myDATA code as OPEN, not as an unfilled field"* — **deleted, and nothing
 *   replaces it, deliberately.** Its subject was an enum constant whose AADE code had never been
 *   established. **The article is mandatory now**, so a method with no code cannot exist and there
 *   is no "open" state left to draw. ⚠️ The concern behind it survives one field along: the create
 *   form refuses to submit without an article, which is asserted below.
 *
 * ⚠️ **The rule being applied:** an assertion whose subject no longer exists is deleted, not bent
 * into something that still passes. Bending it is how coverage disappears while a suite stays green
 * — the same reasoning that removed `PaymentMethodIT`'s enum/seed drift test in the backend.
 */

const owner: Me = aUser({
  id: 1,
  role: { id: 1, name: 'OWNER', fullAccess: true, systemRole: true },
  sections: everySectionAt(AccessLevel.FULL),
})

const viewer: Me = aUser({
  id: 8,
  role: { id: 5, name: 'VIEWER', fullAccess: false, systemRole: false },
  sections: [{ section: Section.SALES, level: AccessLevel.VIEW, available: true }],
})

const cash: PaymentMethodView = {
  id: 1,
  abbreviation: 'ΜΕΤΡ',
  description: 'Μετρητά',
  aadePaymentMethodId: 3,
  aadePaymentMethodCode: 3,
  aadePaymentMethodDescription: 'Μετρητά',
  accountId: 10,
  accountName: 'Ταμείο',
  settlesImmediately: true,
  subjectToCashLimit: true,
  sortCode: 10,
  inUse: false,
  active: true,
}

/** ⚠️ In use — every field of this one is frozen except the sort code. */
const onCredit: PaymentMethodView = {
  id: 2,
  abbreviation: 'ΕΠΙΤ',
  description: 'Επί πιστώσει',
  aadePaymentMethodId: 5,
  aadePaymentMethodCode: 5,
  aadePaymentMethodDescription: 'Επί Πιστώσει',
  accountId: 20,
  accountName: 'Πελάτες',
  settlesImmediately: false,
  subjectToCashLimit: false,
  sortCode: 20,
  inUse: true,
  active: true,
}

const articles = [
  { id: 3, code: 3, description: 'Μετρητά', active: true },
  { id: 5, code: 5, description: 'Επί Πιστώσει', active: true },
]

const accounts = [
  { id: 10, name: 'Ταμείο', kind: 'BANK_CASH' },
  { id: 20, name: 'Πελάτες', kind: 'CONTROL' },
]

let me: Me = owner
/** ⚠️ Written by the POST handler, so a create test observes a CREATE rather than a fixture. */
let rows: PaymentMethodView[] = []
let lastCreateBody: Record<string, unknown> | undefined

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/payment-methods', () => HttpResponse.json({ items: rows })),
  http.get('http://localhost/api/payment-methods/1', () => HttpResponse.json(cash)),
  http.get('http://localhost/api/payment-methods/2', () => HttpResponse.json(onCredit)),
  http.get('http://localhost/api/aade-payment-methods', () =>
    HttpResponse.json({ items: articles }),
  ),
  http.get('http://localhost/api/accounts/payment-method-targets', () =>
    HttpResponse.json({ items: accounts }),
  ),
  /*
   * ⚠️ STATEFUL, and `frontend/README.md` says why: a static handler shows pre-edit data after a
   * save, and a create-form test over one proves the form RENDERED rather than that anything was
   * created. It also lets a test read back the body the form actually sent, which is the only way
   * to assert that a field was OMITTED.
   */
  http.post('http://localhost/api/payment-methods', async ({ request }) => {
    const body = (await request.json()) as Record<string, unknown>
    lastCreateBody = body
    const created: PaymentMethodView = {
      ...cash,
      id: 99,
      abbreviation: String(body.abbreviation),
      description: String(body.description),
      sortCode: typeof body.sortCode === 'number' ? body.sortCode : 30,
    }
    rows = [...rows, created]
    return HttpResponse.json(created, { status: 201 })
  }),
)

const requests = trackRequests(server)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => {
  me = owner
  rows = [cash, onCredit]
  lastCreateBody = undefined
})
afterEach(() => {
  server.resetHandlers()
  requests.reset()
})
afterAll(() => server.close())

function renderList() {
  return render(
    <AppQueryProvider>
      <MemoryRouter>
        <PaymentMethodsList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderCreate() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/settings/payment-methods/new']}>
        <Routes>
          <Route path="/settings/payment-methods/new" element={<PaymentMethodCreate />} />
          <Route path="/settings/payment-methods/:id" element={<PaymentMethodDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail(id: number) {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={[`/settings/payment-methods/${id}`]}>
        <Routes>
          <Route path="/settings/payment-methods/:id" element={<PaymentMethodDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the payment method list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByRole('link', { name: 'ΜΕΤΡ' })
    requests.expectNoWrites()
  })

  it('⚠️ renders correctly when EMPTY, which is now the normal first state', async () => {
    // V37 ships payment_method with no rows and nothing ever seeds it, so this is what the owner
    // meets on his first visit — not an edge case, and not a screen that reads as broken.
    rows = []
    renderList()
    expect(await screen.findByText(/No payment methods yet/)).toBeInTheDocument()
  })

  it('offers an Add control to a FULL role — the seed-only convention no longer applies here', async () => {
    renderList()
    await screen.findByRole('link', { name: 'ΜΕΤΡ' })
    // Queried by its href rather than by role: Base UI's Button renders the Link, so the role it
    // reports is an implementation detail of the component and not what this test is about.
    expect(screen.getByText('Add payment method').closest('a')).toHaveAttribute(
      'href',
      '/settings/payment-methods/new',
    )
  })

  it('offers a VIEW role no Add control', async () => {
    me = viewer
    renderList()
    await screen.findByRole('link', { name: 'ΜΕΤΡ' })
    expect(screen.queryByText('Add payment method')).not.toBeInTheDocument()
  })
})

describe('creating one', () => {
  it('sends no write merely by rendering', async () => {
    renderCreate()
    await screen.findByLabelText('Abbreviation')
    requests.expectNoWrites()
  })

  it('⚠️ will not submit without BOTH the AADE article and the account', async () => {
    const user = userEvent.setup()
    renderCreate()
    await screen.findByLabelText('Abbreviation')

    await user.type(screen.getByLabelText('Abbreviation'), 'ΜΕΤΡ')
    await user.type(screen.getByLabelText('Description'), 'Μετρητά')

    // Both pickers still unset. The server refuses this too — Required.field names the field — but
    // a form that sends a request it already knows will fail teaches the operator nothing.
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    requests.expectNoWrites()
  })

  it('⚠️ OMITS the sort code when blank — never 0, never an empty string', async () => {
    const user = userEvent.setup()
    renderCreate()
    await screen.findByLabelText('Abbreviation')

    await user.type(screen.getByLabelText('Abbreviation'), 'ΚΑΡΤ')
    await user.type(screen.getByLabelText('Description'), 'Κάρτα')
    await user.click(screen.getByLabelText('AADE payment article'))
    await user.click(await screen.findByRole('option', { name: /Μετρητά/ }))
    await user.click(screen.getByLabelText('Reconciles to'))
    await user.click(await screen.findByRole('option', { name: 'Ταμείο' }))

    await user.click(screen.getByRole('button', { name: 'Save' }))

    /*
     * ⚠️ The assertion is on ABSENCE, and it is the whole point of the field being optional. The
     * service is the one allocator and resolves absence to max + 10; sending 0 would not be
     * "absent", it would be a sort code of zero, and the next method created would collide with it
     * against a UNIQUE column.
     */
    await waitFor(() => expect(lastCreateBody).toBeDefined())
    expect(lastCreateBody).not.toHaveProperty('sortCode')
    expect(lastCreateBody).toMatchObject({
      abbreviation: 'ΚΑΡΤ',
      aadePaymentMethodId: 3,
      accountId: 10,
    })
  })
})

describe('one payment method', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail(1)
    await screen.findByRole('heading', { name: 'ΜΕΤΡ' })
    requests.expectNoWrites()
  })

  it('offers every field for editing while the method is UNUSED', async () => {
    renderDetail(1)
    await screen.findByRole('heading', { name: 'ΜΕΤΡ' })

    for (const field of [
      'Abbreviation',
      'Description',
      'AADE payment article',
      'Reconciles to',
      'Sort code',
    ]) {
      expect(screen.getByRole('button', { name: `Edit ${field}` })).toBeEnabled()
    }

    // ⚠️ Behaviour is DERIVED from the account and the article, so it is the third field state —
    // plain text with the reason, never a control.
    expect(screen.queryByRole('button', { name: 'Edit Behaviour' })).not.toBeInTheDocument()
    expect(screen.getByText(/Derived from the account and the AADE article/)).toBeInTheDocument()
  })

  it('⚠️ freezes every field once the method is USED — shown and disabled, with the reason', async () => {
    renderDetail(2)
    await screen.findByRole('heading', { name: 'ΕΠΙΤ' })

    /*
     * `lockedReason`, not a hidden control: "editable in general, fixed on THIS record". A hidden
     * control on the one row where a setting is fixed leaves an operator hunting for something
     * every other row has.
     */
    for (const field of ['Abbreviation', 'Description', 'AADE payment article', 'Reconciles to']) {
      expect(screen.getByRole('button', { name: `Edit ${field}` })).toBeDisabled()
    }
    expect(
      screen.getAllByText(/Documents have already been settled by this method/).length,
    ).toBeGreaterThan(0)
  })

  it('⚠️ leaves the SORT CODE editable even when the method is used', async () => {
    renderDetail(2)
    await screen.findByRole('heading', { name: 'ΕΠΙΤ' })
    // Deliberately exempt from the freeze: reordering is normal and a sort code appears on no
    // document. R2c's lesson — a value settable only once is unusable for its only purpose.
    expect(screen.getByRole('button', { name: 'Edit Sort code' })).toBeEnabled()
  })

  it('says that deactivating does not touch documents already settled by it', async () => {
    renderDetail(1)
    await screen.findByRole('heading', { name: 'ΜΕΤΡ' })
    // Setting is refused; holding is not. Without saying so, nobody would dare deactivate one.
    expect(screen.getByText(/already settled by it are unaffected/)).toBeInTheDocument()
  })

  it('gives a VIEW role no edit affordance at all', async () => {
    me = viewer
    renderDetail(1)
    await screen.findByRole('heading', { name: 'ΜΕΤΡ' })
    expect(screen.queryByRole('button', { name: /^Edit / })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
  })
})
