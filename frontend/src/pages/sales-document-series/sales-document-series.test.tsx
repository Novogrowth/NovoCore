import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import {
  AccessLevel,
  SalesChannel,
  type Me,
  type SalesDocumentSeriesView,
  type SalesDocumentTypeView,
} from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import {
  SalesDocumentSeriesCreate,
  SalesDocumentSeriesDetail,
  SalesDocumentSeriesList,
} from './sales-document-series'

/**
 * Sales document series.
 *
 * **Two things this file holds that nothing else can:**
 *
 * - ⚠️ **A null channel is a NAMED option, never a blank.** Six of the owner's series are
 *   channel-less, so this is the normal case. And since R1b a sales invoice's channel comes FROM
 *   its series — there is no channel field on the invoice at all — so what is chosen here decides
 *   how every future sale in the series is attributed.
 * - ⚠️ **The R2 freeze is `lockedReason`, not the plain-text treatment.** Abbreviation, document
 *   type and ΜΑΡΚ are correctable while nothing has been recorded and shown-disabled-with-reason
 *   afterwards. Before R2 they had no route at all; the whole point of the backend sub-part R2 grew
 *   is that they now do.
 */

const owner: Me = aUser({
  id: 1,
  role: { id: 1, name: 'OWNER', fullAccess: true, systemRole: true },
  sections: everySectionAt(AccessLevel.FULL),
})

const receiptType: SalesDocumentTypeView = {
  id: 1,
  description: 'Απόδειξη Λιανικής',
  affectsStock: true,
  transfersStock: true,
  requiresMydataTransmission: true,
  sortCode: 70,
  active: true,
  draft: false,
}

/** Nothing recorded in it yet — every field correctable. */
const fresh: SalesDocumentSeriesView = {
  id: 10,
  abbreviation: 'ΑΛΠ',
  description: 'Store series',
  documentTypeId: 1,
  documentTypeDescription: 'Απόδειξη Λιανικής',
  channel: SalesChannel.STORE_AND_PHONE,
  getsMark: true,
  inUse: false,
  sortCode: 60,
  active: true,
}

/** ⚠️ Documents recorded — the three fields freeze. */
const used: SalesDocumentSeriesView = {
  id: 11,
  abbreviation: 'ΑΛΠW',
  description: 'Web series',
  documentTypeId: 1,
  documentTypeDescription: 'Απόδειξη Λιανικής',
  channel: SalesChannel.ECOMMERCE,
  getsMark: true,
  inUse: true,
  sortCode: 50,
  active: true,
}

/** ⚠️ Self-supply: no channel at all, which is a real configuration rather than a gap. */
const selfSupply: SalesDocumentSeriesView = {
  id: 12,
  abbreviation: 'ΣΑΥΤ',
  description: 'Self-supply series',
  documentTypeId: 1,
  documentTypeDescription: 'Απόδειξη Λιανικής',
  getsMark: false,
  inUse: false,
  sortCode: 40,
  active: true,
}

