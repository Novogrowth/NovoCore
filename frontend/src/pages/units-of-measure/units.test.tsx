import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { AccessLevel, Section, type Me, type UnitOfMeasureView } from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { UnitCreate } from './unit-create'
import { UnitDetail } from './unit-detail'
import { UnitsList } from './units-list'

/**
 * Units of measure.
 *
 * Two things these hold to that are easy to get wrong in a way nothing else would catch:
 *
 * - **The create form always sends `fractionalQuantityAllowed`, and makes it a required choice.**
 *   Omitting the primitive is a `400` naming no field; sending an unticked checkbox's `false` is
 *   *accepted*, and is a decision nobody made. Only the second needs a design answer, and it is the
 *   one this screen gives. `F4WriteContractIT` proves both against the real server.
 * - **A myDATA code is settable once and then frozen**, which is the `lockedReason` case: shown,
 *   disabled, with the reason. Hidden would leave somebody hunting for a field every unmapped unit
 *   visibly has.
 */

const owner: Me = aUser({
  id: 1,
  role: { id: 1, name: 'OWNER', fullAccess: true, systemRole: true },
  sections: everySectionAt(AccessLevel.FULL),
})

const viewer: Me = aUser({
  id: 8,
  role: { id: 5, name: 'VIEWER', fullAccess: false, systemRole: false },
  sections: [{ section: Section.PRODUCTS, level: AccessLevel.VIEW, available: true }],
})

/** As seeded: no myDATA code, because the verified ΑΑΔΕ list has not been supplied. */
const kilogram: UnitOfMeasureView = {
  id: 4,
  code: 'KILOGRAM',
  name: 'Kilogram',
  fractionalQuantityAllowed: true,
  active: true,
}

/** One that has been mapped, so the frozen branch has something to render. */
const piece: UnitOfMeasureView = {
  id: 1,
  code: 'PIECE',
  name: 'Piece',
  fractionalQuantityAllowed: false,
  mydataCode: '1',
  active: true,
}

const units = [kilogram, piece]

let me: Me = owner

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/units-of-measure', () => HttpResponse.json({ items: units })),
  http.get('http://localhost/api/units-of-measure/without-mydata-code', () =>
    HttpResponse.json({ items: [kilogram] }),
  ),
  http.post('http://localhost/api/units-of-measure', () =>
    HttpResponse.json({ ...kilogram, id: 20 }, { status: 201 }),
  ),
  http.patch('http://localhost/api/units-of-measure/4/name', () => HttpResponse.json(kilogram)),
  http.patch('http://localhost/api/units-of-measure/4/mydata-code', () =>
    HttpResponse.json({ ...kilogram, mydataCode: '99' }),
  ),
  http.patch('http://localhost/api/units-of-measure/4/fractional-quantity', () =>
    HttpResponse.json(kilogram),
  ),
  http.post('http://localhost/api/units-of-measure/4/deactivate', () => new HttpResponse(null, { status: 204 })),
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
        <UnitsList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail(id: number) {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={[`/settings/units-of-measure/${id}`]}>
        <Routes>
          <Route path="/settings/units-of-measure/:id" element={<UnitDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderCreate() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/settings/units-of-measure/new']}>
        <Routes>
          <Route path="/settings/units-of-measure/new" element={<UnitCreate />} />
          <Route path="/settings/units-of-measure/:id" element={<p>created</p>} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the units list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByRole('link', { name: 'KILOGRAM' })
    requests.expectNoWrites()
  })

  it('surfaces the unmapped units as a standing to-do', async () => {
    renderList()
    // ⚠️ A real outstanding item, not a diagnostic: phase 7 cannot transmit a line whose unit has
    // no code, and until step 16b this list was answerable only from psql.
    expect(await screen.findByText(/have no myDATA code/)).toBeInTheDocument()
  })

  it('draws an absent myDATA code as unmapped rather than as an empty optional field', async () => {
    renderList()
    await screen.findByRole('link', { name: 'KILOGRAM' })
    // "no mapping exists" and "nobody filled it in" are different facts, and only one is true here.
    expect(screen.getAllByText('Not mapped').length).toBeGreaterThan(0)
  })
})

