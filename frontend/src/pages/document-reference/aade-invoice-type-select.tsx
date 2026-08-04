import type { TFunction } from 'i18next'

import type { DocumentSide } from '@/api/generated/model'
import { OptionSelect } from '@/components/option-select'

import { aadeOptions, useAadeInvoiceTypes, type AadeInvoiceTypeOption } from './aade-invoice-types-data'
import { NO_AADE_TYPE, idOf } from './values'

/**
 * The AADE invoice-type picker, for a **business** document type's nullable statutory reference.
 *
 * The narrowing, the label rule and the ordering all live in `aade-invoice-types-data.ts`; this file
 * is the control.
 *
 * **No search box, deliberately.** `OptionSelect` is the only way this application builds a select
 * (`frontend/README.md`), and a searchable combobox would be a second select pattern introduced for
 * one field. 34 grouped options is navigable, and this is a picker used nineteen times in total
 * rather than daily.
 */
export function AadeInvoiceTypeSelect({
  t,
  side,
  value,
  onChange,
  disabled,
  id,
  'aria-label': ariaLabel,
}: {
  t: TFunction
  side: DocumentSide
  /** `null` means no statutory code — the ordinary state for six of the owner's nineteen types. */
  value: number | null
  onChange: (value: number | null) => void
  disabled?: boolean
  id?: string
  'aria-label': string
}) {
  const lookup = useAadeInvoiceTypes(side)

  const options: AadeInvoiceTypeOption[] = [
    // ⚠️ Named, never blank. A document type with no AADE code is not an unfilled field — it is an
    // operational document (Προσφορά, Δελτίο Αποστολής, Παραγγελία), which is six of the owner's
    // nineteen. An empty first option would read as "not answered yet".
    { value: NO_AADE_TYPE, label: t('docTypes.aade.none') },
    ...aadeOptions(lookup.items, t),
  ]

  return (
    <OptionSelect
      id={id}
      aria-label={ariaLabel}
      options={options}
      value={value === null ? NO_AADE_TYPE : String(value)}
      onValueChange={(next) =>
        onChange(next === null || next === NO_AADE_TYPE ? null : idOf(next))
      }
      disabled={disabled || !lookup.permitted}
    />
  )
}
