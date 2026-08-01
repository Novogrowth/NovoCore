import type { ColumnDef } from '@tanstack/react-table'
import { Link } from 'react-router-dom'
import type { TFunction } from 'i18next'

import type { SupplierView } from '@/api/generated/model'
import { sortableHeader } from '@/components/data-table/sortable-header'
import { UnsetValue } from '@/components/field-editor/field-editor'
import { Badge } from '@/components/ui/badge'

/**
 * The supplier list's columns.
 *
 * Separated from the screen for the same reason the product columns are: they are where the
 * enum labels and the not-set convention are applied, and both are easier to read and to test on
 * their own.
 *
 * Every column but Flags sorts, by the text the cell shows — including VAT status, which orders by
 * its translated label rather than by the `VatStatus` constant. See `product-columns.tsx` for why
 * that is the rule and what it implies about switching language.
 */
export function supplierColumns(t: TFunction): ColumnDef<SupplierView, unknown>[] {
  return [
    {
      accessorKey: 'name',
      header: sortableHeader(t('suppliers.column.name')),
      cell: ({ row }) => (
        <Link to={`/suppliers/${row.original.id}`} className="font-medium hover:underline">
          {row.original.name}
        </Link>
      ),
    },
    {
      id: 'vatNumber',
      accessorFn: (supplier) => supplier.vatNumber,
      header: sortableHeader(t('suppliers.column.vatNumber')),
      cell: ({ row }) =>
        row.original.vatNumber ? (
          <span className="tabular-nums">{row.original.vatNumber}</span>
        ) : (
          <UnsetValue />
        ),
    },
    {
      id: 'vatStatus',
      accessorFn: (supplier) =>
        supplier.vatStatus ? t(`VatStatus.${supplier.vatStatus}`, { ns: 'enums' }) : undefined,
      header: sortableHeader(t('vatStatus.label')),
      cell: ({ row }) =>
        row.original.vatStatus ? (
          t(`VatStatus.${row.original.vatStatus}`, { ns: 'enums' })
        ) : (
          <UnsetValue />
        ),
    },
    {
      id: 'email',
      accessorFn: (supplier) => supplier.email,
      header: sortableHeader(t('suppliers.column.email')),
      cell: ({ row }) => row.original.email ?? <UnsetValue />,
    },
    {
      id: 'phone',
      accessorFn: (supplier) => supplier.phone,
      header: sortableHeader(t('suppliers.column.phone')),
      cell: ({ row }) => row.original.phone ?? <UnsetValue />,
    },
    {
      id: 'flags',
      header: t('suppliers.column.flags'),
      enableSorting: false,
      cell: ({ row }) => (
        <div className="flex gap-1">
          {row.original.active === false && (
            <Badge variant="outline">{t('suppliers.flag.inactive')}</Badge>
          )}
        </div>
      ),
    },
  ]
}
