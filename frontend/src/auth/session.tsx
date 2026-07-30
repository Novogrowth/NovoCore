import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'

import { login as postLogin, logout as postLogout, type Credentials } from '@/api/auth'
import { meControllerMe } from '@/api/generated/endpoints/me/me'
import type { Me } from '@/api/generated/model'
import { ApiError, setUnauthenticatedHandler } from '@/api/http'

/**
 * Who is signed in, loaded once and shared by everything.
 *
 * `GET /api/me` is the only source of the current user's grants, and it is deliberately the only
 * one: a screen that decides for itself what a role may do is a screen that will disagree with the
 * backend eventually, and the backend is the side that refuses.
 */
export const ME_QUERY_KEY = ['me'] as const

async function fetchMe(signal: AbortSignal): Promise<Me> {
  return meControllerMe(signal)
}

/**
 * Throws away everything loaded under the old session, WITHOUT removing `['me']` itself.
 *
 * ⚠️ **`queryClient.clear()` is the wrong tool here, and looks like the right one.** It removes
 * queries from the cache, and removing a query does not notify the observer mounted on it —
 * `QueryCache.remove` destroys the query and emits a cache event the component is not listening
 * for. So the shell keeps rendering the state it last saw: the previous user's name and data, after
 * they signed out. Worse, a subsequent `invalidateQueries(['me'])` matches nothing, because the
 * query it names has just been deleted, so nothing refetches and a correct password appears to do
 * nothing at all.
 *
 * Every one of those was a real defect in this file, and each is covered by a test in
 * `session.test.tsx` that fails against the old code.
 *
 * So: remove everything else, and drive `['me']` by writing to it.
 */
function discardOtherQueries(queryClient: QueryClient): void {
  queryClient.removeQueries({
    predicate: (query) => query.queryKey[0] !== ME_QUERY_KEY[0],
  })
}

/**
 * Ends the session in the UI the moment any request comes back 401.
 *
 * Without this, a session that expires mid-session leaves stale data on screen behind a login
 * form — and that data belonged to whoever was signed in before.
 */
export function useSessionExpiryHandler(): void {
  const queryClient = useQueryClient()

  useEffect(() => {
    setUnauthenticatedHandler(() => {
      // Written, not removed: this is the line that re-renders the shell.
      queryClient.setQueryData(ME_QUERY_KEY, null)
      discardOtherQueries(queryClient)
    })
    return () => setUnauthenticatedHandler(undefined)
  }, [queryClient])
}

export interface SessionState {
  me: Me | undefined
  isLoading: boolean
  /** True once the answer is known to be "nobody" — a 401 rather than a request in flight. */
  isSignedOut: boolean
  /**
   * The server could not answer. **Not** the same as signed out, and the caller must not treat it
   * as such: a login form in front of an unreachable server invites someone to type a password
   * that cannot be checked, and hides the real problem.
   */
  error: Error | undefined
}

/**
 * The current user.
 *
 * A 401 is not an error here — it is the answer "nobody is signed in", which is a normal state on
 * first load. Every other failure stays an error, because a Settings page that renders as though
 * the user has no grants when the server is down is worse than one that says the server is down.
 */
export function useSession(): SessionState {
  const query = useQuery<Me | null>({
    queryKey: ME_QUERY_KEY,
    queryFn: ({ signal }) => fetchMe(signal),
    retry: false,
    staleTime: 5 * 60_000,
  })

  // A 401 is an answer. Anything else — a 500, or a `TypeError` from fetch when the server is
  // unreachable, which is NOT an ApiError and was previously discarded — is a failure to report.
  const signedOut = query.error instanceof ApiError && query.error.isUnauthenticated

  return {
    me: query.data ?? undefined,
    isLoading: query.isLoading,
    isSignedOut: signedOut,
    error: query.error && !signedOut ? query.error : undefined,
  }
}

/** Signs in, then reloads `/api/me` so grants are the new user's rather than the previous one's. */
export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (credentials: Credentials) => postLogin(credentials),
    onSuccess: async () => {
      // Everything cached before this point was fetched as somebody else, or as nobody. `['me']`
      // is deliberately left in place — see discardOtherQueries — so that invalidating it here
      // actually refetches. Removing it first is what made a correct password appear to do nothing.
      discardOtherQueries(queryClient)
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
      // Written to null rather than removed, for the reason discardOtherQueries states: removing
      // it leaves the shell rendering the person who just signed out.
      queryClient.setQueryData(ME_QUERY_KEY, null)
      discardOtherQueries(queryClient)
    },
  })
}