const allSeries = [fresh, used, selfSupply]
let me: Me = owner

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/sales-document-series', () =>
    HttpResponse.json({ items: allSeries }),
  ),
  http.get('http://localhost/api/sales-document-series/10', () => HttpResponse.json(fresh)),
  http.get('http://localhost/api/sales-document-series/11', () => HttpResponse.json(used)),
  http.get('http://localhost/api/sales-document-series/12', () => HttpResponse.json(selfSupply)),
  http.get('http://localhost/api/sales-document-types', () =>
    HttpResponse.json({ items: [receiptType] }),
  ),
  http.post('http://localhost/api/sales-document-series', () =>
    HttpResponse.json({ ...fresh, id: 40 }, { status: 201 }),
  ),
  http.patch('http://localhost/api/sales-document-series/10/abbreviation', () =>
    HttpResponse.json(fresh),
  ),
  http.delete('http://localhost/api/sales-document-series/10/channel', () =>
    HttpResponse.json({ ...fresh, channel: undefined }),
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
        <SalesDocumentSeriesList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail(id: number) {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={[`/settings/sales-document-series/${id}`]}>
        <Routes>
          <Route
            path="/settings/sales-document-series/:id"
            element={<SalesDocumentSeriesDetail />}
          />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderCreate() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/settings/sales-document-series/new']}>
        <Routes>
          <Route
            path="/settings/sales-document-series/new"
            element={<SalesDocumentSeriesCreate />}
          />
          <Route path="/settings/sales-document-series/:id" element={<p>created</p>} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the sales document series list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByRole('link', { name: 'ΑΛΠ' })
    requests.expectNoWrites()
  })

  it('⚠️ names the channel-less series rather than leaving its cell blank', async () => {
    renderList()
    await screen.findByRole('link', { name: 'ΣΑΥΤ' })
    // "not a sales channel" is a fact about a self-supply series — the customer is the issuer —
    // and a blank cell would read as a field somebody skipped.
    expect(screen.getAllByText('Not a sales channel').length).toBeGreaterThan(0)
  })

  it('marks a series documents have been recorded in', async () => {
    renderList()
    await screen.findByRole('link', { name: 'ΑΛΠW' })
    expect(screen.getAllByText('In use').length).toBeGreaterThan(0)
  })
})

describe('one sales document series', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail(10)
    await screen.findByRole('heading', { name: 'ΑΛΠ' })
    requests.expectNoWrites()
  })

  it('⚠️ offers all three freezable fields while nothing has been recorded', async () => {
    renderDetail(10)
    await screen.findByRole('heading', { name: 'ΑΛΠ' })

    /*
     * ⚠️ THIS IS THE NEGATIVE CONTROL FOR THE TEST BELOW, and it is why the pair is worth more
     * than either half. A screen that disabled these unconditionally would pass the freeze test
     * and fail nothing — and before R2 that is exactly what it would have done, because no route
     * to change them existed on any installation.
     */
    expect(screen.getByRole('button', { name: 'Edit Abbreviation' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Edit Document type' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Edit ΜΑΡΚ' })).toBeEnabled()
  })

  it('⚠️ shows the three DISABLED with the reason once documents are recorded', async () => {
    renderDetail(11)
    await screen.findByRole('heading', { name: 'ΑΛΠW' })

    // `lockedReason`: shown, disabled, with the reason — the SECOND field state, "editable in
    // general, fixed on this record". Not hidden, and not the plain-text no-route treatment,
    // because a route does exist and works on every series nothing has been recorded in.
    expect(screen.getByRole('button', { name: 'Edit Abbreviation' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Edit Document type' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Edit ΜΑΡΚ' })).toBeDisabled()
    expect(screen.getAllByText(/already been recorded in this series/).length).toBeGreaterThan(0)
  })

  it('leaves description and channel editable on a used series', async () => {
    renderDetail(11)
    await screen.findByRole('heading', { name: 'ΑΛΠW' })
    // The freeze is three fields, not the whole record: a description is a label and a channel is
    // configuration the backend lets you change at any time.
    expect(screen.getByRole('button', { name: 'Edit Description' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Edit Sales channel' })).toBeEnabled()
  })

  it('says that an invoice takes its channel from the series', async () => {
    renderDetail(10)
    await screen.findByRole('heading', { name: 'ΑΛΠ' })
    // R1b removed `channel` from `NewSalesInvoice` entirely. Without this line the field looks
    // like a label rather than the thing that decides every future sale's attribution.
    expect(screen.getByText(/comes FROM its series/)).toBeInTheDocument()
  })
})

describe('creating a sales document series', () => {
  it('sends no write merely by rendering', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Create series' })
    requests.expectNoWrites()
  })

  it('⚠️ starts the channel at the NAMED "not a sales channel" option, never a blank', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Create series' })
    // A blank default would read as "not answered yet". Channel-less is a real answer, and six of
    // the owner's series give it.
    expect(screen.getByLabelText('Sales channel')).toHaveTextContent('Not a sales channel')
  })

  it('warns that three fields freeze once the series is used', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Create series' })
    expect(screen.getByText(/After that they are fixed/)).toBeInTheDocument()
  })
})
