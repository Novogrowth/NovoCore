import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import {
  AccessLevel,
  Section,
  type Me,
  type PaymentMethodView,
} from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { PaymentMethodDetail, PaymentMethodsList } from './payment-methods'

/**
 * Payment methods.
 *
 * <h2>⚠️ The seed-only convention's SECOND instance, and the absence test is the load-bearing one</h2>
 *
 * `AadeInvoiceTypesList` established it: **no Add control, a permanent line saying who authors the
 * rows, and an absence test naming the omission as permanent rather than "not yet".** This is the
 * screen that copies it, and it is the first evidence the convention is one rather than a
 * description of a single screen.
 *
 * <h2>⚠️ It exists because of a scoping error</h2>
 *
 * The owner's specification listed payment methods beside delivery methods. Establishing that
 * `SettlementMethod` is an enum was carried into R2's scope as "nothing to edit" — so one got a full
 * CRUD screen and the other got nothing. **No create was right; no screen was not.**
 *
 * <h2>⚠️ And the myDATA code is NOT a column</h2>
 *
 * It has been on the enum since the enum was written. The view resolves it, so there is nothing to
 * drift — `PaymentMethodIT` holds the table and the enum together in both directions.
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
  inUse: false,
  settlesImmediately: true,
  subjectToCashLimit: true,
  sortCode: 10,
  active: true,
}

/** ⚠️ One of the three whose AADE code is genuinely open and was deliberately not invented. */
const stripe: PaymentMethodView = {
  id: 2,
  aadePaymentMethodId: 1,
  aadePaymentMethodDescription: 'Επαγ. Λογαριασμός Πληρωμών Ημεδαπής',
  accountId: 11,
  aadePaymentMethodCode: 1,
  accountName: 'Stripe',
  inUse: false,
  abbreviation: 'STRP',
  description: 'Stripe',
  settlesImmediately: true,
  subjectToCashLimit: false,
  sortCode: 80,
  active: true,
}

let me: Me = owner

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/payment-methods', () =>
    HttpResponse.json({ items: [cash, stripe] }),
  ),
  http.get('http://localhost/api/payment-methods/CASH', () => HttpResponse.json(cash)),
  http.get('http://localhost/api/payment-methods/STRIPE', () => HttpResponse.json(stripe)),
  http.patch('http://localhost/api/payment-methods/CASH/description', () =>
    HttpResponse.json(cash),
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
        <PaymentMethodsList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail(method: string) {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={[`/settings/payment-methods/${method}`]}>
        <Routes>
          <Route path="/settings/payment-methods/:method" element={<PaymentMethodDetail />} />
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

  it('offers NO create control, permanently — adding one is a change to the software', async () => {
    renderList()
    await screen.findByRole('link', { name: 'ΜΕΤΡ' })

    /*
     * ⚠️ The convention's second instance, and the distinction it protects is the same one:
     * this is NOT `frontend/README.md`'s fourth field state ("not built yet", a deferral somebody
     * will come back to). Nobody may ever add a row here, on any installation — a new method needs
     * an AccountSystemKey, a settlesImmediately and a subjectToCashLimit, and no form can supply
     * those. Asserted for a FULL-access owner: a role that could add one anywhere would add one
     * here.
     */
    expect(screen.queryByRole('link', { name: /New/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /New/ })).not.toBeInTheDocument()
  })

  it('says why nobody can add one, so the missing button reads as a decision', async () => {
    renderList()
    expect(await screen.findByText(/nobody can add one/)).toBeInTheDocument()
  })

  it('⚠️ draws an absent myDATA code as OPEN, not as an unfilled field', async () => {
    renderList()
    await screen.findByRole('link', { name: 'STRP' })
    // AADE's code for Stripe, PayPal and ACS cash-on-delivery has not been established, and was
    // deliberately not invented. "Open" and "nobody filled it in" are different statements.
    expect(screen.getAllByText('Open').length).toBeGreaterThan(0)
  })
})

describe('one payment method', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail('CASH')
    await screen.findByRole('heading', { name: 'ΜΕΤΡ' })
    requests.expectNoWrites()
  })

  it('offers description and sort code, and nothing else', async () => {
    renderDetail('CASH')
    await screen.findByRole('heading', { name: 'ΜΕΤΡ' })

    expect(screen.getByRole('button', { name: 'Edit Description' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit Sort code' })).toBeInTheDocument()

    // ⚠️ The third field state — no route on any installation — so plain text with the reason,
    // never a disabled control. The myDATA code in particular is not even a column.
    expect(screen.queryByRole('button', { name: 'Edit myDATA code' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit Behaviour' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit Abbreviation' })).not.toBeInTheDocument()
    expect(screen.getByText(/is not stored separately/)).toBeInTheDocument()
  })

  it('says that deactivating does not touch documents already settled by it', async () => {
    renderDetail('CASH')
    await screen.findByRole('heading', { name: 'ΜΕΤΡ' })
    // Setting is refused; holding is not. Without saying so, nobody would dare deactivate one.
    expect(screen.getByText(/already settled by it are unaffected/)).toBeInTheDocument()
  })

  it('gives a VIEW role no edit affordance at all', async () => {
    me = viewer
    renderDetail('CASH')
    await screen.findByRole('heading', { name: 'ΜΕΤΡ' })
    expect(screen.queryByRole('button', { name: /^Edit / })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
  })
})
