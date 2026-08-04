import type { ColumnDef } from '@tanstack/react-table'
import type { TFunction } from 'i18next'
import { Link } from 'react-router-dom'

import type { DeliveryMethodView } from '@/api/generated/model'
import { sortableHeader } from '@/components/data-table/sortable-header'
import { UnsetValue } from '@/components/field-editor/field-editor'
import { Badge } from '@/components/ui/badge'

const BASE = '/settings/delivery-methods'

/** The delivery-method list's columns. */
export function deliveryMethodColumns(t: TFunction): ColumnDef<DeliveryMethodView, unknown>[] {
  return [
    {
      accessorKey: 'abbreviation',
      header: sortableHeader(t('deliveryMethods.column.abbreviation')),
      cell: ({ row }) => (
        <Link to={`${BASE}/${row.original.id}`} className="font-medium hover:underline">
          {row.original.abbreviation}
        </Link>
      ),
    },
    {
      id: 'description',
      accessorFn: (method) => method.description,
      header: sortableHeader(t('deliveryMethods.column.description')),
      cell: ({ row }) => row.original.description ?? <UnsetValue />,
    },
    {
      id: 'flags',
      header: t('deliveryMethods.column.flags'),
      enableSorting: false,
      cell: ({ row }) =>
        row.original.active === false ? (
          <Badge variant="outline">{t('deliveryMethods.flag.inactive')}</Badge>
        ) : null,
    },
  ]
}

