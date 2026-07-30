import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'

import { login as postLogin, logout as postLogout, type Credentials } from '@/api/auth'
import { ApiError, apiRequest, setUnauthenticatedHandler } from '@/api/http'
import type { Me } from '@/api/generated/model'

/**
 * Who is signed in, loaded once and shared by everything.
 *
 * `GET /api/me` is the only source of the current user's grants, and it is deliberately the only
 * one: a screen that decides for itself what a role may do is a screen that will disagree with the
 * backend eventually, and the backend is the side that refuses.
 */
export const ME_QUERY_KEY = ['me'] as const

async function fetchMe(signal: AbortSignal): Promise<Me> {
  return apiRequest<Me>({ url: '/api/me', method: 'GET', signal })
}

/**
 * Drops every cached query the moment any request comes back 401.
 *
 * Without this, a session that expires mid-session leaves stale data on screen behind a login
 * form — and worse, that data belonged to whoever was signed in before.
 */
export function useSessionExpiryHandler(): void {
  const queryClient = useQueryClient()

  useEffect(() => {
    setUnauthenticatedHandler(() => {
      queryClient.setQueryData(ME_QUERY_KEY, null)
      queryClient.clear()
    })
    return () => setUnauthenticatedHandler(undefined)
  }, [queryClient])
}

export interface SessionState {
  me: Me | undefined
  isLoading: boolean
  /** True once the answer is known to be "nobody" — a 401 rather than a request in flight. */
  isSignedOut: boolean
  error: ApiError | undefined
}

/**
 * The current user.
 *
 * A 401 is not an error here — it is the answer "nobody is signed in", which is a normal state on
 * first load. Every other failure stays an error, because a Settings page that renders as though
 * the user has no grants when the server is down is worse than one that says the server is down.
 */
export function useSession(): SessionState {
  const query = useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: ({ signal }) => fetchMe(signal),
    retry: false,
    staleTime: 5 * 60_000,
  })

  const error = query.error instanceof ApiError ? query.error : undefined

  return {
    me: query.data ?? undefined,
    isLoading: query.isLoading,
    isSignedOut: error?.isUnauthenticated ?? false,
    error: error?.isUnauthenticated ? undefined : error,
  }
}

/** Signs in, then reloads `/api/me` so grants are the new user's rather than the previous one's. */
export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (credentials: Credentials) => postLogin(credentials),
    onSuccess: async () => {
      // Clear first: anything cached before this point was fetched as somebody else, or as
      // nobody. Refetching /api/me afterwards is what the app waits on.
      queryClient.clear()
      await queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY })
    },
  })
}

/** Signs out and discards everything that was loaded under the session. */
export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => postLogout(),
    onSuccess: () => {
      queryClient.clear()
    },
  })
}
