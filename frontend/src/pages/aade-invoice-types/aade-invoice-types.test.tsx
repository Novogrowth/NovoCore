import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import {
  AadeInvoiceGroup,
  AccessLevel,
  Section,
  type AadeInvoiceTypeView,
  type Me,
} from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { AadeInvoiceTypeDetail, AadeInvoiceTypesList } from './aade-invoice-types'

/**
 * The AADE myDATA invoice-type codification.
 *
 * ⚠️ **The load-bearing test in this file is an ABSENCE test**, and it is the first instance of a
 * convention: a `StatutoryCodification` screen has **no create control, permanently**, and says so.
 *
 * `frontend/README.md` already names an absence test for the *fourth* field state — "not built
 * yet", a deferral that somebody will one day come back and build. This is a different thing and
 * must not be confused with it: **nobody may ever add a row here, on any installation.** AADE
 * authors them, Flyway writes them, and `StatutoryCodificationRulesTest` makes a create path a
 * build failure on the backend. The next seed-only screen — `/api/vat-exemption-reasons` has three
 * write routes and no screen at all — copies these three things: no Add button, a permanent
 * explanatory line, and this test.
 */

const owner: Me = aUser({
  id: 1,
  role: { id: 1, name: 'OWNER', fullAccess: true, systemRole: true },
  sections: everySectionAt(AccessLevel.FULL),
})

const viewer: Me = aUser({
  id: 8,
  role: { id: 5, name: 'VIEWER', fullAccess: false, systemRole: false },
  sections: [{ section: Section.TAX_AND_CHARGES, level: AccessLevel.VIEW, available: true }],
})

const salesInvoice: AadeInvoiceTypeView = {
  id: 1,
  code: '1.1',
  description: 'Τιμολόγιο Πώλησης',
  group: AadeInvoiceGroup.ISSUER_MATCHED,
  active: true,
}

/**
 * ⚠️ Code 4 is one of the two whose annex 8.1 description cell is EMPTY. V31 seeded the group
 * heading in its place, so both 4 and 12 read `Για Μελλοντική Χρήση` — character for character.
 * That is why every picker option leads with the code.
 */
const futureUse: AadeInvoiceTypeView = {
  id: 13,
  code: '4',
  description: 'Για Μελλοντική Χρήση',
  group: AadeInvoiceGroup.ISSUER_MATCHED,
  active: true,
}

const otherFutureUse: AadeInvoiceTypeView = {
  id: 34,
  code: '12',
  description: 'Για Μελλοντική Χρήση',
  group: AadeInvoiceGroup.ISSUER_UNMATCHED,
  active: true,
}

const types = [salesInvoice, futureUse, otherFutureUse]

let me: Me = owner

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/aade-invoice-types', () => HttpResponse.json({ items: types })),
  http.get('http://localhost/api/aade-invoice-types/1', () => HttpResponse.json(salesInvoice)),
  http.get('http://localhost/api/aade-invoice-types/13', () => HttpResponse.json(futureUse)),
  http.patch('http://localhost/api/aade-invoice-types/1/description', () =>
    HttpResponse.json(salesInvoice),
  ),
  http.post('http://localhost/api/aade-invoice-types/1/deactivate', () =>
    new HttpResponse(null, { status: 204 }),
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
        <AadeInvoiceTypesList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail(id: number) {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={[`/settings/aade-invoice-types/${id}`]}>
        <Routes>
          <Route path="/settings/aade-invoice-types/:id" element={<AadeInvoiceTypeDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the AADE invoice type list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByRole('link', { name: '1.1' })
    requests.expectNoWrites()
  })

  it('offers NO create control, permanently — AADE authors these rows and Flyway writes them', async () => {
    renderList()
    await screen.findByRole('link', { name: '1.1' })

    /*
     * ⚠️ This is not "not built yet". It is "nobody may ever do this, on any installation", and
     * the two look identical in a diff — which is exactly why the assertion exists rather than
     * being left to the absence of a line of JSX. A code typed into a form is transmitted to the
     * tax authority, so a wrong one is a compliance defect rather than a data-entry mistake.
     *
     * Asserted for a FULL-access owner: a role that could add one anywhere would add one here.
     */
    expect(screen.queryByRole('link', { name: /New/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /New/ })).not.toBeInTheDocument()
  })

  it('says who authors the rows, so the missing button reads as a decision', async () => {
    renderList()
    // A list with no Add control and no explanation reads as unfinished work. This is the other
    // half of the convention, and without it the assertion above is only half true in practice.
    expect(await screen.findByText(/nobody can add one/)).toBeInTheDocument()
  })
})

describe('one AADE invoice type', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail(1)
    await screen.findByRole('heading', { name: '1.1' })
    requests.expectNoWrites()
  })

  it('offers the description and nothing else — the code and group have no route', async () => {
    renderDetail(1)
    await screen.findByRole('heading', { name: '1.1' })

    expect(screen.getByRole('button', { name: 'Edit Description' })).toBeInTheDocument()
    // `frontend/README.md`'s THIRD state: no route on any installation, so plain text with the
    // reason — never a disabled control, which invites a hunt for the permission that unlocks it.
    expect(screen.queryByRole('button', { name: 'Edit Code' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit Group' })).not.toBeInTheDocument()
    expect(screen.getByText(/AADE publishes it/)).toBeInTheDocument()
  })

  it('explains the empty-cell description rather than leaving it to be "fixed"', async () => {
    renderDetail(13)
    await screen.findByRole('heading', { name: '4' })
    // Annex 8.1 leaves this cell empty and V31 seeded the group heading. A reader who does not
    // know that will treat it as a placeholder somebody forgot.
    expect(screen.getByText(/description cell empty/)).toBeInTheDocument()
  })

  it('gives a VIEW role no edit affordance at all', async () => {
    me = viewer
    renderDetail(1)
    await screen.findByRole('heading', { name: '1.1' })
    expect(screen.queryByRole('button', { name: /^Edit / })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
  })
})
