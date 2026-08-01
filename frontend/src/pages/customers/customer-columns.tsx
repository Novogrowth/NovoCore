import type { ColumnDef } from '@tanstack/react-table'
import { Link } from 'react-router-dom'
import type { TFunction } from 'i18next'

import type { CustomerView } from '@/api/generated/model'
import { sortableHeader } from '@/components/data-table/sortable-header'
import { UnsetValue } from '@/components/field-editor/field-editor'
import { Badge } from '@/components/ui/badge'

/**
 * The customer list's columns — the supplier columns plus one that only customers have.
 *
 * The **system** badge is not decoration. `Πελάτης Λιανικής` looks like an ordinary customer in a
 * list of customers, and it is the one row whose VAT treatment is fixed and which cannot be
 * deactivated. Marking it here means the operator learns that before opening it.
 *
 * ⚠️ **This is the screen the collation work is most visible on**, and the reason it was done
 * first: `Πελάτης Λιανικής` sorted below every `TEST-CUSTOMER-*` row under the database's byte
 * order, because the Greek block sits above Latin in code-point order. `@/lib/collation` is what
 * puts it where a reader expects. Sorting by name here is therefore not a new feature so much as
 * the first correct statement of an order the list was already claiming to have.
 */
export function customerColumns(t: TFunction): ColumnDef<CustomerView, unknown>[] {
  return [
    {
      accessorKey: 'name',
      header: sortableHeader(t('customers.column.name')),
      cell: ({ row }) => (
        <Link to={`/customers/${row.original.id}`} className="font-medium hover:underline">
          {row.original.name}
        </Link>
      ),
    },
    {
      id: 'vatNumber',
      accessorFn: (customer) => customer.vatNumber,
      header: sortableHeader(t('customers.column.vatNumber')),
      cell: ({ row }) =>
        row.original.vatNumber ? (
          <span className="tabular-nums">{row.original.vatNumber}</span>
        ) : (
          <UnsetValue />
        ),
    },
    {
      id: 'vatStatus',
      accessorFn: (customer) =>
        customer.vatStatus ? t(`VatStatus.${customer.vatStatus}`, { ns: 'enums' }) : undefined,
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
      accessorFn: (customer) => customer.email,
      header: sortableHeader(t('customers.column.email')),
      cell: ({ row }) => row.original.email ?? <UnsetValue />,
    },
    {
      id: 'phone',
      accessorFn: (customer) => customer.phone,
      header: sortableHeader(t('customers.column.phone')),
      cell: ({ row }) => row.original.phone ?? <UnsetValue />,
    },
    {
      id: 'flags',
      header: t('customers.column.flags'),
      enableSorting: false,
      cell: ({ row }) => (
        <div className="flex gap-1">
          {row.original.systemKey !== undefined && (
            <Badge variant="secondary" title={t('customers.system.badgeTitle')}>
              {t('customers.system.badge')}
            </Badge>
          )}
          {row.original.active === false && (
            <Badge variant="outline">{t('customers.flag.inactive')}</Badge>
          )}
        </div>
      ),
    },
  ]
}
