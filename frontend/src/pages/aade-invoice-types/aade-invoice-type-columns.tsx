import type { ColumnDef } from '@tanstack/react-table'
import type { TFunction } from 'i18next'
import { Link } from 'react-router-dom'

import type { AadeInvoiceTypeView } from '@/api/generated/model'
import { sortableHeader } from '@/components/data-table/sortable-header'
import { UnsetValue } from '@/components/field-editor/field-editor'
import { Badge } from '@/components/ui/badge'

/** Where the codification's rows link to. Kept beside the columns that build the link. */
const BASE = '/settings/aade-invoice-types'

/**
 * The AADE codification's columns.
 *
 * ⚠️ **The group column sorts by its TRANSLATED label**, not by the constant — the standing rule
 * that a column sorts by what the cell shows. A consequence worth knowing rather than discovering:
 * it reorders when the language changes, which is correct.
 */
export function aadeInvoiceTypeColumns(t: TFunction): ColumnDef<AadeInvoiceTypeView, unknown>[] {
  return [
    {
      accessorKey: 'code',
      header: sortableHeader(t('aadeTypes.column.code')),
      cell: ({ row }) => (
        <Link to={`${BASE}/${row.original.id}`} className="font-medium hover:underline">
          {row.original.code}
        </Link>
      ),
    },
    {
      id: 'description',
      accessorFn: (type) => type.description,
      header: sortableHeader(t('aadeTypes.column.description')),
      cell: ({ row }) => row.original.description ?? <UnsetValue />,
    },
    {
      id: 'group',
      // Sort by the translated label, not the constant — what the cell shows.
      accessorFn: (type) => t(`AadeInvoiceGroup.${type.group}`, { ns: 'enums' }),
      header: sortableHeader(t('aadeTypes.column.group')),
      cell: ({ row }) => t(`AadeInvoiceGroup.${row.original.group}`, { ns: 'enums' }),
    },
    {
      id: 'flags',
      header: t('aadeTypes.column.flags'),
      enableSorting: false,
      cell: ({ row }) =>
        row.original.active === false ? (
          <Badge variant="outline">{t('aadeTypes.flag.inactive')}</Badge>
        ) : null,
    },
  ]
}

