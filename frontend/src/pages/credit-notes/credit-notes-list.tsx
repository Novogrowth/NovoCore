import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useSalesControllerNotes } from '@/api/generated/endpoints/sales/sales'
import { Section } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { SearchFilter } from '@/components/data-table/search-filter'
import { useListState } from '@/components/data-table/use-list-state'
import { PlusIcon } from '@/components/icons'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { localIsoDate } from '@/lib/calendar-date'

import { creditNoteColumns } from './credit-note-columns'

/**
 * The range this list opens on — 1 January of the current year to today.
 *
 * ⚠️ **The same precondition as the invoice list, arrived at through the same route rather than
 * copied:** `GET /api/credit-notes` calls `requireRange(from)` when neither a `customerId` nor a
 * `salesInvoiceId` is given, and answers `400 "a date range needs 'from' and 'to', or name a
 * customerId instead"`. So a default is not a convenience.
 *
 * ⚠️ **`localIsoDate`, never `toISOString()`.** That is not a style preference: writing the 11 tests
 * over the invoice list found this screen's sibling opening on **31 December**, because
 * `toISOString()` converts local midnight to the previous day in UTC. A calendar date is not an
 * instant.
 */
function currentYear(): { from: string; to: string } {
  const today = new Date()
  return { from: `${today.getFullYear()}-01-01`, to: localIsoDate(today) }
}

/**
 * Credit notes — **recorded, never issued**, exactly as sales invoices are.
 *
 * A credit note is how an issued document is corrected: Greek law has no cancellation of an issued
 * document, so an error in one is answered with a credit invoice. Prosvasis Go issues it, AADE holds
 * it, and this screen reads Novocore's copy.
 *
 * <h2>⚠️ This list is NOT server-paged, and that is a fact about the endpoint</h2>
 *
 * `GET /api/credit-notes` returns its rows whole and declares no sort constants
 * (`{paged: false, sorts: []}`), so `DataTable` pages and sorts in the browser — over the whole
 * list, correctly. The invoice list one directory away does the opposite, and neither screen
 * configures it: both read the generated capability map through `useListState`.
 *
 * **This screen and its detail are PERMANENT PRODUCT.** Only the record form is transitional — see
 * `credit-note-record.tsx`, which says so at length.
 */
export function CreditNotesList() {
  const { t, i18n } = useTranslation('common')
  const permissions = usePermissions()

  const [range, setRange] = useState(currentYear)
  const [search, setSearch] = useState<string | undefined>(undefined)

  const list = useListState('GET /api/credit-notes')

  const notes = useSalesControllerNotes({
    from: range.from,
    to: range.to,
    ...(search ? { search } : {}),
    ...list.params,
  })

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-xl font-semibold">{t('creditNotes.title')}</h1>

        {permissions.canEdit(Section.SALES) && (
          <Button size="sm" nativeButton={false} render={<Link to="/sales/credit-notes/new" />}>
            <PlusIcon /> {t('creditNotes.record')}
          </Button>
        )}
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <SearchFilter
          id="credit-note-search"
          onChange={setSearch}
          label={t('creditNotes.filter.search')}
          placeholder={t('creditNotes.filter.searchPlaceholder')}
        />
        <div className="space-y-1">
          <Label htmlFor="credit-note-from">{t('creditNotes.filter.from')}</Label>
          <Input
            id="credit-note-from"
            type="date"
            value={range.from}
            onChange={(event) => setRange((current) => ({ ...current, from: event.target.value }))}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="credit-note-to">{t('creditNotes.filter.to')}</Label>
          <Input
            id="credit-note-to"
            type="date"
            value={range.to}
            onChange={(event) => setRange((current) => ({ ...current, to: event.target.value }))}
          />
        </div>
      </div>

      <DataTable
        data={notes.data}
        columns={creditNoteColumns(t, i18n.language)}
        list={list}
        isLoading={notes.isLoading}
        emptyMessage={t('creditNotes.empty')}
        getRowId={(row) => String(row.id)}
      />
    </div>
  )
}
