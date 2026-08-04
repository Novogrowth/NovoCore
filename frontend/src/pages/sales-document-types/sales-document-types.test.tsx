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
  type SalesDocumentTypeView,
} from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import {
  SalesDocumentTypeCreate,
  SalesDocumentTypeDetail,
  SalesDocumentTypesList,
} from './sales-document-types'

/**
 * Sales document types — the business's own list, which ships empty.
 *
 * **The three things here that no other screen in this application does:**
 *
 * - ⚠️ **A stock flag has THREE states and `null` is not `false`.** R1b branches the consumption
 *   path on it: a type whose `affectsStock` is `false` records a document and *silently* consumes
 *   nothing. A checkbox would decide that for the operator, invisibly.
 * - ⚠️ **Activate is shown DISABLED with the reason while a flag is unset**, mirroring
 *   `sales_document_type_active_has_stock_behaviour`. Not hidden — an operator who cannot see why
 *   the button does nothing cannot act on it.
 * - ⚠️ **The AADE picker offers 34 codes, not 55**, and every option leads with the code, because
 *   two of them share the description `Για Μελλοντική Χρήση` exactly.
 *
 * **A screen test still cannot tell you a write works.** `R2ReferenceDataContractIT` is what asks
 * the real server.
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

/** Both flags answered, so it is active and offered by forms. */
const receipt: SalesDocumentTypeView = {
  id: 1,
  description: 'Απόδειξη Λιανικής',
  affectsStock: true,
  transfersStock: true,
  requiresMydataTransmission: true,
  aadeInvoiceTypeId: 20,
  aadeInvoiceTypeCode: '11.1',
  sortCode: 90,
  active: true,
  draft: false,
}

/**
 * ⚠️ A DRAFT — `affectsStock` and `transfersStock` are both absent, which is what the wire looks
 * like when nobody has answered. Not `false`.
 */
const draft: SalesDocumentTypeView = {
  id: 2,
  description: 'Προσφορά',
  requiresMydataTransmission: false,
  sortCode: 80,
  active: false,
  // Derived: a stock flag is ABSENT, so the server computes draft = true.
  draft: true,
}

const issuedCodes: AadeInvoiceTypeView[] = [
  {
    id: 20,
    code: '11.1',
    description: 'ΑΛΠ',
    group: AadeInvoiceGroup.ISSUER_UNMATCHED,
    active: true,
  },
  {
    id: 13,
    code: '4',
    description: 'Για Μελλοντική Χρήση',
    group: AadeInvoiceGroup.ISSUER_MATCHED,
    active: true,
  },
]

let me: Me = owner
let types: SalesDocumentTypeView[] = [receipt, draft]

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/sales-document-types', () => HttpResponse.json({ items: types })),
  http.get('http://localhost/api/sales-document-types/1', () => HttpResponse.json(receipt)),
  http.get('http://localhost/api/sales-document-types/2', () => HttpResponse.json(draft)),
  http.get('http://localhost/api/aade-invoice-types', () =>
    HttpResponse.json({ items: issuedCodes }),
  ),
  http.post('http://localhost/api/sales-document-types', () =>
    HttpResponse.json({ ...receipt, id: 30 }, { status: 201 }),
  ),
  http.patch('http://localhost/api/sales-document-types/1/description', () =>
    HttpResponse.json(receipt),
  ),
  http.put('http://localhost/api/sales-document-types/2/stock-behaviour', () =>
    HttpResponse.json({ ...draft, affectsStock: true, transfersStock: true }),
  ),
  http.post('http://localhost/api/sales-document-types/2/reactivate', () =>
    new HttpResponse(null, { status: 204 }),
  ),
)

const requests = trackRequests(server)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  me = owner
  types = [receipt, draft]
  requests.reset()
})
afterAll(() => server.close())

