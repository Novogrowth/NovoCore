import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { StrictMode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { AppQueryProvider } from '@/auth/query-client'

import App from './App'

/**
 * The real shell, mounted the way `main.tsx` mounts it.
 *
 * The session tests drive the hooks through a reduced harness, which is the right shape for testing
 * the hooks — and it is exactly why they missed a first-load hang in `App` itself. This renders the
 * component that actually ships.
 */

const server = setupServer(
  http.get('http://localhost/api/me', () => new HttpResponse(null, { status: 401 })),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('the application on first load', () => {
  it('reaches the login screen when nobody is signed in', async () => {
    render(
      <StrictMode>
        <AppQueryProvider>
          <MemoryRouter>
            <App />
          </MemoryRouter>
        </AppQueryProvider>
      </StrictMode>,
    )

    // The symptom this guards against: "Loading…" for ever, because the one query the shell waits
    // on never settles into a state the shell recognises.
    expect(await screen.findByLabelText('Password')).toBeInTheDocument()
    expect(screen.queryByText('Loading…')).not.toBeInTheDocument()
  })

  it('shows the shell to a signed-in user', async () => {
    server.use(
      http.get('http://localhost/api/me', () =>
        HttpResponse.json({
          id: 1,
          username: 'owner',
          displayName: 'The Owner',
          role: { id: 1, name: 'OWNER', fullAccess: true, systemRole: true },
          sections: [],
          restrictedFields: [],
        }),
      ),
    )

    render(
      <StrictMode>
        <AppQueryProvider>
          <MemoryRouter>
            <App />
          </MemoryRouter>
        </AppQueryProvider>
      </StrictMode>,
    )

    expect(await screen.findByText('The Owner')).toBeInTheDocument()
  })
})
