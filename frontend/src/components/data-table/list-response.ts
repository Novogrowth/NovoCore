import { pagingCapability } from '@/api/generated/paging'

/**
 * Reading a list response without caring whether the endpoint pages.
 *
 * Today 3 of 56 list endpoints page on the server. The rest return everything, and a table over
 * one of them has to page in the browser. Both shapes arrive as `ListResponse`, and they are told
 * apart by one fact:
 *
 * > **A server-paged response carries a `page` object; an unpaged one omits it** — Jackson's
 * > `non_null` inclusion drops the null rather than sending `"page": null`.
 *
 * So no component needs configuring for this, and no component changes when an endpoint moves
 * from one to the other.
 */

export interface PageInfoLike {
  page?: number
  size?: number
  totalElements?: number
  totalPages?: number
  hasNext?: boolean
  hasPrevious?: boolean
}

export interface ListResponseLike<T> {
  items?: T[]
  page?: PageInfoLike
}

/** Anything a table can be handed: the response, a plain array, or nothing yet. */
export type TableData<T> = readonly T[] | ListResponseLike<T> | undefined

export interface UnwrappedList<T> {
  rows: readonly T[]
  /** Present only when the server did the paging. */
  serverPage: PageInfoLike | undefined
}

/**
 * The empty result, shared.
 *
 * ⚠️ **This constant is load-bearing, and a `[]` literal in its place is a hang.** `useReactTable`
 * memoises the core row model on the *identity* of `data`, and rebuilding it calls
 * `_autoResetPageIndex()` — which sets the page, which re-renders, which would allocate another
 * `[]`, which rebuilds it again. A fresh array here is an unterminating render loop, and because
 * React flushes it in a microtask, the tab never regains the event loop: the in-flight request that
 * would have ended it can never be delivered.
 *
 * Both places below that can be empty return this one array. See `list-response.test.ts`, which
 * asserts the identity rather than the contents.
 */
const NO_ROWS: readonly never[] = []

export function unwrapList<T>(data: TableData<T>): UnwrappedList<T> {
  if (data === undefined) return { rows: NO_ROWS, serverPage: undefined }
  if (Array.isArray(data)) return { rows: data, serverPage: undefined }

  const response = data as ListResponseLike<T>
  return { rows: response.items ?? NO_ROWS, serverPage: response.page }
}

/**
 * Whether a table over this route should let the server page.
 *
 * Answered from the generated capability map rather than from the response, because the question
 * is asked *before* the request — to decide whether to send `page` and `size` at all. Sending them
 * to an endpoint that does not accept them is at best ignored and at worst a 400.
 */
export function isServerPaged(route: string): boolean {
  return pagingCapability(route).paged
}

/** The sort values an endpoint accepts, empty when it does not sort on the server. */
export function serverSorts(route: string): readonly string[] {
  return pagingCapability(route).sorts
}
