import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { AccessLevel, Section, type DeliveryMethodView, type Me } from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { DeliveryMethodCreate, DeliveryMethodDetail, DeliveryMethodsList } from './delivery-methods'

/**
 * Delivery methods — the plainest of R2's six screens, and the one with the least to hold.
 *
 * ⚠️ **Nothing in the schema references `delivery_method`** — measured 2026-08-04, zero foreign
 * keys — so `inUse` is always `false` and the abbreviation is always correctable. That is a fact
 * about the schema rather than about the data: it changes at 18b, when a dispatch document gains a
 * delivery method, and `DocumentReferenceGraphIT` makes that day a red build rather than a silent
 * gap.
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

const courier: DeliveryMethodView = {
  id: 1,
  abbreviation: 'ACS',
  description: 'ACS Courier',
  inUse: false,
  active: true,
}

let me: Me = owner

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/delivery-methods', () => HttpResponse.json({ items: [courier] })),
  http.get('http://localhost/api/delivery-methods/1', () => HttpResponse.json(courier)),
  http.post('http://localhost/api/delivery-methods', () =>
    HttpResponse.json({ ...courier, id: 9 }, { status: 201 }),
  ),
  http.patch('http://localhost/api/delivery-methods/1/abbreviation', () =>
    HttpResponse.json(courier),
  ),
  http.patch('http://localhost/api/delivery-methods/1/description', () =>
    HttpResponse.json(courier),
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
        <DeliveryMethodsList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/settings/delivery-methods/1']}>
        <Routes>
          <Route path="/settings/delivery-methods/:id" element={<DeliveryMethodDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderCreate() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/settings/delivery-methods/new']}>
        <Routes>
          <Route path="/settings/delivery-methods/new" element={<DeliveryMethodCreate />} />
          <Route path="/settings/delivery-methods/:id" element={<p>created</p>} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the delivery method list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByRole('link', { name: 'ACS' })
    requests.expectNoWrites()
  })

  it("offers a create control — this list is the business's own", async () => {
    renderList()
    await screen.findByRole('link', { name: 'ACS' })
    // The contrast with the AADE codification, which offers none and never will.
    expect(screen.getByText('New delivery method')).toBeInTheDocument()
  })
})

describe('one delivery method', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: 'ACS' })
    requests.expectNoWrites()
  })

  it('⚠️ offers the abbreviation for correction — R2 added the route it needed', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: 'ACS' })
    // Before R2 there was no route to change this on any installation, so the only remedy for a
    // typo was deactivate-and-recreate — which burns the abbreviation, because the unique index
    // is not partial.
    expect(screen.getByRole('button', { name: 'Edit Abbreviation' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Edit Description' })).toBeEnabled()
  })

  it('gives a VIEW role no edit affordance at all', async () => {
    me = viewer
    renderDetail()
    await screen.findByRole('heading', { name: 'ACS' })
    expect(screen.queryByRole('button', { name: /^Edit / })).not.toBeInTheDocument()
  })
})

describe('creating a delivery method', () => {
  it('sends no write merely by rendering', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Create delivery method' })
    requests.expectNoWrites()
  })
})
