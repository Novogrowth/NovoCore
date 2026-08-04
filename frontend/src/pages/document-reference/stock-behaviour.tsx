import type { TFunction } from 'i18next'

import { SegmentedControl, type SegmentedOption } from '@/components/segmented-control'

import type { StockAnswer } from './values'

/**
 * The two stock flags on a document type — ⚠️ **three states, not a checkbox.**
 *
 * The reasoning is in `values.ts` beside {@link StockAnswer}: `null` is not `false`, it is "nobody
 * has answered", and R1b branches document recording on the difference. A type whose stock question
 * is unanswered saves as an **inactive draft**, and the database says the same thing from the other
 * side — `…_active_has_stock_behaviour` — so this control cannot be worked around by a second write
 * path.
 *
 * ⚠️ **`undecided` is offered on CREATE and disabled on DETAIL, and that is the server's shape
 * rather than a preference.** `PUT …/stock-behaviour` takes `StockBehaviourRequest`, whose two
 * components are `@Mandatory Boolean` — so once the question is answered there is no request that
 * unanswers it. `allowUndecided` carries that difference, and the two screens pass different values.
 *
 * It is shown **disabled with the reason** rather than removed, because an option that vanished
 * between the create form and the detail screen leaves somebody hunting for it.
 */
export function StockBehaviourControl({
  t,
  value,
  onChange,
  allowUndecided,
  disabled,
  'aria-label': ariaLabel,
}: {
  t: TFunction
  value: StockAnswer
  onChange: (value: StockAnswer) => void
  /** True on the create form only. */
  allowUndecided: boolean
  disabled?: boolean
  'aria-label': string
}) {
  const options: SegmentedOption<StockAnswer>[] = [
    { value: 'yes', label: t('docTypes.stock.yes') },
    { value: 'no', label: t('docTypes.stock.no') },
    {
      value: 'undecided',
      label: t('docTypes.stock.undecided'),
      ...(allowUndecided ? {} : { disabledReason: t('docTypes.stock.cannotUnanswer') }),
    },
  ]

  return (
    <SegmentedControl
      options={options}
      value={value}
      onValueChange={onChange}
      disabled={disabled}
      aria-label={ariaLabel}
    />
  )
}
