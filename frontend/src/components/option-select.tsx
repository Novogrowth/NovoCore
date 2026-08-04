import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

/**
 * A select over a list of options — **the only way this application builds one.**
 *
 * ⚠️ **Base UI's `Select.Value` renders the raw value unless the root is told the options.** It
 * resolves a label by looking the value up in `Select.Root`'s `items`; with no `items`, it falls
 * back to `String(value)` and renders it without complaint. Nothing warns, and the popup — built
 * from the same list — still shows the right words, so the defect is only visible on the closed
 * trigger. It shipped that way on every select in the application: the create form said
 * *"Unit: 4"* and *"Type: GOODS"* where it meant *"Gram"* and *"Goods"*, and the header showed
 * *"en"* rather than *"English"*.
 *
 * Adding `items` at each call site would have fixed it and left the same trap set for the next
 * screen, because the options would then be written twice — once for `items` and once as the
 * children — and two lists that must agree eventually do not. So the option list is passed **once**
 * and this renders both from it. There is no way to use this component and get the raw value.
 *
 * `components/ui/select.tsx` still exports the primitives, for a select whose items are not a flat
 * list of labels. Anything that reaches for them owes `items` to the root itself.
 */

/** Not exported: a call site builds these with `idOptions`, or writes the literals inline. */
interface Option {
  value: string
  label: string
}

export interface OptionSelectProps {
  options: readonly Option[]
  /** `null` when nothing is chosen — an id is not a value, and the empty state is not `'0'`. */
  value: string | null
  onValueChange: (value: string | null) => void
  /** Wires the trigger to a `<Label htmlFor>`. */
  id?: string
  'aria-label'?: string
  disabled?: boolean
  className?: string
}

export function OptionSelect({
  options,
  value,
  onValueChange,
  id,
  'aria-label': ariaLabel,
  disabled,
  className,
}: OptionSelectProps) {
  return (
    <Select
      // The whole point: this is what lets the trigger show "Gram" instead of "4".
      items={options as { label: string; value: string }[]}
      value={value}
      onValueChange={onValueChange}
      disabled={disabled}
    >
      {/*
        ⚠️ `w-full` overrides `SelectTrigger`'s `w-fit` (R2b §5).

        The trigger sizes to its content by default, so in a constrained column a long option is
        squeezed and `line-clamp-1` cuts it. That bit the AADE invoice-type picker, whose options
        read `code — description · group` and are the longest in the application.

        ⚠️ **What is cut is the END, not the start** — `line-clamp-1` truncates trailing text, so the
        `code —` prefix was never at risk and the *group* suffix was what disappeared. That
        correction matters because the opposite was written into two documents before it was
        checked; see PROGRESS.md, R2b §5.

        A width change only. F10 still owns the general styling sweep.
      */}
      <SelectTrigger id={id} aria-label={ariaLabel} className={className ?? 'w-full'}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
