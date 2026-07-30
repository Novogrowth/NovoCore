import { apiRequest } from './http'

/**
 * The two endpoints the generated client does not know about.
 *
 * `/login` and `/logout` are Spring Security's own, not controllers of ours — SecurityConfiguration
 * says so explicitly and WebConfiguration scopes the section interceptor to `/api/**` for the same
 * reason. `OpenApiSpecIT` generates the spec from the handler mapping, so they are absent from it
 * and orval will never produce a hook for either. This file is the whole of the exception.
 *
 * Both answer with a status and no body — 204, or 401 — rather than the redirects Spring Security
 * defaults to, because a `fetch` that follows a 302 to a login page receives HTML with status 200
 * and cannot tell it apart from success.
 */

export interface Credentials {
  username: string
  password: string
}

/**
 * Establishes a session.
 *
 * Form-encoded because that is what Spring Security's filter reads; it is not a JSON endpoint and
 * sending JSON to it fails in a way that looks like a wrong password.
 *
 * The CSRF token needed here is already present: the cookie is written on the very first response,
 * 401 included, because SecurityConfiguration opts out of deferred token loading. `apiRequest`
 * picks it up like any other state-changing call.
 *
 * @throws ApiError with status 401 when the credentials are refused.
 */
export async function login(credentials: Credentials): Promise<void> {
  await apiRequest<void>({
    url: '/login',
    method: 'POST',
    formEncoded: true,
    data: { username: credentials.username, password: credentials.password },
  })
}

/**
 * Ends the session.
 *
 * The server invalidates it and deletes the cookie; the caller is responsible for discarding
 * cached data, which `useLogout` does by clearing the query cache. Answering 204 rather than
 * redirecting is again deliberate on the backend.
 */
export async function logout(): Promise<void> {
  await apiRequest<void>({ url: '/logout', method: 'POST' })
}