describe('one unit', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail(4)
    await screen.findByRole('heading', { name: 'KILOGRAM' })
    requests.expectNoWrites()
  })

  it('offers no way to edit the code', async () => {
    renderDetail(4)
    await screen.findByRole('heading', { name: 'KILOGRAM' })
    // Products refer to the unit by its code. No route changes one, so no affordance at all.
    expect(screen.queryByRole('button', { name: 'Edit Code' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit Name' })).toBeInTheDocument()
  })

  it('offers the myDATA code while it is unset', async () => {
    renderDetail(4)
    await screen.findByRole('heading', { name: 'KILOGRAM' })
    expect(screen.getByRole('button', { name: 'Edit myDATA code' })).toBeEnabled()
  })

  it('shows the myDATA code disabled with the reason once it is set', async () => {
    renderDetail(1)
    await screen.findByRole('heading', { name: 'PIECE' })

    /*
     * ⚠️ The `lockedReason` case — shown and DISABLED, never hidden.
     *
     * Recording a code is allowed exactly once and a second call is refused, so this is "editable in
     * general, fixed on this record". Hiding the control would leave an operator hunting for a field
     * that every unmapped unit visibly has; `editable: false` would say "not yours to edit", which
     * is about the role and is false here.
     */
    expect(screen.getByRole('button', { name: 'Edit myDATA code' })).toBeDisabled()
    expect(screen.getByText(/recorded once and cannot be corrected/)).toBeInTheDocument()
  })

  it('renames through PATCH …/name', async () => {
    const user = userEvent.setup()
    renderDetail(4)
    await screen.findByRole('heading', { name: 'KILOGRAM' })

    await user.click(screen.getByRole('button', { name: 'Edit Name' }))
    const input = screen.getByDisplayValue('Kilogram')
    await user.clear(input)
    await user.type(input, 'Kilo')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(requests.called('PATCH', '/api/units-of-measure/4/name')).toBe(true)
    })
  })

  it('gives a VIEW role no edit affordance at all', async () => {
    me = viewer
    renderDetail(4)
    await screen.findByRole('heading', { name: 'KILOGRAM' })

    expect(screen.queryByRole('button', { name: /^Edit / })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
  })
})

describe('adding a unit', () => {
  it('sends no write merely by rendering', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Add unit' })
    requests.expectNoWrites()
  })

  it('will not submit until the fractional choice has been made', async () => {
    const user = userEvent.setup()
    renderCreate()
    await screen.findByRole('button', { name: 'Add unit' })

    const submit = screen.getByRole('button', { name: 'Add unit' })
    await user.type(screen.getByLabelText('Code'), 'PALLET')
    await user.type(screen.getByLabelText('Name'), 'Pallet')

    /*
     * ⚠️ The assertion that matters on this form.
     *
     * `fractionalQuantityAllowed` is a PRIMITIVE boolean, so omitting it is refused outright — that
     * half is the server's, and `F4WriteContractIT` holds it. What no server check can decide is the
     * DEFAULT: an unticked checkbox sends `false`, which is accepted, and produces a unit that
     * cannot be sold by the half with nobody having chosen that. So the control is a required choice
     * and the form stays disabled until it is made.
     */
    expect(submit).toBeDisabled()

    await user.click(screen.getByRole('combobox'))
    await user.click(await screen.findByRole('option', { name: 'Fractional allowed' }))
    expect(submit).toBeEnabled()
  })

  it('always sends fractionalQuantityAllowed', async () => {
    const user = userEvent.setup()
    let sent: unknown
    server.use(
      http.post('http://localhost/api/units-of-measure', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({ ...kilogram, id: 20 }, { status: 201 })
      }),
    )

    renderCreate()
    await screen.findByRole('button', { name: 'Add unit' })
    await user.type(screen.getByLabelText('Code'), 'PALLET')
    await user.type(screen.getByLabelText('Name'), 'Pallet')
    await user.click(screen.getByRole('combobox'))
    await user.click(await screen.findByRole('option', { name: 'Whole numbers only' }))
    await user.click(screen.getByRole('button', { name: 'Add unit' }))

    await waitFor(() => {
      expect(sent).toMatchObject({
        code: 'PALLET',
        name: 'Pallet',
        // Present, and false because it was CHOSEN rather than defaulted.
        fractionalQuantityAllowed: false,
      })
    })
    // ⚠️ And no myDATA code, because leaving it empty must mean "no mapping" rather than "".
    expect(sent).not.toHaveProperty('mydataCode')
  })
})
