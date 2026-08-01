import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { AccessLevel, Section, type Me, type UserView } from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { OWNER_ROLE, aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { UserCreate } from './user-create'
import { UserDetail } from './user-detail'
import { UsersList } from './users-list'

/**
 * Accounts, and the one credential this application ever puts on screen.
 *
 * The decision these tests hold to: **generate, show once, force an acknowledgment, never again.**
 * No confirm-field — the operator did not choose the value and cannot mistype it. What can actually
 * go wrong is closing the dialog without having taken the password, and the only defence against
 * that is refusing to close until somebody says they have it.
 */

const owner: Me = aUser({
  id: 1,
  role: OWNER_ROLE,
  sections: everySectionAt(AccessLevel.FULL),
})

/** Signed in as the account being looked at — the one case where the role is fixed. */
const self: Me = { ...owner, id: 3 }

const viewer: Me = aUser({
  id: 8,
  role: { id: 5, name: 'VIEWER', fullAccess: false, systemRole: false },
  sections: [{ section: Section.USERS_AND_ROLES, level: AccessLevel.VIEW, available: true }],
})

const kostas: UserView = {
  id: 3,
  username: 'kostas',
  displayName: 'Kostas',
  language: 'el',
  role: { id: 1, name: 'OWNER', fullAccess: true, systemRole: true, active: true },
  active: true,
}

let me: Me = owner

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/users', () => HttpResponse.json({ items: [kostas] })),
  http.get('http://localhost/api/users/3', () => HttpResponse.json(kostas)),
  http.get('http://localhost/api/roles', () =>
    HttpResponse.json({
      items: [
        { id: 1, name: 'OWNER', fullAccess: true, systemRole: true, active: true },
        { id: 3, name: 'TEST-ROLE-SHOP', fullAccess: false, systemRole: false, active: true },
      ],
    }),
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
        <UsersList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail(id: number) {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={[`/users/${id}`]}>
        <Routes>
          <Route path="/users/:id" element={<UserDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderCreate() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/users/new']}>
        <Routes>
          <Route path="/users/new" element={<UserCreate />} />
          <Route path="/users/:id" element={<p>the account</p>} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the user list', () => {
  it('sends the typed term as ?search=, alongside the active-only filter', async () => {
    const seen: URLSearchParams[] = []
    server.use(
      http.get('http://localhost/api/users', ({ request }) => {
        seen.push(new URL(request.url).searchParams)
        return HttpResponse.json({ items: [] })
      }),
    )

    const user = userEvent.setup()
    renderList()

    await user.type(screen.getByLabelText('Search'), 'maria')

    await waitFor(() => expect(seen.at(-1)?.get('search')).toBe('maria'))
    expect(seen.at(-1)?.get('active')).toBe('true')
  })

  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByText('kostas')
    requests.expectNoWrites()
  })
})