function renderList() {
  return render(
    <AppQueryProvider>
      <MemoryRouter>
        <SalesDocumentTypesList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail(id: number) {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={[`/settings/sales-document-types/${id}`]}>
        <Routes>
          <Route path="/settings/sales-document-types/:id" element={<SalesDocumentTypeDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderCreate() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/settings/sales-document-types/new']}>
        <Routes>
          <Route path="/settings/sales-document-types/new" element={<SalesDocumentTypeCreate />} />
          <Route path="/settings/sales-document-types/:id" element={<p>created</p>} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the sales document type list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByRole('link', { name: 'Απόδειξη Λιανικής' })
    requests.expectNoWrites()
  })

  it('draws an unanswered stock flag as undecided, never as a no', async () => {
    renderList()
    await screen.findByRole('link', { name: 'Προσφορά' })
    // "stock does not move" and "nobody has said" are different facts about a document type, and
    // only the second is true of this row.
    expect(screen.getAllByText('Not decided').length).toBeGreaterThan(0)
  })

  it('surfaces the drafts as a standing to-do', async () => {
    renderList()
    // A draft is inactive and offered by no form; a decision nobody can see is one nobody finishes.
    expect(await screen.findByText(/draft/)).toBeInTheDocument()
  })

  it('draws an absent AADE code as an ordinary state, not an empty field', async () => {
    renderList()
    await screen.findByRole('link', { name: 'Προσφορά' })
    // Six of the owner's nineteen types are operational documents with no AADE type at all.
    expect(screen.getAllByText('No AADE invoice type').length).toBeGreaterThan(0)
  })
})

describe('one sales document type', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail(1)
    await screen.findByRole('heading', { name: 'Απόδειξη Λιανικής' })
    requests.expectNoWrites()
  })

  it('⚠️ shows Activate DISABLED with the reason while a stock flag is unset', async () => {
    renderDetail(2)
    await screen.findByRole('heading', { name: 'Προσφορά' })

    /*
     * ⚠️ THE R1 CONSTRAINT THIS STEP EXISTS TO MEET, and the shape of the answer matters as much
     * as the answer. Disabled — not hidden, and not a refusal after the fact:
     *   - hidden leaves somebody hunting for a control every other row has;
     *   - a refusal after the fact makes the operator press a button to be told no.
     * The server refuses this too, with a fuller sentence; this only stops a request whose answer
     * is already certain.
     */
    expect(screen.getByRole('button', { name: 'Activate' })).toBeDisabled()
    expect(screen.getByText(/Set both stock flags before activating/)).toBeInTheDocument()
  })

  it('enables Activate once both flags are answered', async () => {
    renderDetail(1)
    await screen.findByRole('heading', { name: 'Απόδειξη Λιανικής' })
    // Active already, so the control on offer is Deactivate — which is the point: a decided type
    // is never stuck behind the draft guard.
    expect(screen.getByRole('button', { name: 'Deactivate' })).toBeEnabled()
  })

  it('names each Edit button by its field', async () => {
    renderDetail(1)
    await screen.findByRole('heading', { name: 'Απόδειξη Λιανικής' })
    // Five controls all called "Edit" are indistinguishable to a screen reader and to a test —
    // and every positional test that indexed into them broke the day a field was inserted above.
    expect(screen.getByRole('button', { name: 'Edit Description' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit Stock behaviour' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit AADE invoice type' })).toBeInTheDocument()
  })

  it('gives a VIEW role no edit affordance at all', async () => {
    me = viewer
    renderDetail(1)
    await screen.findByRole('heading', { name: 'Απόδειξη Λιανικής' })
    expect(screen.queryByRole('button', { name: /^Edit / })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
  })
})

describe('creating a sales document type', () => {
  it('sends no write merely by rendering', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Create document type' })
    requests.expectNoWrites()
  })

  it('⚠️ offers "not decided" as a real choice and starts there', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Create document type' })

    // The create form is the ONE place `undecided` can be chosen — `StockBehaviourRequest` boxes
    // both components as @Mandatory, so no request can unanswer the question afterwards.
    const affects = screen.getByRole('group', { name: 'Affects stock' })
    expect(affects).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Not decided' }).length).toBe(2)
    expect(screen.getByText(/saved as an inactive draft/)).toBeInTheDocument()
  })

  it('refuses to submit until myDATA transmission is answered — no default is taken', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Create document type' })
    // An unticked checkbox sends `false`, which the server accepts happily — so "not a tax
    // document" would be indistinguishable from "nobody said". F4's units form made the same call.
    expect(screen.getByRole('button', { name: 'Create document type' })).toBeDisabled()
  })
})
