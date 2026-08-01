import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ColumnDef } from '@tanstack/react-table'
import { describe, expect, it, vi } from 'vitest'

import '@/i18n'

import { DataTable } from './data-table'
import { isServerPaged, serverSorts, unwrapList } from './list-response'
import { sortableHeader } from './sortable-header'
import { canSortColumn, moneySorting } from './sorting'
import type { ListStateHandle } from './use-list-state'

/** What a screen passes down from `useListState` when the server does the paging. */
const listHandle = (page: number, sorts: readonly string[] = []): ListStateHandle => ({
  state: { page, size: 25 },
  setPage: vi.fn(),
  setSize: vi.fn(),
  setSort: vi.fn(),
  params: { page, size: 25 },
  serverPaged: true,
  serverSorts: sorts,
})

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
        list={listHandle(0)}
      />,
    )

    // 25 rows in hand, 8 pages according to the server — the table must not conclude there is
    // one page because it can only see one page's worth of data.
    expect(screen.getByText('Page 1 of 8')).toBeInTheDocument()
    expect(screen.getByText('Paged by the server')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /next/i })).toBeEnabled()
  })

  it('asks the list handle for the next page rather than paging what it has', async () => {
    const user = userEvent.setup()
    const list = listHandle(0)
    render(
      <DataTable
        data={{
          items: rows(25),
          page: { page: 0, size: 25, totalElements: 200, totalPages: 8, hasNext: true },
        }}
        columns={columns}
        list={list}
      />,
    )

    await user.click(screen.getByRole('button', { name: /next/i }))
    // The next 25 rows live on the server; the only correct action is to ask for them.
    expect(list.setPage).toHaveBeenCalledWith(1)
  })

  it('trusts hasNext over the row count on the last server page', () => {
    render(
      <DataTable
        data={{
          items: rows(25),
          page: { page: 7, size: 25, totalElements: 200, totalPages: 8, hasNext: false },
        }}
        columns={columns}
        list={listHandle(7)}
      />,
    )
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled()
  })

  it('shows the position but no buttons when nothing owns the page number', () => {
    // A server-paged response with no list handle has nowhere to put the next page number. Buttons
    // here would render enabled and do nothing, which reads as broken rather than as absent.
    render(
      <DataTable
        data={{
          items: rows(25),
          page: { page: 0, size: 25, totalElements: 200, totalPages: 8, hasNext: true },
        }}
        columns={columns}
      />,
    )

    expect(screen.getByText('Page 1 of 8')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /next/i })).not.toBeInTheDocument()
  })
})

/* ------------------------------------------------------------------------------------------- *
 * Sorting
 * ------------------------------------------------------------------------------------------- */

interface Party {
  id: number
  name: string
  phone?: string
  balance?: { amount: string; currency: string }
}

/** Deliberately in an order no correct sort produces, so a passing assertion means work happened. */
const PARTIES: Party[] = [
  { id: 1, name: 'Zebra BV', phone: '210 111', balance: { amount: '9.00', currency: 'EUR' } },
  { id: 2, name: 'Ωμέγα ΑΕ', balance: { amount: '1234.56', currency: 'EUR' } },
  { id: 3, name: 'apple corp', phone: '210 222', balance: { amount: '90.00', currency: 'EUR' } },
  { id: 4, name: 'Άλφα Τεχνική', phone: '210 333' },
]

const partyColumns: ColumnDef<Party, unknown>[] = [
  { accessorKey: 'name', header: sortableHeader('Name'), meta: { sortKey: 'NAME' } },
  { accessorKey: 'phone', header: sortableHeader('Phone') },
  {
    id: 'balance',
    accessorFn: (party) => party.balance,
    sortingFn: moneySorting,
    header: sortableHeader('Balance'),
  },
  { id: 'flags', header: 'Flags', enableSorting: false },
]

/** The rendered order of the name column, which is the first cell of each row. */
const renderedNames = () =>
  screen
    .getAllByRole('row')
    .slice(1)
    .map((row) => row.querySelector('td')?.textContent ?? '')

