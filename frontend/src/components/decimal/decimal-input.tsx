import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { Money, Quantity, Rate, UnitCost } from '@/api/generated/model'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { isTypeable, parseTyped, toWire, type WireScale } from '@/lib/decimal'

/**
 * Every decimal field in NovoCore.
 *
 * **Never `<input type="number">`** — ESLint fails the build on one. It parses through a double,
 * so the value it hands back has already lost what money cannot afford to lose; it disagrees with
 * itself across locales about whether the comma is a decimal separator; and it changes value when
 * a mouse wheel passes over it, which on an invoice line is a silent error nobody sees.
 *
 * So: a text input with `inputMode="decimal"` — a numeric keypad on a phone, a plain text field
 * everywhere else — holding exactly what was typed, converting only through `Decimal`.
 */

interface DecimalInputProps {
  /** The canonical wire string, or undefined for an empty field. */
  value: string | undefined
  /** Emits the canonical wire string at this type's scale, or undefined when the field is empty. */
  onValueChange: (value: string | undefined) => void
  scale: WireScale
  /** Refuses anything but a whole number — a unit of measure that cannot be divided. */
  wholeNumbersOnly?: boolean
  disabled?: boolean
  required?: boolean
  id?: string
  name?: string
  className?: string
  'aria-label'?: string
  'aria-describedby'?: string
}

/**
 * What the field shows while it is being edited.
 *
 * Deliberately NOT locale-formatted and deliberately without grouping: a field that reformats as
 * you type fights the person typing, and a grouping separator pasted back in is ambiguous between
 * `1.234` meaning one-and-a-bit and one-thousand-two-hundred-and-thirty-four. Grouping belongs in
 * read-only display, which is what `formatMoney` and its siblings are for.
 */
function displayFor(value: string | undefined): string {
  return value ?? ''
}

export function DecimalInput({
  value,
  onValueChange,
  scale,
  wholeNumbersOnly = false,
  className,
  ...rest
}: DecimalInputProps) {
  const [text, setText] = useState(() => displayFor(value))
  const [focused, setFocused] = useState(false)

  /** What a piece of text means: the value it yields, or nothing when it yields none. */
  const interpret = (candidate: string): string | undefined => {
    const parsed = parseTyped(candidate)
    if (parsed === undefined) return undefined
    if (wholeNumbersOnly && !parsed.isInteger()) return undefined
    return toWire(parsed, scale)
  }

  const holdsUnusableEntry = text.trim() !== '' && interpret(text) === undefined

  /*
   * A value changed from outside — a form reset, a record finishing loading — while this field is
   * not being edited.
   *
   * Adjusted during render rather than in an effect, which is React's own advice for state that
   * derives from props: an effect would render once with stale text, then again with the new
   * text, and the first of those is visible.
   *
   * Two things must not happen. Typing must not be interrupted, hence the focus check. And an
   * entry the field could not interpret must not be erased: it emits `undefined`, which would
   * otherwise come straight back as an instruction to clear the field, deleting what somebody
   * typed and hiding the mistake instead of showing it.
   */
  const [lastValue, setLastValue] = useState(value)
  if (value !== lastValue) {
    setLastValue(value)
    if (!focused && !(value === undefined && holdsUnusableEntry)) {
      setText(displayFor(value))
    }
  }

  const handleChange = (next: string) => {
    // Letters and symbols are refused: no reading of them is a number. Digits and separators are
    // always accepted, even when the result is ambiguous — see `isTypeable`. An ambiguous value
    // stays visible and unusable rather than being quietly reshaped into a different number.
    if (!isTypeable(next)) return
    setText(next)
    onValueChange(interpret(next))
  }

  const handleBlur = () => {
    setFocused(false)
    const canonical = interpret(text)
    if (canonical === undefined) {
      // Left exactly as typed. The field is marked invalid and holds no value, so the mistake is
      // visible and correctable rather than erased.
      onValueChange(undefined)
      return
    }
    // Canonicalise: `12,5` becomes `12.50`, so what is on screen is what will be sent.
    setText(canonical)
    onValueChange(canonical)
  }

  return (
    <Input
      {...rest}
      // Not "number". See the note above; the lint rule enforces it.
      type="text"
      inputMode="decimal"
      autoComplete="off"
      value={text}
      aria-invalid={holdsUnusableEntry || undefined}
      onChange={(event) => handleChange(event.target.value)}
      onFocus={() => setFocused(true)}
      onBlur={handleBlur}
      className={cn('text-right tabular-nums', className)}
    />
  )
}

type FieldProps = Omit<DecimalInputProps, 'value' | 'onValueChange' | 'scale'>

/** An amount of money, at two decimals, with its currency. */
export function MoneyInput({
  value,
  currency,
  onValueChange,
  ...rest
}: FieldProps & {
  value: Money | undefined
  currency: string
  onValueChange: (value: Money | undefined) => void
}) {
  const id = useId()
  return (
    <div className="flex items-center gap-2">
      <DecimalInput
        {...rest}
        id={rest.id ?? id}
        scale="money"
        value={value?.amount}
        onValueChange={(amount) =>
          onValueChange(amount === undefined ? undefined : { amount, currency })
        }
      />
      {/* The currency is shown, never chosen by omission: the API never defaults it. */}
      <span className="text-muted-foreground text-sm">{currency}</span>
    </div>
  )
}

/** A unit cost: the same shape as money, at six decimals rather than two. */
export function UnitCostInput({
  value,
  currency,
  onValueChange,
  ...rest
}: FieldProps & {
  value: UnitCost | undefined
  currency: string
  onValueChange: (value: UnitCost | undefined) => void
}) {
  const id = useId()
  return (
    <div className="flex items-center gap-2">
      <DecimalInput
        {...rest}
        id={rest.id ?? id}
        scale="unitCost"
        value={value?.amount}
        onValueChange={(amount) =>
          onValueChange(amount === undefined ? undefined : { amount, currency })
        }
      />
      <span className="text-muted-foreground text-sm">{currency}</span>
    </div>
  )
}

/**
 * A quantity, at six decimals.
 *
 * `allowFractions` comes from the unit of measure — the backend records per unit whether it can be
 * divided, and a field that lets someone order 2.5 of something sold in whole boxes produces a
 * document the core will refuse.
 */
export function QuantityInput({
  value,
  onValueChange,
  allowFractions = true,
  ...rest
}: FieldProps & {
  value: Quantity | undefined
  onValueChange: (value: Quantity | undefined) => void
  allowFractions?: boolean
}) {
  return (
    <DecimalInput
      {...rest}
      scale="quantity"
      wholeNumbersOnly={!allowFractions}
      value={value}
      onValueChange={onValueChange}
    />
  )
}

/** A percentage rate, at six decimals. 24% is `24.000000`, never `0.24`. */
export function RateInput({
  value,
  onValueChange,
  ...rest
}: FieldProps & {
  value: Rate | undefined
  onValueChange: (value: Rate | undefined) => void
}) {
  const { t } = useTranslation()
  return (
    <div className="flex items-center gap-2">
      <DecimalInput {...rest} scale="rate" value={value} onValueChange={onValueChange} />
      <span className="text-muted-foreground text-sm" aria-label={t('rate.percent', '%')}>
        %
      </span>
    </div>
  )
}
