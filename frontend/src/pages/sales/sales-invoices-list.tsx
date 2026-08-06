import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useSalesControllerInvoices } from '@/api/generated/endpoints/sales/sales'
import { Section } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { SearchFilter } from '@/components/data-table/search-filter'
import { useListState } from '@/components/data-table/use-list-state'
import { PlusIcon } from '@/components/icons'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { localIsoDate } from '@/lib/calendar-date'
import { Label } from '@/components/ui/label'

import { salesInvoiceColumns } from './sales-invoice-columns'

/**
 * The first date of the current calendar year, and today — **the range this list opens on**.
 *
 * ⚠️ This screen cannot open on "everything": `GET /api/sales-invoices` answers
 * `400 "a date range needs 'from' and 'to', or name a customerId instead"` when given neither. So a
 * default is not a convenience, it is a precondition, and it had to be chosen rather than defaulted
 * into.
 *
 * **The choice, and the reason recorded with it:** every accounting question this business asks is
 * scoped to a fiscal year, the period lock (D5) is year-shaped, and the list is server-paged so the
 * row count is not the constraint. The range is visible and changeable — it is two ordinary date
 * inputs, not a hidden default somebody has to discover.
 */
function currentYear(): { from: string; to: string } {
  const today = new Date()
  return { from: `${today.getFullYear()}-01-01`, to: localIsoDate(today) }
}


/**
 * Sales invoices — **recorded, never issued**.
 *
 * Novocore does not obtain a ΜΑΡΚ. A document appears here only after it legally exists, issued
 * through Prosvasis Go today and a certified Πάροχος at step 40. Nothing on this screen offers to
 * issue anything; the verb is *record*.
 *
 * ## The first server-paged screen in this application
 *
 * Every list built before this one returns its rows whole and is paged and sorted in the browser.
 * This one is not: the response carries a `page` block, `DataTable` reads that from the generated
 * capability map and switches to `manualSorting`, and only the three columns whose `meta.sortKey`
 * the endpoint declares remain sortable. None of that is configured here — it follows from
 * `useListState('GET /api/sales-invoices')` — which is the point of the machinery S2 built.
 *
 * ## Search reaches fields that are not on the invoice
 *
 * The box sends `search=`, matched against the document number **and** the customer's name and VAT
 * number **and** the series' abbreviation and description. Those last two live on other tables and
 * are reached by subquery rather than by a join, so an invoice with no series — every invoice
 * recorded before R1b — is not silently dropped from its own list. See `TextSearch.matchingRelated`.
 *
 * ⚠️ Before F5 this endpoint **accepted `search=` and ignored it**, returning the full list and
 * looking like it had filtered. That is why the parameter and the screen landed together.
 */
export function SalesInvoicesList() {
  const { t, i18n } = useTranslation('common')
  const permissions = usePermissions()

  const [range, setRange] = useState(currentYear)
  const [search, setSearch] = useState<string | undefined>(undefined)

  const list = useListState('GET /api/sales-invoices')

  const invoices = useSalesControllerInvoices({
    from: range.from,
    to: range.to,
    ...(search ? { search } : {}),
    ...list.params,
  })

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-xl font-semibold">{t('salesInvoices.title')}</h1>

        {permissions.canEdit(Section.SALES) && (
          <Button size="sm" nativeButton={false} render={<Link to="/sales/invoices/new" />}>
            <PlusIcon /> {t('salesInvoices.record')}
          </Button>
        )}
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <SearchFilter
          id="sales-invoice-search"
          onChange={setSearch}
          label={t('salesInvoices.filter.search')}
          placeholder={t('salesInvoices.filter.searchPlaceholder')}
        />
        <div className="space-y-1">
          <Label htmlFor="sales-invoice-from">{t('salesInvoices.filter.from')}</Label>
          <Input
            id="sales-invoice-from"
            type="date"
            value={range.from}
            onChange={(event) =>
              setRange((current) => ({ ...current, from: event.target.value }))
            }
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="sales-invoice-to">{t('salesInvoices.filter.to')}</Label>
          <Input
            id="sales-invoice-to"
            type="date"
            value={range.to}
            onChange={(event) => setRange((current) => ({ ...current, to: event.target.value }))}
          />
        </div>
      </div>

      <DataTable
        data={invoices.data}
        columns={salesInvoiceColumns(t, i18n.language)}
        list={list}
        isLoading={invoices.isLoading}
        emptyMessage={t('salesInvoices.empty')}
        getRowId={(row) => String(row.id)}
      />
    </div>
  )
}
