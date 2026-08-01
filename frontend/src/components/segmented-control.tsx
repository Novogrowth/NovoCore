import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { cn } from '@/lib/utils'

/**
 * One choice out of a few, all of them on screen at once.
 *
 * Built for the grant grid, where a row is a section and the choice is `NONE` / `VIEW` / `FULL`. A
 * select would hide two of the three answers behind a click, and the whole value of a grid is that
 * a role's access is readable in one pass without opening anything.
 *
 * **Two things it does that `ToggleGroup` on its own does not, and both are the point.**
 *
 * **It cannot be emptied.** Base UI's toggle group lets the pressed item be pressed again to
 * deselect, which would leave a row answering "neither NONE nor VIEW nor FULL" — a state the API has
 * no way to express. An empty change is ignored, so the control always reports one of its options.
 *
 * **An option can be unavailable, with a reason attached to that option rather than to the
 * control.** This is the {@link FieldEditorProps.lockedReason} distinction applied one level down: a
 * caller holding `VIEW` on Sales may confer `NONE` and `VIEW` there and not `FULL`, and hiding
 * `FULL` would leave an administrator hunting for a level that exists on every other row. It is
 * shown, disabled, and says why.
 *
 * ⚠️ **A `disabledReason` written here is a mirror of a backend rule, and mirrors drift.** Prefer
 * the server's own words wherever the server gets to speak: every one of these refusals is a `422`
 * carrying a full explanation, so the reason for a refusal that *happens* comes from `Refusal`, and
 * these texts exist only to keep the operator from firing a request whose answer is already known.
 */

export interface SegmentedOption<T extends string> {
  value: T
  label: string
  /** Present when this option cannot be chosen here, and why. Rendered disabled, never hidden. */
  disabledReason?: string
}

export interface SegmentedControlProps<T extends string> {
  options: readonly SegmentedOption<T>[]
  value: T
  onValueChange: (value: T) => void
  /** Names the control for a screen reader — a grid of these needs one per row. */
  'aria-label': string
  /** The whole control, not one option: a VIEW grant, or a record nothing may change. */
  disabled?: boolean
  className?: string
}

export function SegmentedControl<T extends string>({
  options,
  value,
  onValueChange,
  'aria-label': ariaLabel,
  disabled = false,
  className,
}: SegmentedControlProps<T>) {
  return (
    <ToggleGroup
      aria-label={ariaLabel}
      spacing={0}
      variant="outline"
      value={[value]}
      disabled={disabled}
      onValueChange={(next) => {
        // Pressing the pressed item empties the group. There is no "no answer" here, so the
        // current one stands.
        const chosen = next.find((candidate) => candidate !== value) as T | undefined
        if (chosen !== undefined) onValueChange(chosen)
      }}
      className={cn('w-fit', className)}
    >
      {options.map((option) => (
        <ToggleGroupItem
          key={option.value}
          value={option.value}
          size="sm"
          disabled={disabled || option.disabledReason !== undefined}
          // The reason is on the control itself as well as beside the row, so it survives being
          // read by a screen reader that never reaches the note.
          {...(option.disabledReason !== undefined ? { title: option.disabledReason } : {})}
        >
          {option.label}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}
