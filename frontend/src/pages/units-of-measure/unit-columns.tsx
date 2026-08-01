import type { ColumnDef } from '@tanstack/react-table'
import type { TFunction } from 'i18next'
import { Link } from 'react-router-dom'

import type { UnitOfMeasureView } from '@/api/generated/model'
import { sortableHeader } from '@/components/data-table/sortable-header'
import { UnsetValue } from '@/components/field-editor/field-editor'
import { Badge } from '@/components/ui/badge'

/**
 * The unit-of-measure list's columns.
 *
 * ⚠️ **An absent myDATA code means "no mapping exists", not "not filled in yet"** — and today every
 * seeded unit is in that state, because the verified ΑΑΔΕ list has not been supplied. It is drawn as
 * a badge rather than a dash so the column reads as an outstanding item rather than as an empty
 * optional field, which is what it actually is: phase 7 cannot transmit an invoice line whose unit
 * has no code.
 */
export function unitColumns(t: TFunction): ColumnDef<UnitOfMeasureView, unknown>[] {
  return [
    {
      accessorKey: 'code',
      header: sortableHeader(t('units.column.code')),
      cell: ({ row }) => (
        <Link
          to={`/settings/units-of-measure/${row.original.id}`}
          className="font-medium hover:underline"
        >
          {row.original.code}
        </Link>
      ),
    },
    {
      id: 'name',
      accessorFn: (unit) => unit.name,
      header: sortableHeader(t('units.column.name')),
      cell: ({ row }) => row.original.name ?? <UnsetValue />,
    },
    {
      id: 'fractionalQuantityAllowed',
      accessorFn: (unit) => unit.fractionalQuantityAllowed,
      header: sortableHeader(t('units.column.fractional')),
      cell: ({ row }) =>
        row.original.fractionalQuantityAllowed
          ? t('units.fractional.allowed')
          : t('units.fractional.wholeOnly'),
    },
    {
      id: 'mydataCode',
      accessorFn: (unit) => unit.mydataCode,
      header: sortableHeader(t('units.column.mydataCode')),
      cell: ({ row }) =>
        row.original.mydataCode ?? (
          <Badge variant="outline" title={t('units.mydataMissingTitle')}>
            {t('units.mydataMissing')}
          </Badge>
        ),
    },
    {
      id: 'flags',
      header: t('units.column.flags'),
      enableSorting: false,
      cell: ({ row }) =>
        row.original.active === false ? (
          <Badge variant="outline">{t('units.flag.inactive')}</Badge>
        ) : null,
    },
  ]
}
