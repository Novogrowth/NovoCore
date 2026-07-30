import { useId, useState } from 'react'

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

/** A decimal field with something written after it — a currency code, a percent sign. */
function WithSuffix({ suffix, children }: { suffix: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-2">
      {children}
      <span className="text-muted-foreground text-sm">{suffix}</span>
    </div>
  )
}

/**
 * An amount and its currency.
 *
 * `MoneyInput` and `UnitCostInput` were 45 duplicated lines differing in one word. They are one
 * component now, with the scale — the only real difference — passed in, because two copies of a
 * money field are two places for a money field to be fixed.
 */
function AmountInput({
  value,
  currency,
  onValueChange,
  scale,
  ...rest
}: FieldProps & {
  value: Money | UnitCost | undefined
  currency: string
  onValueChange: (value: { amount: string; currency: string } | undefined) => void
  scale: 'money' | 'unitCost'
}) {
  const generatedId = useId()
  return (
    // The currency is shown, never chosen by omission: the API never defaults it.
    <WithSuffix suffix={currency}>
      <DecimalInput
        {...rest}
        id={rest.id ?? generatedId}
        scale={scale}
        value={value?.amount}
        onValueChange={(amount) =>
          onValueChange(amount === undefined ? undefined : { amount, currency })
        }
      />
    </WithSuffix>
  )
}

/** An amount of money, at two decimals, with its currency. */
export function MoneyInput(
  props: FieldProps & {
    value: Money | undefined
    currency: string
    onValueChange: (value: Money | undefined) => void
  },
) {
  return <AmountInput {...props} scale="money" />
}

/** A unit cost: the same shape as money, at six decimals rather than two. */
export function UnitCostInput(
  props: FieldProps & {
    value: UnitCost | undefined
    currency: string
    onValueChange: (value: UnitCost | undefined) => void
  },
) {
  return <AmountInput {...props} scale="unitCost" />
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
  // No translation key: the per-cent sign is the same character in both languages, and the key
  // this once looked up existed in neither locale file, so it always fell through to the literal.
  return (
    <WithSuffix suffix="%">
      <DecimalInput {...rest} scale="rate" value={value} onValueChange={onValueChange} />
    </WithSuffix>
  )
}
