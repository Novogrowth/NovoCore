import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useMemo, type ReactNode } from 'react'

import { ApiError } from '@/api/http'

/**
 * The shared query client.
 *
 * Its own file rather than sitting beside the session hooks, so that editing a hook does not
 * remount the provider during development.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
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
  })
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