describe('who is allowed to sort a column', () => {
  it('lets a browser-paged table sort anything the screen marked sortable', () => {
    // Every row is in hand, so sorting them here sorts the list.
    expect(canSortColumn('NAME', false, [])).toBe(true)
    expect(canSortColumn(undefined, false, [])).toBe(true)
  })

  it('refuses a server-paged column the endpoint cannot order by', () => {
    // ⚠️ The case worth having a test for: sorting one page of many and presenting the result as
    // the order of the whole table. Convincing, and wrong.
    expect(canSortColumn(undefined, true, ['ENTRY_DATE'])).toBe(false)
    expect(canSortColumn('NAME', true, ['ENTRY_DATE'])).toBe(false)
    expect(canSortColumn('ENTRY_DATE', true, ['ENTRY_DATE'])).toBe(true)
  })

  it('reads the same capability map the screens do', () => {
    // Not a hand-written fixture: these are the values the generator wrote from the real spec.
    expect(serverSorts('GET /api/journal-entries')).toContain('ENTRY_DATE')
    expect(serverSorts('GET /api/customers')).toEqual([])
  })
})

describe('sorting in the browser', () => {
  it('orders text for a reader rather than by code unit', async () => {
    const user = userEvent.setup()
    render(<DataTable data={PARTIES} columns={partyColumns} />)

    await user.click(screen.getByRole('button', { name: 'Sort by Name, A to Z' }))

    // Greek block first, then Latin — and `apple` ahead of `Zebra`, which byte order reverses.
    expect(renderedNames()).toEqual(['Άλφα Τεχνική', 'Ωμέγα ΑΕ', 'apple corp', 'Zebra BV'])
  })

  it('reverses on the second click and returns to the natural order on the third', async () => {
    const user = userEvent.setup()
    render(<DataTable data={PARTIES} columns={partyColumns} />)

    await user.click(screen.getByRole('button', { name: 'Sort by Name, A to Z' }))
    await user.click(screen.getByRole('button', { name: 'Sort by Name, Z to A' }))
    expect(renderedNames()).toEqual(['Zebra BV', 'apple corp', 'Ωμέγα ΑΕ', 'Άλφα Τεχνική'])

    // The third state is not a courtesy: without it there is no way back to the order the screen
    // opened in, which is the one the backend chose.
    await user.click(screen.getByRole('button', { name: 'Stop sorting by Name' }))
    expect(renderedNames()).toEqual(PARTIES.map((party) => party.name))
  })

  it('keeps "not set" last in both directions', async () => {
    const user = userEvent.setup()
    render(<DataTable data={PARTIES} columns={partyColumns} />)

    await user.click(screen.getByRole('button', { name: 'Sort by Phone, A to Z' }))
    expect(renderedNames().at(-1)).toBe('Ωμέγα ΑΕ')

    await user.click(screen.getByRole('button', { name: 'Sort by Phone, Z to A' }))
    // A descending sort opening on a screen of blanks reads as a broken table.
    expect(renderedNames().at(-1)).toBe('Ωμέγα ΑΕ')
  })

  it('orders money as a number, not as the string it arrived as', async () => {
    const user = userEvent.setup()
    render(<DataTable data={PARTIES} columns={partyColumns} />)

    await user.click(screen.getByRole('button', { name: 'Sort by Balance, A to Z' }))
    // Lexically "1234.56" < "9.00" < "90.00". Numerically it is this, and this is what money means.
    expect(renderedNames()).toEqual(['Zebra BV', 'apple corp', 'Ωμέγα ΑΕ', 'Άλφα Τεχνική'])
  })

  it('starts every column ascending, whatever kind of value it holds', () => {
    // ⚠️ Not cosmetic. TanStack's default reads row zero to choose the first direction — a string
    // ascends, anything else descends — so Balance would open descending, and a column whose first
    // row is empty would change direction when the data did. The header's label follows the
    // direction, so the control would announce a different action from one load to the next.
    render(<DataTable data={PARTIES} columns={partyColumns} />)

    for (const column of ['Name', 'Phone', 'Balance']) {
      expect(screen.getByRole('button', { name: `Sort by ${column}, A to Z` })).toBeInTheDocument()
    }
  })

  it('offers no control on a column that says it does not sort', () => {
    render(<DataTable data={PARTIES} columns={partyColumns} />)
    expect(screen.queryByRole('button', { name: /Flags/ })).not.toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Flags' })).not.toHaveAttribute('aria-sort')
  })

  it('reports the sort state on the header cell, where a screen reader reads it', async () => {
    const user = userEvent.setup()
    render(<DataTable data={PARTIES} columns={partyColumns} />)

    const nameHeader = () => screen.getByRole('columnheader', { name: /Name/ })
    expect(nameHeader()).toHaveAttribute('aria-sort', 'none')

    await user.click(screen.getByRole('button', { name: 'Sort by Name, A to Z' }))
    expect(nameHeader()).toHaveAttribute('aria-sort', 'ascending')

    await user.click(screen.getByRole('button', { name: 'Sort by Name, Z to A' }))
    expect(nameHeader()).toHaveAttribute('aria-sort', 'descending')
  })

  it('goes back to the first page, so the reader sees what they just sorted', async () => {
    const user = userEvent.setup()
    const list: ListStateHandle = {
      state: { page: 3, size: 25 },
      setPage: vi.fn(),
      setSize: vi.fn(),
      setSort: vi.fn(),
      params: {},
      serverPaged: false,
      serverSorts: [],
    }
    render(<DataTable data={PARTIES} columns={partyColumns} list={list} />)

    await user.click(screen.getByRole('button', { name: 'Sort by Name, A to Z' }))
    expect(list.setPage).toHaveBeenCalledWith(0)
  })
})

