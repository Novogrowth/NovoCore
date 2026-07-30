import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'

import { ApiError, apiRequest, setUnauthenticatedHandler } from './http'

/**
 * The API client's behaviour, against a server that answers the way NovoCore answers.
 *
 * These are the properties every one of the 174 generated hooks inherits by going through this
 * one function, so they are worth asserting once, here, rather than hoping each call site got
 * them right.
 */

const received: { headers: Headers; body: string }[] = []

const server = setupServer(
  http.get('http://localhost/api/products', ({ request }) => {
    received.push({ headers: request.headers, body: '' })
    return HttpResponse.json({ items: [{ id: 1, sku: 'A' }] })
  }),
  http.post('http://localhost/api/products', async ({ request }) => {
    received.push({ headers: request.headers, body: await request.text() })
    return new HttpResponse(null, { status: 204 })
  }),
  http.get('http://localhost/api/forbidden', () =>
    // A permission refusal says nothing on purpose — step 14's deliberate asymmetry.
    HttpResponse.json({ status: 403, title: 'Forbidden' }, { status: 403 }),
  ),
  http.get('http://localhost/api/invalid', () =>
    HttpResponse.json(
      { status: 422, title: 'Unprocessable', detail: 'A cash sale of EUR 600.00 reaches the legal cash limit.' },
      { status: 422, headers: { 'content-type': 'application/problem+json' } },
    ),
  ),
  http.get('http://localhost/api/expired', () => new HttpResponse(null, { status: 401 })),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  received.length = 0
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  setUnauthenticatedHandler(undefined)
})
afterAll(() => server.close())

describe('CSRF', () => {
  it('echoes the XSRF-TOKEN cookie back on a state-changing request', async () => {
    // Spring Security writes this cookie non-HttpOnly precisely so it can be read and echoed;
    // the session cookie stays HttpOnly and is never touched.
    document.cookie = 'XSRF-TOKEN=a-token-value; path=/'

    await apiRequest({ url: 'http://localhost/api/products', method: 'POST', data: { sku: 'A' } })

    expect(received[0]?.headers.get('X-XSRF-TOKEN')).toBe('a-token-value')
  })

  it('does not send the header on a read', async () => {
    document.cookie = 'XSRF-TOKEN=a-token-value; path=/'
    await apiRequest({ url: 'http://localhost/api/products', method: 'GET' })
    expect(received[0]?.headers.get('X-XSRF-TOKEN')).toBeNull()
  })

  it('decodes a token that arrived percent-encoded', async () => {
    document.cookie = `XSRF-TOKEN=${encodeURIComponent('a+b/c=')}; path=/`
    await apiRequest({ url: 'http://localhost/api/products', method: 'POST', data: {} })
    expect(received[0]?.headers.get('X-XSRF-TOKEN')).toBe('a+b/c=')
  })
})

describe('request bodies', () => {
  it('sends JSON by default', async () => {
    await apiRequest({ url: 'http://localhost/api/products', method: 'POST', data: { sku: 'A' } })
    expect(received[0]?.headers.get('content-type')).toContain('application/json')
    expect(received[0]?.body).toBe('{"sku":"A"}')
  })

  it('sends form encoding when asked, which is what Spring Security’s /login reads', async () => {
    await apiRequest({
      url: 'http://localhost/api/products',
      method: 'POST',
      formEncoded: true,
      data: { username: 'owner', password: 'a b&c' },
    })
    expect(received[0]?.headers.get('content-type')).toContain('application/x-www-form-urlencoded')
    expect(received[0]?.body).toBe('username=owner&password=a+b%26c')
  })

  it('returns undefined rather than parsing a 204', async () => {
    const result = await apiRequest({
      url: 'http://localhost/api/products',
      method: 'POST',
      data: {},
    })
    expect(result).toBeUndefined()
  })
})

describe('refusals', () => {
  it('carries the detail of a validation refusal, because it explains itself', async () => {
    await expect(
      apiRequest({ url: 'http://localhost/api/invalid', method: 'GET' }),
    ).rejects.toMatchObject({
      status: 422,
      detail: 'A cash sale of EUR 600.00 reaches the legal cash limit.',
    })
  })

  it('has no detail on a permission refusal, and does not invent one', async () => {
    try {
      await apiRequest({ url: 'http://localhost/api/forbidden', method: 'GET' })
      expect.unreachable('should have thrown')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      const refusal = error as ApiError
      expect(refusal.isForbidden).toBe(true)
      expect(refusal.detail).toBeUndefined()
    }
  })

  it('tells the session handler once when the session is gone', async () => {
    const onExpired = vi.fn()
    setUnauthenticatedHandler(onExpired)

    await expect(
      apiRequest({ url: 'http://localhost/api/expired', method: 'GET' }),
    ).rejects.toBeInstanceOf(ApiError)

    expect(onExpired).toHaveBeenCalledTimes(1)
  })
})

describe('query parameters', () => {
  it('omits undefined and null rather than sending the words', async () => {
    server.use(
      http.get('http://localhost/api/products', ({ request }) => {
        expect(new URL(request.url).search).toBe('?active=true')
        return HttpResponse.json({ items: [] })
      }),
    )

    await apiRequest({
      url: 'http://localhost/api/products',
      method: 'GET',
      params: { active: true, supplierId: undefined, sku: null },
    })
  })

  it('refuses a parameter that is not a scalar instead of sending [object Object]', async () => {
    await expect(
      apiRequest({
        url: 'http://localhost/api/products',
        method: 'GET',
        params: { filter: { nested: true } },
      }),
    ).rejects.toThrow(TypeError)
  })
})
