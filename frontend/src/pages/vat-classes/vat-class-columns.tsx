import type { ColumnDef } from '@tanstack/react-table'
import type { TFunction } from 'i18next'
import { Link } from 'react-router-dom'

import type { VatClassView } from '@/api/generated/model'
import { sortableHeader } from '@/components/data-table/sortable-header'
import { UnsetValue } from '@/components/field-editor/field-editor'
import { Badge } from '@/components/ui/badge'
import { compareDecimal } from '@/lib/collation'

/**
 * The VAT class list's columns.
 *
 * ⚠️ **The code is the identity, never the rate.** Nine seeded classes carry **eight** distinct
 * percentages — 4% appears twice, as `1040` in its own right and as `1041`, the island-reduced
 * counterpart of 6% under a different legal basis (αρ.31 ν.5057/2023). So the code column is the
 * link, and nothing anywhere locates a class by its rate. `VatClassService` deliberately offers no
 * lookup by rate for the same reason, and V30 deliberately does not index one.
 */
export function vatClassColumns(
  t: TFunction,
  counterpartCode: (id: number) => string | undefined,
): ColumnDef<VatClassView, unknown>[] {
  return [
    {
      accessorKey: 'code',
      header: sortableHeader(t('vatClasses.column.code')),
      cell: ({ row }) => (
        <Link to={`/settings/vat-classes/${row.original.id}`} className="font-medium hover:underline">
          {row.original.code}
        </Link>
      ),
    },
    {
      id: 'description',
      accessorFn: (vatClass) => vatClass.description,
      header: sortableHeader(t('vatClasses.column.description')),
      cell: ({ row }) => row.original.description ?? <UnsetValue />,
    },
    {
      id: 'ratePercent',
      accessorFn: (vatClass) => vatClass.ratePercent,
      header: sortableHeader(t('vatClasses.column.rate')),
      // A rate is a decimal on the wire as a STRING, so the default text comparator would put
      // "9.000000" above "13.000000". `compareDecimal` is the shared one; nothing here parses.
      sortingFn: (a, b, columnId) =>
        compareDecimal(a.getValue<string | undefined>(columnId), b.getValue<string | undefined>(columnId)),
      cell: ({ row }) =>
        row.original.ratePercent === undefined ? (
          <UnsetValue />
        ) : (
          t('vatClasses.ratePercent', { rate: trimRate(row.original.ratePercent) })
        ),
    },
    {
      id: 'reducedCounterpart',
      accessorFn: (vatClass) =>
        vatClass.reducedCounterpartId === undefined
          ? undefined
          : counterpartCode(vatClass.reducedCounterpartId),
      header: sortableHeader(t('vatClasses.column.reducedCounterpart')),
      /*
       * The island-reduced rate this class maps to, shown as the counterpart's CODE rather than its
       * id — the id is meaningless on screen and the code is what an accountant recognises.
       *
       * Read-only on this screen. The mapping is real, applicable data — this business ships to
       * reduced-VAT islands — but `PUT`/`DELETE …/reduced-counterpart` are deliberately not wired
       * up in F4: changing which rate an island order is charged at carries statutory weight and is
       * its own decision. A test asserts the absence, so building it later is deliberate.
       */
      cell: ({ row }) => {
        if (row.original.reducedCounterpartId === undefined) return <UnsetValue />
        const code = counterpartCode(row.original.reducedCounterpartId)
        return code === undefined ? <UnsetValue /> : <Badge variant="outline">{code}</Badge>
      },
    },
    {
      id: 'flags',
      header: t('vatClasses.column.flags'),
      enableSorting: false,
      cell: ({ row }) =>
        row.original.active === false ? (
          <Badge variant="outline">{t('vatClasses.flag.inactive')}</Badge>
        ) : null,
    },
  ]
}

/**
 * `24.000000` reads as `24`, and `13.500000` as `13.5`.
 *
 * ⚠️ Text manipulation, not arithmetic: the value is a wire decimal and is never turned into a
 * number. `numeric(19,6)` means every rate arrives with six decimal places, and showing six zeroes
 * on every row of a table whose whole content is percentages is noise.
 */
export function trimRate(rate: string): string {
  if (!rate.includes('.')) return rate
  return rate.replace(/0+$/, '').replace(/\.$/, '')
}