describe('sorting when the server pages', () => {
  const pagedResponse = {
    items: PARTIES,
    page: { page: 0, size: 25, totalElements: 200, totalPages: 8, hasNext: true },
  }

  it('asks the server rather than reordering the page it holds', async () => {
    const user = userEvent.setup()
    const list = listHandle(0, ['NAME'])
    render(<DataTable data={pagedResponse} columns={partyColumns} list={list} />)

    await user.click(screen.getByRole('button', { name: 'Sort by Name, A to Z' }))

    expect(list.setSort).toHaveBeenCalledWith('NAME', 'ASC')
    // ⚠️ And the rows on screen did NOT move. They are 4 rows out of 200; reordering them would
    // produce a table that looks sorted and is not.
    expect(renderedNames()).toEqual(PARTIES.map((party) => party.name))
  })

  it('sends the direction the second click asks for', async () => {
    const user = userEvent.setup()
    const list = listHandle(0, ['NAME'])
    const { rerender } = render(<DataTable data={pagedResponse} columns={partyColumns} list={list} />)

    // The screen would re-render with the sort now in its state; stand that in.
    const sorted: ListStateHandle = { ...list, state: { page: 0, size: 25, sort: 'NAME', direction: 'ASC' } }
    rerender(<DataTable data={pagedResponse} columns={partyColumns} list={sorted} />)

    expect(screen.getByRole('columnheader', { name: /Name/ })).toHaveAttribute('aria-sort', 'ascending')

    await user.click(screen.getByRole('button', { name: 'Sort by Name, Z to A' }))
    expect(sorted.setSort).toHaveBeenCalledWith('NAME', 'DESC')
  })

  it('takes the control away entirely from a column the endpoint cannot order by', () => {
    // Phone and Balance carry no `sortKey`, so the server has no way to order by them. They render
    // as text rather than as a control that would silently sort one page out of eight.
    render(<DataTable data={pagedResponse} columns={partyColumns} list={listHandle(0, ['NAME'])} />)

    expect(screen.getByRole('button', { name: 'Sort by Name, A to Z' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Phone/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Balance/ })).not.toBeInTheDocument()
  })

  it('takes it away from every column when the endpoint declares no sorts at all', () => {
    render(<DataTable data={pagedResponse} columns={partyColumns} list={listHandle(0)} />)
    expect(screen.queryByRole('button', { name: /Sort by/ })).not.toBeInTheDocument()
  })
})
