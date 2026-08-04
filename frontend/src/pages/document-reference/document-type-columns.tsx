import type { ColumnDef } from '@tanstack/react-table'
import type { TFunction } from 'i18next'
import { Link } from 'react-router-dom'

import { sortableHeader } from '@/components/data-table/sortable-header'
import { Badge } from '@/components/ui/badge'

import { isDraft, type DocumentTypeView } from './values'

/**
 * The document-type list's columns — one definition for the sales and purchase screens alike.
 *
 * ⚠️ **Three states in a stock cell too**: an unanswered flag is not a `no`, and rendering it as
 * one would be the same silent decision the create form refuses to make.
 */
export function documentTypeColumns(
  t: TFunction,
  basePath: string,
): ColumnDef<DocumentTypeView, unknown>[] {
  return [
    {
      accessorKey: 'description',
      header: sortableHeader(t('docTypes.column.description')),
      cell: ({ row }) => (
        <Link to={`${basePath}/${row.original.id}`} className="font-medium hover:underline">
          {row.original.description}
        </Link>
      ),
    },
    {
      id: 'aadeInvoiceTypeCode',
      accessorFn: (type) => type.aadeInvoiceTypeCode,
      header: sortableHeader(t('docTypes.column.aade')),
      // ⚠️ An absent AADE code is a real, ordinary state — an operational document, six of the
      // owner's nineteen — and NOT an unfilled field. Rendering a dash would read as the second.
      cell: ({ row }) =>
        row.original.aadeInvoiceTypeCode ?? (
          <span className="text-muted-foreground">{t('docTypes.aade.none')}</span>
        ),
    },
    {
      id: 'affectsStock',
      accessorFn: (type) => type.affectsStock,
      header: sortableHeader(t('docTypes.column.affectsStock')),
      cell: ({ row }) => stockCell(t, row.original.affectsStock),
    },
    {
      id: 'transfersStock',
      accessorFn: (type) => type.transfersStock,
      header: sortableHeader(t('docTypes.column.transfersStock')),
      cell: ({ row }) => stockCell(t, row.original.transfersStock),
    },
    {
      id: 'requiresMydataTransmission',
      accessorFn: (type) => type.requiresMydataTransmission,
      header: sortableHeader(t('docTypes.column.mydata')),
      cell: ({ row }) =>
        row.original.requiresMydataTransmission ? t('docTypes.yes') : t('docTypes.no'),
    },
    {
      id: 'flags',
      header: t('docTypes.column.flags'),
      enableSorting: false,
      cell: ({ row }) => (
        <div className="flex gap-1">
          {isDraft(row.original) && <Badge variant="outline">{t('docTypes.flag.draft')}</Badge>}
          {row.original.active === false && !isDraft(row.original) && (
            <Badge variant="outline">{t('docTypes.flag.inactive')}</Badge>
          )}
        </div>
      ),
    },
  ]
}


/**
 * ⚠️ Three states in a cell too: an unanswered flag is not a `no`.
 *
 * A plain function rather than a component, deliberately — it holds no state and exists only to
 * keep one cell readable, and a component export here would break Fast Refresh for the module.
 */
function stockCell(t: TFunction, value: boolean | undefined) {
  if (value === undefined) {
    return <Badge variant="outline">{t('docTypes.stock.undecided')}</Badge>
  }
  return value ? t('docTypes.yes') : t('docTypes.no')
}

