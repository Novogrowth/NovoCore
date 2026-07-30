import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ColumnDef } from '@tanstack/react-table'
import { describe, expect, it } from 'vitest'

import '@/i18n'

import { DataTable } from './data-table'
import { isServerPaged, unwrapList } from './list-response'

interface Row {
  id: number
  name: string
}

const columns: ColumnDef<Row, unknown>[] = [
  { accessorKey: 'id', header: 'Id' },
  { accessorKey: 'name', header: 'Name' },
]

const rows = (count: number): Row[] =>
  Array.from({ length: count }, (_, index) => ({ id: index + 1, name: `Row ${index + 1}` }))

describe('reading a list response', () => {
  it('takes a plain array', () => {
    expect(unwrapList(rows(2)).rows).toHaveLength(2)
    expect(unwrapList(rows(2)).serverPage).toBeUndefined()
  })

  it('takes an unpaged ListResponse — no page object, because Jackson drops the null', () => {
    const unwrapped = unwrapList({ items: rows(3) })
    expect(unwrapped.rows).toHaveLength(3)
    expect(unwrapped.serverPage).toBeUndefined()
  })

  it('takes a server-paged ListResponse', () => {
    const unwrapped = unwrapList({
      items: rows(2),
      page: { page: 1, size: 2, totalElements: 9, totalPages: 5, hasNext: true, hasPrevious: true },
    })
    expect(unwrapped.rows).toHaveLength(2)
    expect(unwrapped.serverPage?.totalPages).toBe(5)
  })

  it('survives nothing at all', () => {
    expect(unwrapList(undefined).rows).toEqual([])
  })
})

describe('which endpoints page on the server', () => {
  it('knows the three that do today', () => {
    expect(isServerPaged('GET /api/sales-invoices')).toBe(true)
    expect(isServerPaged('GET /api/journal-entries')).toBe(true)
    expect(isServerPaged('GET /api/accounts/{id}/ledger')).toBe(true)
  })

  it('knows the ones that do not, including the tier-A candidates', () => {
    // Both are named for paging on the backend and have not had it yet. When they do, the map
    // regenerates and every table over them switches with no component change.
    expect(isServerPaged('GET /api/inventory/lots')).toBe(false)
    expect(isServerPaged('GET /api/email/outbox')).toBe(false)
    expect(isServerPaged('GET /api/products')).toBe(false)
  })

  it('treats an unknown route as client-paged rather than failing', () => {
    expect(isServerPaged('GET /api/not-a-route')).toBe(false)
  })
})

describe('DataTable', () => {
  it('renders rows from a plain array', () => {
    render(<DataTable data={rows(3)} columns={columns} />)
    expect(screen.getByText('Row 1')).toBeInTheDocument()
    expect(screen.getByText('Row 3')).toBeInTheDocument()
  })

  it('says so when there is nothing', () => {
    render(<DataTable data={[]} columns={columns} />)
    expect(screen.getByText('Nothing to show.')).toBeInTheDocument()
  })

  it('pages in the browser when the endpoint did not page', async () => {
    const user = userEvent.setup()
    render(<DataTable data={{ items: rows(60) }} columns={columns} />)

    expect(screen.getByText('Row 1')).toBeInTheDocument()
    expect(screen.queryByText('Row 26')).not.toBeInTheDocument()
    expect(screen.getByText('Paged in the browser')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /next/i }))
    expect(screen.getByText('Row 26')).toBeInTheDocument()
  })

  it('defers to the server when the response says the server paged', () => {
    render(
      <DataTable
        data={{
          items: rows(25),
          page: { page: 0, size: 25, totalElements: 200, totalPages: 8, hasNext: true },
        }}
        columns={columns}
      />,
    )

    // 25 rows in hand, 8 pages according to the server — the table must not conclude there is
    // one page because it can only see one page's worth of data.
    expect(screen.getByText('Page 1 of 8')).toBeInTheDocument()
    expect(screen.getByText('Paged by the server')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /next/i })).toBeEnabled()
  })

  it('trusts hasNext over the row count on the last server page', () => {
    render(
      <DataTable
        data={{
          items: rows(25),
          page: { page: 7, size: 25, totalElements: 200, totalPages: 8, hasNext: false },
        }}
        columns={columns}
      />,
    )
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled()
  })
})
