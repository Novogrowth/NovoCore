import type { SetupServer } from 'msw/node'
import { expect } from 'vitest'

/**
 * What a screen asked the server for — and, more importantly, what it should not have.
 *
 * **The standing pattern for every screen test: rendering a screen must send no write.**
 *
 * This exists because the opposite was true and nobody noticed. Every POST, PATCH, PUT and DELETE
 * in the generated client was emitted as a `useQuery`, so a component that merely rendered a write
 * hook would have sent the request on mount, and again on every refetch, invalidation and window
 * focus. `client-shape.test.ts` now proves the client is wired correctly; this proves each screen
 * *uses* it correctly, which is a different question — a screen can still fire a mutation in an
 * effect, or on a render-time condition, and produce the same outcome from correct machinery.
 *
 * Use it in every screen test:
 *
 * ```ts
 * const requests = trackRequests(server)
 * afterEach(() => requests.reset())
 *
 * it('sends no write merely by rendering', async () => {
 *   renderScreen()
 *   await screen.findByRole('heading', { name: 'Espresso blend 1kg' })
 *   requests.expectNoWrites()
 * })
 * ```
 *
 * It listens to the mock server itself rather than to individual handlers, so it sees **every**
 * request — including one to a route the test never set up, which a per-handler counter cannot.
 */

interface RecordedRequest {
  method: string
  path: string
}

export interface RequestLog {
  /** Every request, in order. */
  all(): { method: string; path: string }[]
  /** Everything that was not a read. */
  writes(): RecordedRequest[]
  /** Requests to one path, whatever the method. */
  to(path: string): RecordedRequest[]
  /** True when a request was made to this path — the negative is what most tests assert. */
  called(method: string, path: string): boolean
  /** Fails, naming them, if the screen sent anything other than a read. */
  expectNoWrites(): void
  reset(): void
}

const READ_METHODS = ['GET', 'HEAD', 'OPTIONS']

export function trackRequests(server: SetupServer): RequestLog {
  let recorded: RecordedRequest[] = []

  server.events.on('request:start', ({ request }) => {
    recorded.push({ method: request.method.toUpperCase(), path: new URL(request.url).pathname })
  })

  const writes = () => recorded.filter((request) => !READ_METHODS.includes(request.method))

  return {
    all: () => [...recorded],
    writes,
    to: (path) => recorded.filter((request) => request.path === path),
    called: (method, path) =>
      recorded.some(
        (request) => request.method === method.toUpperCase() && request.path === path,
      ),
    expectNoWrites: () => {
      expect(
        writes().map((request) => `${request.method} ${request.path}`),
        'rendering a screen must not write. A write here means a mutation is being fired by ' +
          'rendering rather than by an operator — see client-shape.test.ts for the defect this ' +
          'pattern exists to catch.',
      ).toEqual([])
    },
    reset: () => {
      recorded = []
    },
  }
}
