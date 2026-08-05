import type { ColumnDef } from '@tanstack/react-table'
import type { TFunction } from 'i18next'
import { Link } from 'react-router-dom'

import { SalesInvoiceSort, type SalesInvoiceView } from '@/api/generated/model'
import { sortableHeader } from '@/components/data-table/sortable-header'
import { UnsetValue } from '@/components/field-editor/field-editor'
import { Badge } from '@/components/ui/badge'
import { formatMoney } from '@/lib/decimal'

/**
 * The sales invoice list's columns — **the first in this repository to carry `meta.sortKey`**.
 *
 * ## Why that matters, and what it costs to get wrong
 *
 * `GET /api/sales-invoices` is the first list a screen is built over that **pages on the server**.
 * `DataTable` therefore refuses to sort it in the browser, and correctly: sorting the twenty-five
 * rows in hand and presenting them as the order of four thousand produces a table that looks
 * entirely convincing and answers a different question.
 *
 * So a column here is sortable **only** if its `meta.sortKey` is one the endpoint declares, and it
 * sorts through the request. The three the backend offers are the three values of
 * {@link SalesInvoiceSort}; every other column below is deliberately plain text.
 *
 * ⚠️ **The keys are taken from the generated enum, never typed as strings.** A typo in a literal
 * would not fail anything: `canSortColumn` would simply find the key absent from the endpoint's
 * declared list and quietly render the header as text. The column would stop being sortable and
 * nothing would say so.
 *
 * ## What is NOT sortable, and why that is a decision rather than an omission
 *
 * **Customer** is the one somebody will ask for. It is not offered, and F5 recorded the reason:
 * the endpoint already accepts a `customerId` filter — it is *why* an unfiltered call is refused —
 * so grouping one customer's documents is a filter concern rather than a sort concern. ⚠️ It also
 * carries a cost that is invisible from here: the database orders text by bytes under locale `C`,
 * which puts every Greek name after every Latin one, so a `CUSTOMER_NAME` sort would have to arrive
 * together with `ORDER BY … COLLATE "el-GR-x-icu"`. That obligation belongs with whoever adds the
 * first Greek-bearing server sort key.
 *
 * **Totals** are not sortable either, for the same server-paged reason plus a second one: money is a
 * string on the wire, and there is no `MONEY_TOTAL` sort key to send.
 */
export function salesInvoiceColumns(
  t: TFunction,
  locale: string,
): ColumnDef<SalesInvoiceView, unknown>[] {
  return [
    {
      accessorKey: 'documentNumber',
      header: sortableHeader(t('salesInvoices.column.documentNumber')),
      meta: { sortKey: SalesInvoiceSort.DOCUMENT_NUMBER },
      cell: ({ row }) => (
        <Link
          to={`/sales/invoices/${row.original.id}`}
          className="font-medium hover:underline"
        >
          {row.original.documentNumber}
        </Link>
      ),
    },
    {
      accessorKey: 'invoiceDate',
      header: sortableHeader(t('salesInvoices.column.date')),
      meta: { sortKey: SalesInvoiceSort.INVOICE_DATE },
      cell: ({ row }) => new Date(row.original.invoiceDate).toLocaleDateString(locale),
    },
    {
      id: 'series',
      header: sortableHeader(t('salesInvoices.column.series')),
      // Plain text: the endpoint declares no sort over the series, and a header that sorted one
      // page of many would be worse than one that does not sort at all.
      cell: ({ row }) =>
        // ⚠️ Absent is the ordinary state for every invoice recorded before R1b, not a fault.
        // `series_id` is nullable precisely so migrated history is not given a series nobody
        // authored, so this renders as the same "not set" dash every other unset value uses.
        row.original.seriesAbbreviation ?? <UnsetValue />,
    },
    { accessorKey: 'customerName', header: t('salesInvoices.column.customer') },
    {
      id: 'grossTotal',
      header: t('salesInvoices.column.gross'),
      cell: ({ row }) => (
        <span className="tabular-nums">{formatMoney(row.original.grossTotal, locale)}</span>
      ),
    },
    {
      id: 'state',
      header: t('salesInvoices.column.state'),
      /*
       * Three states from two W1-published booleans, and the order of the checks is the point: a
       * reversing document and a reversed one are different things, and reading `reversed` first
       * would label the reversal itself as reversed.
       */
      cell: ({ row }) => {
        if (row.original.reversal) {
          return <Badge variant="outline">{t('salesInvoices.state.reversal')}</Badge>
        }
        if (row.original.reversed) {
          return <Badge variant="destructive">{t('salesInvoices.state.reversed')}</Badge>
        }
        return <Badge variant="secondary">{t('salesInvoices.state.inForce')}</Badge>
      },
    },
  ]
}
