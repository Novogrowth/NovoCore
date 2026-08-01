import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { AccessLevel, Section, type Me, type VatClassView } from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { VatClassCreate } from './vat-class-create'
import { VatClassDetail } from './vat-class-detail'
import { VatClassesList } from './vat-classes-list'

/**
 * VAT classes.
 *
 * **What these tests are mostly about is what the screen must NOT offer.** There is no route that
 * changes a rate or a code, and there never will be — editing a rate would retroactively change
 * what every invoice already issued under that class appears to have charged. So the assertions
 * below are absences, and they are the point of the step rather than a detail of it.
 */

const owner: Me = aUser({
  id: 1,
  role: { id: 1, name: 'OWNER', fullAccess: true, systemRole: true },
  sections: everySectionAt(AccessLevel.FULL),
})

/** Can read the section and change nothing in it. */
const viewer: Me = aUser({
  id: 8,
  role: { id: 5, name: 'VIEWER', fullAccess: false, systemRole: false },
  sections: [{ section: Section.TAX_AND_CHARGES, level: AccessLevel.VIEW, available: true }],
})

/** The seeded shape: the ΑΑΔΕ codes, and the island chain 24→17 already mapped. */
const standard: VatClassView = {
  id: 9,
  code: '1410',
  description: 'ΦΠΑ 24%',
  ratePercent: '24.000000',
  reducedCounterpartId: 8,
  active: true,
}

const island: VatClassView = {
  id: 8,
  code: '1170',
  description: 'ΦΠΑ 17%',
  ratePercent: '17.000000',
  active: true,
}

const classes = [island, standard]

let me: Me = owner

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/vat-classes', () => HttpResponse.json({ items: classes })),
  http.get('http://localhost/api/vat-classes/9', () => HttpResponse.json(standard)),
  http.get('http://localhost/api/vat-classes/8', () => HttpResponse.json(island)),
  http.patch('http://localhost/api/vat-classes/9/description', () =>
    HttpResponse.json({ ...standard, description: 'ΦΠΑ 24% (κανονικός)' }),
  ),
  http.post('http://localhost/api/vat-classes/9/deactivate', () => new HttpResponse(null, { status: 204 })),
  http.post('http://localhost/api/vat-classes', () => HttpResponse.json({ ...standard, id: 20 }, { status: 201 })),
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
        <VatClassesList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail(id: number) {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={[`/settings/vat-classes/${id}`]}>
        <Routes>
          <Route path="/settings/vat-classes/:id" element={<VatClassDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderCreate() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/settings/vat-classes/new']}>
        <Routes>
          <Route path="/settings/vat-classes/new" element={<VatClassCreate />} />
          <Route path="/settings/vat-classes/:id" element={<p>created</p>} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the VAT class list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByRole('link', { name: '1410' })
    requests.expectNoWrites()
  })

  it('shows the rate trimmed, and the counterpart as a code rather than an id', async () => {
    renderList()
    await screen.findByRole('link', { name: '1410' })

    // `numeric(19,6)` means every rate arrives as `24.000000`; six zeroes on every row is noise.
    expect(screen.getByText('24%')).toBeInTheDocument()
    /*
     * ⚠️ The row carries `reducedCounterpartId: 8`. An id on screen means nothing to anybody; the
     * code is what an accountant recognises, and resolving it needs the unfiltered list.
     *
     * `1170` appears TWICE on this screen and both are correct: once as the 17% class's own code
     * link, once as the 24% class's counterpart badge. Asserting a single match would fail for a
     * reason that has nothing to do with what is being tested.
     */
    expect(screen.getAllByText('1170').length).toBe(2)
  })

  it('sends search= when the box is used', async () => {
    const user = userEvent.setup()
    renderList()
    await screen.findByRole('link', { name: '1410' })

    await user.type(screen.getByLabelText('Search'), 'φπα')
    await waitFor(
      () => {
        expect(
          requests.all().some((request) => request.path === '/api/vat-classes'),
        ).toBe(true)
      },
      { timeout: 2000 },
    )
  })
})

