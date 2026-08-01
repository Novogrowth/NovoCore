import type { ColumnDef } from '@tanstack/react-table'
import { Link } from 'react-router-dom'
import type { TFunction } from 'i18next'

import { ProtectedField, type ProductView } from '@/api/generated/model'
import { hiddenInResponse, type Permissions } from '@/auth/permissions'
import { sortableHeader } from '@/components/data-table/sortable-header'
import { moneySorting } from '@/components/data-table/sorting'
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
 *
 * ## What a sortable column sorts by
 *
 * **The value the cell shows**, not the value underneath it. Type and Unit are the cases that make
 * the difference: sorting on the raw `ProductType` would order by `GOODS`/`SERVICE`, which is
 * neither of the two languages this application is written in, and sorting Unit by
 * `unitOfMeasureId` would order by whatever sequence the units happened to be created in. Both
 * would look arbitrary on screen, and neither would look like a bug.
 *
 * A consequence worth stating rather than discovering: **the order of those columns changes with
 * the language**, because their labels do. That is correct — an alphabetical list is alphabetical
 * in the alphabet being read.
 *
 * ⚠️ **Prices sort with {@link moneySorting}, never as text.** A wire amount is a string, and
 * `"9.00"` sorts above `"1234.56"` under any text comparator.
 */
export function productColumns(
  t: TFunction,
  locale: string,
  suppliers: Lookup<{ id?: number; name?: string }>,
  permissions: Permissions,
): ColumnDef<ProductView, unknown>[] {
  /*
   * ⚠️ **A column whose value this role never receives must not offer a sort.**
   *
   * This is S1's disclosure finding one control along. There, a restricted column was removed from
   * the *search* query rather than merely redacted in the answer, because matching on a value
   * discloses it one character at a time. Ordering does the same thing more efficiently: a sort is
   * a comparison against every other row at once.
   *
   * Client-side the values are simply absent, so the sort could not leak anything today — it would
   * be a control that shuffles nothing, which `frontend/README.md` already argues against in the
   * `FieldEditor` note. Both reasons point the same way, and the first one starts applying the
   * moment these lists sort on the server.
   */
  const notHidden = (field: ProtectedField) => !permissions.isFieldHidden(field)

  return [
    {
      accessorKey: 'sku',
      header: sortableHeader(t('products.column.sku')),
      cell: ({ row }) => (
        <Link to={`/products/${row.original.id}`} className="font-medium hover:underline">
          {row.original.sku}
        </Link>
      ),
    },
    { accessorKey: 'name', header: sortableHeader(t('products.column.name')) },
    {
      id: 'type',
      accessorFn: (product) =>
        product.type ? t(`ProductType.${product.type}`, { ns: 'enums' }) : undefined,
      header: sortableHeader(t('products.column.type')),
      cell: ({ row }) =>
        row.original.type ? t(`ProductType.${row.original.type}`, { ns: 'enums' }) : <UnsetValue />,
    },
    {
      id: 'unit',
      accessorFn: (product) => product.unitOfMeasure?.name,
      header: sortableHeader(t('products.column.unit')),
      cell: ({ row }) => row.original.unitOfMeasure?.name ?? <UnsetValue />,
    },
    {
      id: 'sellingPrice',
      accessorFn: (product) => product.sellingPrice,
      sortingFn: moneySorting,
      header: sortableHeader(t('products.column.sellingPrice')),
      cell: ({ row }) =>
        row.original.sellingPrice ? (
          <span className="tabular-nums">{formatMoney(row.original.sellingPrice, locale)}</span>
        ) : (
          <UnsetValue />
        ),
    },
    {
      id: 'lastPurchasePrice',
      accessorFn: (product) =>
        hiddenInResponse(product, ProtectedField.PRODUCT_LAST_PURCHASE_PRICE)
          ? undefined
          : product.lastPurchasePrice,
      sortingFn: moneySorting,
      enableSorting: notHidden(ProtectedField.PRODUCT_LAST_PURCHASE_PRICE),
      header: sortableHeader(t('products.column.lastPurchasePrice')),
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
      accessorFn: (product) =>
        hiddenInResponse(product, ProtectedField.PRODUCT_SUPPLIER)
          ? undefined
          : nameFor(suppliers, product.supplierId, (supplier) => supplier.name),
      enableSorting: notHidden(ProtectedField.PRODUCT_SUPPLIER),
      header: sortableHeader(t('products.column.supplier')),
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
      // Three independent badges are not an ordering. Sorting by "bundle, then serial-tracked,
      // then inactive" would be a rule invented here that nothing on the screen explains.
      enableSorting: false,
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
