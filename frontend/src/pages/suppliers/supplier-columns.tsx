import type { ColumnDef } from '@tanstack/react-table'
import { Link } from 'react-router-dom'
import type { TFunction } from 'i18next'

import type { SupplierView } from '@/api/generated/model'
import { UnsetValue } from '@/components/field-editor/field-editor'
import { Badge } from '@/components/ui/badge'

/**
 * The supplier list's columns.
 *
 * Separated from the screen for the same reason the product columns are: they are where the
 * enum labels and the not-set convention are applied, and both are easier to read and to test on
 * their own.
 */
export function supplierColumns(t: TFunction): ColumnDef<SupplierView, unknown>[] {
  return [
    {
      accessorKey: 'name',
      header: t('suppliers.column.name'),
      cell: ({ row }) => (
        <Link to={`/suppliers/${row.original.id}`} className="font-medium hover:underline">
          {row.original.name}
        </Link>
      ),
    },
    {
      id: 'vatNumber',
      header: t('suppliers.column.vatNumber'),
      cell: ({ row }) =>
        row.original.vatNumber ? (
          <span className="tabular-nums">{row.original.vatNumber}</span>
        ) : (
          <UnsetValue />
        ),
    },
    {
      id: 'vatStatus',
      header: t('vatStatus.label'),
      cell: ({ row }) =>
        row.original.vatStatus ? (
          t(`VatStatus.${row.original.vatStatus}`, { ns: 'enums' })
        ) : (
          <UnsetValue />
        ),
    },
    {
      id: 'email',
      header: t('suppliers.column.email'),
      cell: ({ row }) => row.original.email ?? <UnsetValue />,
    },
    {
      id: 'phone',
      header: t('suppliers.column.phone'),
      cell: ({ row }) => row.original.phone ?? <UnsetValue />,
    },
    {
      id: 'flags',
      header: t('suppliers.column.flags'),
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