describe('one VAT class', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail(9)
    await screen.findByRole('heading', { name: '1410' })
    requests.expectNoWrites()
  })

  it('offers no way to edit the rate or the code — and no disabled control either', async () => {
    renderDetail(9)
    await screen.findByRole('heading', { name: '1410' })

    /*
     * ⚠️ The assertion the whole screen exists to satisfy.
     *
     * Not `queryByRole(..., { disabled: true })`: a disabled Edit button would be *worse* than a
     * missing one, because it tells an administrator holding TAX_AND_CHARGES:FULL that some
     * permission unlocks it. None does, on any installation. The value is shown as plain text with
     * the reason, which is the same call `RoleDetail` makes about a role's description.
     */
    expect(screen.queryByRole('button', { name: 'Edit Rate' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit Code' })).not.toBeInTheDocument()
    expect(screen.getByText('24%')).toBeInTheDocument()
    expect(screen.getByText(/A rate can never be changed/)).toBeInTheDocument()

    // The one editable field is there, so this test cannot pass on a page with no buttons at all.
    expect(screen.getByRole('button', { name: 'Edit Description' })).toBeInTheDocument()
  })

  it('does not offer to change the island counterpart mapping', async () => {
    renderDetail(9)
    await screen.findByRole('heading', { name: '1410' })

    /*
     * `PUT` and `DELETE …/reduced-counterpart` exist on the backend and are deliberately not wired
     * up in F4 — which rate an island order is charged at carries statutory weight and is its own
     * decision. The mapping itself is real and applicable: this business ships to reduced-VAT
     * islands. This assertion is what makes building it later a deliberate act with a test to
     * update, rather than something that drifts in with a copied screen.
     */
    expect(
      screen.queryByRole('button', { name: 'Edit Island reduced rate' }),
    ).not.toBeInTheDocument()
    expect(screen.getByText('1170')).toBeInTheDocument()
  })

  it('saves a description through PATCH …/description', async () => {
    const user = userEvent.setup()
    renderDetail(9)
    await screen.findByRole('heading', { name: '1410' })

    await user.click(screen.getByRole('button', { name: 'Edit Description' }))
    const input = screen.getByDisplayValue('ΦΠΑ 24%')
    await user.clear(input)
    await user.type(input, 'ΦΠΑ 24% (κανονικός)')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(requests.called('PATCH', '/api/vat-classes/9/description')).toBe(true)
    })
  })

  it('gives a VIEW role no edit affordance and no deactivate button', async () => {
    me = viewer
    renderDetail(9)
    await screen.findByRole('heading', { name: '1410' })

    expect(screen.queryByRole('button', { name: /^Edit / })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
  })
})

describe('adding a VAT class', () => {
  it('sends no write merely by rendering', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Add VAT class' })
    requests.expectNoWrites()
  })

  it('says on the form that this is also how a rate changes', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Add VAT class' })
    // Because there is no edit screen to discover it on.
    expect(screen.getByText(/A rate change is a new class plus a deactivation/)).toBeInTheDocument()
  })

  it('refuses to submit until all three fields are filled', async () => {
    const user = userEvent.setup()
    renderCreate()
    await screen.findByRole('button', { name: 'Add VAT class' })

    const submit = screen.getByRole('button', { name: 'Add VAT class' })
    expect(submit).toBeDisabled()

    // ⚠️ `NewVatClass` declares no `required` list — every component is reference-typed and the
    // spec generator only sees primitives — so `tsc` cannot catch an omission here. This can.
    await user.type(screen.getByLabelText('Code'), '1130')
    expect(submit).toBeDisabled()
    await user.type(screen.getByLabelText('Description'), 'ΦΠΑ 13%')
    expect(submit).toBeDisabled()
    await user.type(screen.getByLabelText('Rate'), '13')
    expect(submit).toBeEnabled()
  })
})
