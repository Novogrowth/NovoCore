import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { apiRequest } from '@/api/http'

import { AppQueryProvider } from './query-client'
import { useLogin, useLogout, useSession, useSessionExpiryHandler } from './session'

/**
 * Signing in, signing out, and a session that ends while somebody is working.
 *
 * These three are the one mechanism nothing else can compensate for: if the shell does not notice
 * that the user changed, it keeps showing the previous user's data behind a form that appears to
 * have done nothing. The tests drive the real hooks against a real query client, because every one
 * of the three defects they were written to catch lived in the interaction between the two.
 */

let signedIn = false

const server = setupServer(
  http.get('http://localhost/api/me', () =>
    signedIn
      ? HttpResponse.json({ id: 1, username: 'owner', displayName: 'The Owner', sections: [] })
      : new HttpResponse(null, { status: 401 }),
  ),
  http.post('http://localhost/login', () => {
    signedIn = true
    return new HttpResponse(null, { status: 204 })
  }),
  http.post('http://localhost/logout', () => {
    signedIn = false
    return new HttpResponse(null, { status: 204 })
  }),
  http.get('http://localhost/api/products', () =>
    signedIn
      ? HttpResponse.json({ items: [{ id: 1, sku: 'SECRET-SKU' }] })
      : new HttpResponse(null, { status: 401 }),
  ),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  signedIn = false
})
afterAll(() => server.close())

/** The shell, reduced to the one decision it makes: signed in, or not. */
function Shell() {
  const { me, isLoading, isSignedOut, error } = useSession()
  const login = useLogin()
  const logout = useLogout()
  useSessionExpiryHandler()

  return (
    <div>
      {isLoading && <span>loading</span>}
      {error && <span>server-error</span>}
      {!isLoading && !error && (isSignedOut || !me) && <span>signed-out</span>}
      {me && <span>signed-in as {me.displayName}</span>}

      <button onClick={() => login.mutate({ username: 'owner', password: 'correct' })}>
        sign in
      </button>
      <button onClick={() => logout.mutate()}>sign out</button>
      <button
        onClick={() => {
          // Any request can be the one that discovers the session is gone.
          void apiRequest({ url: 'http://localhost/api/products', method: 'GET' }).catch(() => {})
        }}
      >
        load products
      </button>
    </div>
  )
}

function renderShell() {
  return render(
    <AppQueryProvider>
      <Shell />
    </AppQueryProvider>,
  )
}

describe('the session', () => {
  it('shows the login form when nobody is signed in', async () => {
    renderShell()
    expect(await screen.findByText('signed-out')).toBeInTheDocument()
  })

  it('shows the application after a successful sign-in', async () => {
    const user = userEvent.setup()
    renderShell()
    await screen.findByText('signed-out')

    await user.click(screen.getByRole('button', { name: 'sign in' }))

    // The failure this catches: clearing the cache REMOVES the ['me'] query, so invalidating it
    // afterwards matches nothing, no refetch happens, and a correct password leaves the person
    // looking at the login form.
    expect(await screen.findByText(/signed-in as The Owner/)).toBeInTheDocument()
  })

  it('returns to the login form after signing out', async () => {
    const user = userEvent.setup()
    renderShell()
    await user.click(screen.getByRole('button', { name: 'sign in' }))
    await screen.findByText(/signed-in as The Owner/)

    await user.click(screen.getByRole('button', { name: 'sign out' }))

    // The failure this catches: removing a query does not notify the observer mounted on it, so
    // the shell keeps rendering the PREVIOUS user's data after they signed out.
    expect(await screen.findByText('signed-out')).toBeInTheDocument()
    expect(screen.queryByText(/signed-in as/)).not.toBeInTheDocument()
  })

  it('returns to the login form when the session expires mid-session', async () => {
    const user = userEvent.setup()
    renderShell()
    await user.click(screen.getByRole('button', { name: 'sign in' }))
    await screen.findByText(/signed-in as The Owner/)

    // The session ends on the server — an eviction, a restart, the eight-hour timeout.
    signedIn = false
    await user.click(screen.getByRole('button', { name: 'load products' }))

    await waitFor(() => expect(screen.getByText('signed-out')).toBeInTheDocument())
    expect(screen.queryByText(/signed-in as/)).not.toBeInTheDocument()
  })

  it('says the server is unreachable rather than pretending nobody is signed in', async () => {
    server.use(http.get('http://localhost/api/me', () => HttpResponse.error()))
    renderShell()

    // A network failure is not "signed out". Showing a login form here invites someone to type a
    // password at a server that cannot answer, and hides the actual problem.
    expect(await screen.findByText('server-error')).toBeInTheDocument()
  })
})
