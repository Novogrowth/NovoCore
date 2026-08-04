import { MutationCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useMemo, type ReactNode } from 'react'

import { ApiError } from '@/api/http'

/**
 * The shared query client.
 *
 * Its own file rather than sitting beside the session hooks, so that editing a hook does not
 * remount the provider during development.
 */
export function createQueryClient(): QueryClient {
  const client = new QueryClient({
    defaultOptions: {
      queries: {
        /*
         * A refusal is an answer, not a failure to retry. Asking three times for something the
         * role may not see produces the same 403 three times, delays the screen by seconds, and
         * fills the network log with one cause repeated.
         */
        retry: (failureCount, error) => {
          if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
          return failureCount < 2
        },
        staleTime: 30_000,
        refetchOnWindowFocus: false,
      },
    },

    /*
     * ⚠️ EVERY SUCCESSFUL WRITE INVALIDATES EVERY QUERY. One mechanism, deliberately global.
     *
     * **The defect this closes is older than the step that found it.** Measured 2026-08-04 (R2b):
     * NOT ONE of the thirteen create forms in this application invalidated its list. Every one of
     * them mutates and then navigates to the new record's detail page — products, customers,
     * suppliers, users, roles, VAT classes, units of measure, and all six R2 screens. R2 did not
     * diverge from the pattern; **it copied the pattern faithfully, including the defect.**
     *
     * ⚠️ **It looked intermittent, which is why seven screens shipped with it.** `staleTime` is
     * 30 seconds, so a list revisited within that window is served from cache and never refetched
     * — and a list revisited after it refetches and looks fine. **The bug heals itself in half a
     * minute**, so nobody creating one supplier a week ever saw it. It becomes constant only when
     * somebody creates nineteen document types and thirty series back to back, which is what R2's
     * screens exist for and how it was finally found.
     *
     * **Why global rather than thirteen call sites:** thirteen copies of a line that must never be
     * forgotten is precisely the shape that produced this, and a fourteenth create form would copy
     * it again. Detail screens already invalidate correctly through their own `applyResponse`; the
     * mechanism existed and the create forms simply did not use it.
     *
     * **The two costs, both correctness-neutral and both smaller than they look:**
     *
     * - `/api/me` refetches after a write. In the case that matters — a permission or role change —
     *   it SHOULD.
     * - A detail screen's `setQueryData` is followed by a refetch, discarding the optimisation. It
     *   ends on server truth, which is the better of the two states to land on.
     *
     * Only *active* queries refetch immediately; everything else is marked stale and refetches on
     * its next mount, which is exactly the behaviour a create form needed.
     *
     * ⚠️ **`query-client.test.ts` asserts this handler is present.** A per-screen test cannot: with
     * the fix global, deleting this block leaves every screen test passing and reintroduces the
     * defect in all thirteen at once.
     */
    mutationCache: new MutationCache({
      onSuccess: () => {
        void client.invalidateQueries()
      },
    }),
  })

  return client
}

export function AppQueryProvider({
  children,
  client,
}: {
  children: ReactNode
  client?: QueryClient
}) {
  const queryClient = useMemo(() => client ?? createQueryClient(), [client])
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