describe('one account', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail(3)
    await screen.findByRole('heading', { name: 'Kostas' })
    requests.expectNoWrites()
  })

  it('offers no way to change a username, because there is no route that does', async () => {
    renderDetail(3)
    await screen.findByRole('heading', { name: 'Kostas' })

    // Display name and role. Not the username, and not the language.
    expect(screen.getAllByRole('button', { name: /^Edit / })).toHaveLength(2)
    expect(screen.getByText(/audit log and every record's author refer to it/i)).toBeInTheDocument()
  })

  it('fixes the role on your own account, shown disabled with the reason', async () => {
    // `UserServiceImpl.changeRole` refuses it: moving yourself into a role you can edit would let
    // one person grant themselves anything. Editable on every other account, so it is shown.
    me = self
    renderDetail(3)
    await screen.findByRole('heading', { name: 'Kostas' })

    const edits = screen.getAllByRole('button', { name: /^Edit / })
    expect(edits[0]).toBeEnabled()
    expect(edits[1]).toBeDisabled()
    expect(screen.getByText(/cannot change your own role/i)).toBeInTheDocument()
  })

  it('shows a VIEW role no affordance at all', async () => {
    me = viewer
    renderDetail(3)
    await screen.findByRole('heading', { name: 'Kostas' })

    expect(screen.queryByRole('button', { name: /^Edit / })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Set password' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
  })
})

describe('handing over a password', () => {
  /** Opens the dialog and answers with what it is showing. */
  async function openHandoff(user: ReturnType<typeof userEvent.setup>) {
    renderDetail(3)
    await screen.findByRole('heading', { name: 'Kostas' })
    await user.click(screen.getByRole('button', { name: 'Set password' }))

    const field = await screen.findByLabelText<HTMLInputElement>('Password')
    return field.value
  }

  it('generates one that satisfies the policy, and sends nothing until it is confirmed', async () => {
    const user = userEvent.setup()
    const shown = await openHandoff(user)

    expect(shown.length).toBeGreaterThanOrEqual(12)
    // Opening the dialog is not setting a password. Nothing has been written yet.
    requests.expectNoWrites()
  })

  it('sets exactly the password it displayed', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      http.patch('http://localhost/api/users/3/password', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const user = userEvent.setup()
    const shown = await openHandoff(user)
    await user.click(screen.getByRole('button', { name: 'Set password' }))

    await waitFor(() => expect(body).toBeDefined())
    expect(body).toEqual({ password: shown })
  })

  it('will not close until somebody says they have taken it', async () => {
    /*
     * The whole point of the dialog. No route returns a password and nothing stores it in the
     * clear, so a dialog closed too early means an account nobody can sign in to — and the only
     * remedy is another reset.
     */
    server.use(
      http.patch('http://localhost/api/users/3/password', () => new HttpResponse(null, { status: 204 })),
    )

    const user = userEvent.setup()
    await openHandoff(user)
    await user.click(screen.getByRole('button', { name: 'Set password' }))

    const done = await screen.findByRole('button', { name: 'Done' })
    expect(done).toBeDisabled()

    await user.click(screen.getByRole('checkbox'))
    expect(done).toBeEnabled()

    await user.click(done)
    await waitFor(() => expect(screen.queryByLabelText('Password')).not.toBeInTheDocument())
  })

  it('cannot be reopened on the same password', async () => {
    // Closed, and the value is gone with the component. Pressing the button again generates a new
    // one rather than showing the old one, because nothing anywhere still has it.
    server.use(
      http.patch('http://localhost/api/users/3/password', () => new HttpResponse(null, { status: 204 })),
    )

    const user = userEvent.setup()
    const first = await openHandoff(user)
    await user.click(screen.getByRole('button', { name: 'Set password' }))
    await user.click(await screen.findByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'Done' }))
    await waitFor(() => expect(screen.queryByLabelText('Password')).not.toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'Set password' }))
    const second = await screen.findByLabelText<HTMLInputElement>('Password')
    expect(second.value).not.toBe(first)
    // And the acknowledgment did not carry over from the first hand-off.
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  it('shows the refusal and leaves the password on screen', async () => {
    server.use(
      http.patch('http://localhost/api/users/3/password', () =>
        HttpResponse.json(
          { status: 422, title: 'Unprocessable Content', detail: 'Password must be at least 12 characters.' },
          { status: 422, headers: { 'content-type': 'application/problem+json' } },
        ),
      ),
    )

    const user = userEvent.setup()
    const shown = await openHandoff(user)
    await user.click(screen.getByRole('button', { name: 'Set password' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('at least 12 characters')
    // Still open, still showing the same value: a refused write must not discard it silently.
    expect(screen.getByLabelText<HTMLInputElement>('Password').value).toBe(shown)
  })
})

describe('creating an account', () => {
  it('sends the generated password with the account, and shows it only once the account exists', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      http.post('http://localhost/api/users', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...kostas, id: 9, username: 'test.probe' }, { status: 201 })
      }),
    )

    const user = userEvent.setup()
    renderCreate()
    await screen.findByRole('button', { name: 'New user' })

    // Nothing on screen before the account exists.
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('Username'), 'test.probe')
    await user.type(screen.getByLabelText('Display name'), 'A probe')
    await user.click(screen.getByLabelText('Role'))
    await user.click(await screen.findByRole('option', { name: 'TEST-ROLE-SHOP' }))
    await user.click(screen.getByRole('button', { name: 'New user' }))

    await waitFor(() => expect(body).toBeDefined())
    expect(body).toEqual({
      username: 'test.probe',
      displayName: 'A probe',
      rawPassword: expect.any(String),
      roleId: 3,
    })

    const shown = await screen.findByLabelText<HTMLInputElement>('Password')
    expect(shown.value).toBe(body!.rawPassword)
    // And it is still gated: the account exists, so this is the only time anyone sees this value.
    expect(screen.getByRole('button', { name: 'Done' })).toBeDisabled()
  })

  it('will not submit without a role', async () => {
    const user = userEvent.setup()
    renderCreate()
    await screen.findByRole('button', { name: 'New user' })

    await user.type(screen.getByLabelText('Username'), 'test.probe')
    await user.type(screen.getByLabelText('Display name'), 'A probe')
    expect(screen.getByRole('button', { name: 'New user' })).toBeDisabled()
  })
})
