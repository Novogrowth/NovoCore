import type { ColumnDef } from '@tanstack/react-table'
import type { TFunction } from 'i18next'
import { Link } from 'react-router-dom'

import type { PaymentMethodView } from '@/api/generated/model'
import { sortableHeader } from '@/components/data-table/sortable-header'
import { Badge } from '@/components/ui/badge'

const BASE = '/settings/payment-methods'

/**
 * The payment-method list's columns.
 *
 * ⚠️ **The row key is the enum constant, not an id** — there is no surrogate id on this table, by
 * design, because the enum name is the identity and a second identifier would be a second thing to
 * keep in step.
 */
export function paymentMethodColumns(t: TFunction): ColumnDef<PaymentMethodView, unknown>[] {
  return [
    {
      // First: it is what the list is ordered by. Eight options in enum-declaration order is not a
      // sensible list for somebody picking while recording a sale — the reason the column exists.
      accessorKey: 'sortCode',
      header: sortableHeader(t('paymentMethods.column.sortCode')),
      cell: ({ row }) => row.original.sortCode,
    },
    {
      accessorKey: 'abbreviation',
      header: sortableHeader(t('paymentMethods.column.abbreviation')),
      cell: ({ row }) => (
        <Link to={`${BASE}/${row.original.id}`} className="font-medium hover:underline">
          {row.original.abbreviation}
        </Link>
      ),
    },
    {
      id: 'description',
      accessorFn: (method) => method.description,
      header: sortableHeader(t('paymentMethods.column.description')),
      cell: ({ row }) => row.original.description,
    },
    {
      id: 'mydataPaymentCode',
      accessorFn: (method) => method.aadePaymentMethodCode,
      header: sortableHeader(t('paymentMethods.column.mydataCode')),
      // ⚠️ An absent code is genuinely OPEN — AADE's code for ACS cash-on-delivery, PayPal and
      // Stripe has not been established and was deliberately not invented. A dash would read as
      // "nobody filled it in", which is a different and false statement.
      cell: ({ row }) =>
        row.original.aadePaymentMethodCode ?? (
          <Badge variant="outline" title={t('paymentMethods.mydataOpenTitle')}>
            {t('paymentMethods.mydataOpen')}
          </Badge>
        ),
    },
    {
      id: 'flags',
      header: t('paymentMethods.column.flags'),
      enableSorting: false,
      cell: ({ row }) =>
        row.original.active === false ? (
          <Badge variant="outline">{t('paymentMethods.flag.inactive')}</Badge>
        ) : null,
    },
  ]
}
