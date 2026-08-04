import type { ColumnDef } from '@tanstack/react-table'
import type { TFunction } from 'i18next'
import { Link } from 'react-router-dom'

import { sortableHeader } from '@/components/data-table/sortable-header'
import { UnsetValue } from '@/components/field-editor/field-editor'
import { Badge } from '@/components/ui/badge'

import { channelLabel, type SeriesView } from './values'

/**
 * The series list's columns.
 *
 * ⚠️ **`showChannel` is the ONLY difference between the sales and purchase lists**, and the
 * purchase side passes `false` because `purchase_document_series` has no channel column at all.
 */
export function seriesColumns(
  t: TFunction,
  basePath: string,
  { showChannel }: { showChannel: boolean },
): ColumnDef<SeriesView, unknown>[] {
  return [
    {
      accessorKey: 'abbreviation',
      header: sortableHeader(t('docSeries.column.abbreviation')),
      cell: ({ row }) => (
        <Link to={`${basePath}/${row.original.id}`} className="font-medium hover:underline">
          {row.original.abbreviation}
        </Link>
      ),
    },
    {
      id: 'description',
      accessorFn: (series) => series.description,
      header: sortableHeader(t('docSeries.column.description')),
      cell: ({ row }) => row.original.description ?? <UnsetValue />,
    },
    {
      id: 'documentTypeDescription',
      accessorFn: (series) => series.documentTypeDescription,
      header: sortableHeader(t('docSeries.column.documentType')),
      cell: ({ row }) => row.original.documentTypeDescription ?? <UnsetValue />,
    },
    ...(showChannel
      ? [
          {
            id: 'channel',
            // Sort by what the cell SHOWS — the translated label, not the constant.
            accessorFn: (series: SeriesView) => channelLabel(t, series.channel),
            header: sortableHeader(t('docSeries.column.channel')),
            cell: ({ row }) => (
              <span className={row.original.channel === undefined ? 'text-muted-foreground' : ''}>
                {channelLabel(t, row.original.channel)}
              </span>
            ),
          } satisfies ColumnDef<SeriesView, unknown>,
        ]
      : []),
    {
      id: 'getsMark',
      accessorFn: (series) => series.getsMark,
      header: sortableHeader(t('docSeries.column.getsMark')),
      cell: ({ row }) => (row.original.getsMark ? t('docSeries.yes') : t('docSeries.no')),
    },
    {
      id: 'flags',
      header: t('docSeries.column.flags'),
      enableSorting: false,
      cell: ({ row }) => (
        <div className="flex gap-1">
          {row.original.inUse && <Badge variant="outline">{t('docSeries.flag.inUse')}</Badge>}
          {row.original.active === false && (
            <Badge variant="outline">{t('docSeries.flag.inactive')}</Badge>
          )}
        </div>
      ),
    },
  ]
}

