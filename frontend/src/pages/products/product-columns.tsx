import type { ColumnDef } from '@tanstack/react-table'
import { Link } from 'react-router-dom'
import type { TFunction } from 'i18next'

import { ProtectedField, type ProductView } from '@/api/generated/model'
import { hiddenInResponse } from '@/auth/permissions'
import { HiddenValue, UnsetValue } from '@/components/field-editor/field-editor'
import { Badge } from '@/components/ui/badge'
import { formatMoney, formatUnitCost } from '@/lib/decimal'
import type { Lookup } from '@/api/lookups'
import { nameFor } from '@/api/lookups'

/**
 * The product list's columns.
 *
 * Separated from the screen because they are the first real consumer of three foundations at once —
 * money formatting at two different scales, per-record redaction, and enum labels — and each of
 * those is easier to read, and to test, on its own.
 */
export function productColumns(
  t: TFunction,
  locale: string,
  suppliers: Lookup<{ id?: number; name?: string }>,
): ColumnDef<ProductView, unknown>[] {
  return [
    {
      accessorKey: 'sku',
      header: t('products.column.sku'),
      cell: ({ row }) => (
        <Link to={`/products/${row.original.id}`} className="font-medium hover:underline">
          {row.original.sku}
        </Link>
      ),
    },
    { accessorKey: 'name', header: t('products.column.name') },
    {
      accessorKey: 'type',
      header: t('products.column.type'),
      cell: ({ row }) =>
        row.original.type ? t(`ProductType.${row.original.type}`, { ns: 'enums' }) : <UnsetValue />,
    },
    {
      id: 'unit',
      header: t('products.column.unit'),
      cell: ({ row }) => row.original.unitOfMeasure?.name ?? <UnsetValue />,
    },
    {
      id: 'sellingPrice',
      header: t('products.column.sellingPrice'),
      cell: ({ row }) =>
        row.original.sellingPrice ? (
          <span className="tabular-nums">{formatMoney(row.original.sellingPrice, locale)}</span>
        ) : (
          <UnsetValue />
        ),
    },
    {
      id: 'lastPurchasePrice',
      header: t('products.column.lastPurchasePrice'),
      cell: ({ row }) => {
        // A unit cost, six decimals — NOT money. Rendering it with formatMoney would round
        // 12.505000 to 12.51 and show a number nobody entered.
        if (hiddenInResponse(row.original, ProtectedField.PRODUCT_LAST_PURCHASE_PRICE)) {
          return <HiddenValue />
        }
        return row.original.lastPurchasePrice ? (
          <span className="tabular-nums">
            {formatUnitCost(row.original.lastPurchasePrice, locale)}
          </span>
        ) : (
          <UnsetValue />
        )
      },
    },
    {
      id: 'supplier',
      header: t('products.column.supplier'),
      cell: ({ row }) => {
        // Withheld and unset are different facts and must not look the same.
        if (hiddenInResponse(row.original, ProtectedField.PRODUCT_SUPPLIER)) return <HiddenValue />
        const name = nameFor(suppliers, row.original.supplierId, (supplier) => supplier.name)
        return name ?? <UnsetValue />
      },
    },
    {
      id: 'flags',
      header: t('products.column.flags'),
      cell: ({ row }) => (
        <div className="flex gap-1">
          {row.original.bundle && <Badge variant="secondary">{t('products.flag.bundle')}</Badge>}
          {row.original.serialTracked && (
            <Badge variant="secondary">{t('products.flag.serialTracked')}</Badge>
          )}
          {row.original.active === false && (
            <Badge variant="outline">{t('products.flag.inactive')}</Badge>
          )}
        </div>
      ),
    },
  ]
}
