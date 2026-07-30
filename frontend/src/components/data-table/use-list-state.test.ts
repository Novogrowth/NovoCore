import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { useListState } from './use-list-state'

/**
 * The hook that decides what a list screen actually sends.
 *
 * This is the mechanism behind "an endpoint gains server-side paging and every table over it
 * follows without being touched": it reads the generated capability map, and either sends
 * `page`/`size`/`sort` or sends nothing at all. Getting it wrong in the quiet direction — sending
 * paging parameters to an endpoint that never declared them — is a 400 on a screen that worked
 * yesterday.
 */
describe('useListState', () => {
  it('sends nothing to an endpoint that does not page', () => {
    // 53 of 56 list endpoints today. The parameters must be absent, not zero: `page=0&size=25` on
    // a route with no such parameters is at best ignored and at worst refused.
    const { result } = renderHook(() => useListState('GET /api/products'))

    expect(result.current.serverPaged).toBe(false)
    expect(result.current.params).toEqual({})
  })

  it('sends page and size to an endpoint that pages', () => {
    const { result } = renderHook(() => useListState('GET /api/sales-invoices'))

    expect(result.current.serverPaged).toBe(true)
    expect(result.current.params).toEqual({ page: 0, size: 25 })
  })

  it('sends a sort value the endpoint declares, with a direction', () => {
    const { result } = renderHook(() => useListState('GET /api/sales-invoices'))

    act(() => result.current.setSort('DOCUMENT_NUMBER'))

    expect(result.current.params).toMatchObject({ sort: 'DOCUMENT_NUMBER', direction: 'ASC' })
  })

  it('refuses to send a sort value the endpoint does not declare', () => {
    // `SalesInvoiceSort` has three values and this is not one of them. Sending it would be a 400;
    // the generated map is what knows the difference, so the filter reads from it rather than from
    // a list maintained here.
    const { result } = renderHook(() => useListState('GET /api/sales-invoices'))

    act(() => result.current.setSort('ENTRY_DATE'))

    expect(result.current.params).not.toHaveProperty('sort')
    expect(result.current.params).not.toHaveProperty('direction')
  })

  it('goes back to the first page when the page size changes', () => {
    // Page 7 of 8 at 25 rows is past the end at 100. Every other answer is wrong for some dataset.
    const { result } = renderHook(() => useListState('GET /api/sales-invoices'))

    act(() => result.current.setPage(7))
    expect(result.current.state.page).toBe(7)

    act(() => result.current.setSize(100))
    expect(result.current.state).toMatchObject({ page: 0, size: 100 })
  })

  it('goes back to the first page when the sort changes', () => {
    const { result } = renderHook(() => useListState('GET /api/journal-entries'))

    act(() => result.current.setPage(3))
    act(() => result.current.setSort('ENTRY_DATE', 'DESC'))

    expect(result.current.state).toMatchObject({ page: 0, sort: 'ENTRY_DATE', direction: 'DESC' })
  })

  it('accepts an initial page and size', () => {
    const { result } = renderHook(() =>
      useListState('GET /api/sales-invoices', { page: 2, size: 50 }),
    )
    expect(result.current.params).toEqual({ page: 2, size: 50 })
  })

  it('treats an unknown route as unpaged rather than failing', () => {
    const { result } = renderHook(() => useListState('GET /api/not-a-route'))
    expect(result.current.params).toEqual({})
  })
})
