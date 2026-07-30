import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { apiRequest } from '@/api/http'

import { trackRequests } from './requests'

/**
 * The guard, guarded.
 *
 * `expectNoWrites()` is the standing assertion in every screen test, so it has to fail when a write
 * happens — a green assertion that cannot go red is worse than no assertion, because it is believed.
 * These drive real requests through the real client and check both directions.
 */
const server = setupServer(
  http.get('http://localhost/api/products', () => HttpResponse.json({ items: [] })),
  http.post('http://localhost/api/products', () => HttpResponse.json({ id: 1 })),
  http.patch('http://localhost/api/products/1/name', () => HttpResponse.json({ id: 1 })),
  http.delete('http://localhost/api/products/1/components', () => new HttpResponse(null, { status: 204 })),
)

const requests = trackRequests(server)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => requests.reset())
afterAll(() => server.close())

describe('the request log', () => {
  it('passes when only reads happened', async () => {
    await apiRequest({ url: 'http://localhost/api/products', method: 'GET' })

    expect(() => requests.expectNoWrites()).not.toThrow()
    expect(requests.called('GET', '/api/products')).toBe(true)
  })

  it('fails when a POST happened, and names it', async () => {
    await apiRequest({ url: 'http://localhost/api/products', method: 'POST', data: {} })

    expect(() => requests.expectNoWrites()).toThrow(/POST \/api\/products/)
  })

  it('fails on a PATCH', async () => {
    await apiRequest({ url: 'http://localhost/api/products/1/name', method: 'PATCH', data: {} })
    expect(() => requests.expectNoWrites()).toThrow(/PATCH/)
  })

  it('fails on a DELETE', async () => {
    await apiRequest({ url: 'http://localhost/api/products/1/components', method: 'DELETE' })
    expect(() => requests.expectNoWrites()).toThrow(/DELETE/)
  })

  it('sees a request to a route the test never mentioned', async () => {
    // The reason this listens to the server rather than to handlers: a screen firing something
    // unexpected is exactly the case a per-handler counter cannot see.
    server.use(http.post('http://localhost/api/anything', () => HttpResponse.json({})))
    await apiRequest({ url: 'http://localhost/api/anything', method: 'POST', data: {} })

    expect(requests.writes().map((request) => request.path)).toEqual(['/api/anything'])
  })

  it('forgets everything on reset, so one test cannot fail another', async () => {
    await apiRequest({ url: 'http://localhost/api/products', method: 'POST', data: {} })
    requests.reset()
    expect(requests.all()).toEqual([])
    expect(() => requests.expectNoWrites()).not.toThrow()
  })
})
