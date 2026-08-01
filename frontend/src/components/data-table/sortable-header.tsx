import type { Column, HeaderContext, RowData } from '@tanstack/react-table'
import { useTranslation } from 'react-i18next'

import { CaretDownIcon, CaretUpDownIcon, CaretUpIcon } from '@/components/icons'
import { Button } from '@/components/ui/button'

/**
 * A column header that can be clicked to sort — and that renders as plain text when it cannot.
 *
 * ## One helper, both states, on purpose
 *
 * A column that may not sort is not a column with a disabled button. `frontend/README.md` draws
 * this distinction for `FieldEditor` and it is the same one here: a control that is present and
 * inert invites somebody to keep clicking at something that will never happen. So an unsortable
 * header is **no affordance at all**, and because both states come out of this one function, a
 * column cannot end up with a control that does nothing simply because the endpoint changed
 * underneath it.
 *
 * `column.getCanSort()` is what decides, and `DataTable` sets it from {@link canSortColumn} — so
 * the moment a server-paged endpoint stops offering a sort value, that header goes back to being
 * text without anybody editing a column file.
 *
 * ## Three states, not two
 *
 * Ascending → descending → **unsorted**. The third is not a courtesy: every one of these lists has
 * a natural order the backend chose (customers by name, products by SKU), and without a way back a
 * user who sorted by Phone has no route to the order the screen opened in short of reloading it.
 *
 * ## What a screen reader gets
 *
 * The button is named by the column, so a row of them is a row of distinct controls rather than
 * five things called "Sort" — the same failure the "Edit" buttons had, recorded in
 * `frontend/README.md`. Its `aria-label` also says what the *next* click does, because the visible
 * caret says which way it is sorted now and a label repeating that would leave the actual action
 * unannounced. `aria-sort` on the cell is set by `DataTable`, which owns the `<th>`.
 */
export function SortableHeader<TData extends RowData, TValue>({
  column,
  label,
}: {
  column: Column<TData, TValue>
  label: string
}) {
  const { t } = useTranslation('common')

  if (!column.getCanSort()) return <>{label}</>

  const sorted = column.getIsSorted()

  // What the click will do, which is not the same as what the caret shows. `getNextSortingOrder`
  // is the table's own answer, so this cannot drift from the behaviour of the button.
  const next = column.getNextSortingOrder()
  const action =
    next === 'asc'
      ? t('table.sort.toAscending', { column: label })
      : next === 'desc'
        ? t('table.sort.toDescending', { column: label })
        : t('table.sort.clear', { column: label })

  return (
    <Button
      variant="ghost"
      size="sm"
      className="-ml-2 h-8 data-[sorted=true]:text-foreground"
      data-sorted={sorted !== false}
      aria-label={action}
      onClick={column.getToggleSortingHandler()}
    >
      {label}
      {sorted === 'asc' ? (
        <CaretUpIcon aria-hidden />
      ) : sorted === 'desc' ? (
        <CaretDownIcon aria-hidden />
      ) : (
        <CaretUpDownIcon aria-hidden className="opacity-40" />
      )}
    </Button>
  )
}

/**
 * The `header` for a sortable column, as a one-liner at the column definition.
 *
 * Written this way so a column file stays a list of columns: `header: sortableHeader(t('…'))`
 * rather than an inline component per column, which is how five slightly different headers happen.
 */
export function sortableHeader<TData extends RowData, TValue>(label: string) {
  return function Header({ column }: HeaderContext<TData, TValue>) {
    return <SortableHeader column={column} label={label} />
  }
}
