import type { ColumnDef } from '@tanstack/react-table'
import type { TFunction } from 'i18next'
import { Link } from 'react-router-dom'

import type { CreditNoteView } from '@/api/generated/model'
import { sortableHeader } from '@/components/data-table/sortable-header'
import { Badge } from '@/components/ui/badge'
import { compareMoney, compareText } from '@/lib/collation'
import { formatMoney } from '@/lib/decimal'

/**
 * The credit note list's columns.
 *
 * <h2>⚠️ NO `meta.sortKey` ANYWHERE, and the contrast with sales invoices is the point</h2>
 *
 * `GET /api/credit-notes` is `{paged: false, sorts: []}` in the generated capability map: it returns
 * its rows whole and declares no sort constants. So every column below sorts **in the browser**,
 * over the entire list, which is correct — and `canSortColumn` would refuse a `sortKey` here anyway,
 * because the endpoint declares none.
 *
 * ⚠️ **This is the opposite of `sales-invoice-columns.tsx`, which sits one directory away and
 * carries the repository's only three `sortKey`s.** The two files look nearly identical and behave
 * differently, so the reason is written at both: a **server-paged** list must sort through the
 * request, because ordering the twenty-five rows in hand and presenting them as the order of four
 * thousand is convincing and wrong. An **unpaged** list has every row already, so sorting it here
 * answers the question it appears to answer.
 *
 * 📌 So this screen owes no `sortKey` and discharges nothing: it joins the standing obligation
 * counted in `frontend/README.md` — the day `GET /api/credit-notes` gains paging, these controls
 * disappear until somebody adds keys, which is safe and loud.
 *
 * <h2>Ordering by what the cell shows</h2>
 *
 * Text goes through `compareText` (Greek block first, then Latin — the order the database will
 * produce), and money through `compareMoney`, which groups by currency before comparing amounts
 * rather than stating a conversion nobody performed. A wire decimal compared as text puts `"9.00"`
 * above `"1234.56"`.
 */
export function creditNoteColumns(
  t: TFunction,
  locale: string,
): ColumnDef<CreditNoteView, unknown>[] {
  return [
    {
      accessorKey: 'documentNumber',
      header: sortableHeader(t('creditNotes.column.documentNumber')),
      sortingFn: (a, b) => compareText(a.original.documentNumber, b.original.documentNumber),
      cell: ({ row }) => (
        <Link to={`/sales/credit-notes/${row.original.id}`} className="hover:underline">
          {row.original.documentNumber}
        </Link>
      ),
    },
    {
      /*
       * ⭐ The invoice a note corrects, as a link — the one column a credit note has that an invoice
       * does not. A credit note only exists against a sale, which is why the backend files both
       * under one section: somebody reading a note has to be able to reach the sale it corrects.
       */
      accessorKey: 'salesInvoiceNumber',
      header: sortableHeader(t('creditNotes.column.invoice')),
      sortingFn: (a, b) =>
        compareText(a.original.salesInvoiceNumber, b.original.salesInvoiceNumber),
      cell: ({ row }) => (
        <Link
          to={`/sales/invoices/${row.original.salesInvoiceId}`}
          className="hover:underline"
        >
          {row.original.salesInvoiceNumber}
        </Link>
      ),
    },
    {
      accessorKey: 'customerName',
      header: sortableHeader(t('creditNotes.column.customer')),
      sortingFn: (a, b) => compareText(a.original.customerName, b.original.customerName),
    },
    {
      accessorKey: 'creditNoteDate',
      header: sortableHeader(t('creditNotes.column.date')),
      cell: ({ row }) => new Date(row.original.creditNoteDate).toLocaleDateString(locale),
    },
    {
      accessorKey: 'grossTotal',
      header: sortableHeader(t('creditNotes.column.gross')),
      sortingFn: (a, b) => compareMoney(a.original.grossTotal, b.original.grossTotal),
      cell: ({ row }) => (
        <span className="tabular-nums">{formatMoney(row.original.grossTotal, locale)}</span>
      ),
    },
    {
      /*
       * Three states from two booleans, and `reversal` is tested first: a reversing document must be
       * labelled before a reversed one, or a reversal reads as reversed. Both are W1-published
       * derived properties read off the view, not re-derived here.
       */
      id: 'state',
      header: t('creditNotes.column.state'),
      enableSorting: false,
      cell: ({ row }) => {
        if (row.original.reversal) {
          return <Badge variant="outline">{t('creditNotes.state.reversal')}</Badge>
        }
        if (row.original.reversed) {
          return <Badge variant="destructive">{t('creditNotes.state.reversed')}</Badge>
        }
        return <Badge variant="secondary">{t('creditNotes.state.inForce')}</Badge>
      },
    },
  ]
}
