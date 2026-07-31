import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { CheckIcon, WarningCircleIcon } from '@/components/icons'
import { Refusal } from '@/components/refusal'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

/**
 * One field, one request.
 *
 * NovoCore's master-data API is shaped as per-field PATCH routes — a product has seven of them, and
 * a customer, supplier and unit of measure are the same — each returning the complete updated
 * record. This component is the single answer to that shape, and every master-data screen after
 * this one uses it.
 *
 * **Nothing here batches.** A form that fires seven PATCHes on submit invents partial-failure
 * states the backend has no transaction to prevent: three succeed, one is refused, three never run,
 * and the operator is left to work out which. One field at a time cannot produce that state.
 *
 * **A refusal belongs to the field that caused it**, and is shown by the same `Refusal` every other
 * refused write in the application uses — it is not a property of field editing, and a second copy
 * of it here is how one of them ends up silently dropping the message.
 */

export interface FieldEditorProps<T> {
  label: string
  /** What the field currently holds, as the record reports it. */
  value: T
  /** Read-only rendering. A withheld or unset value decides its own presentation here. */
  display: ReactNode
  /** The editing control, wired to the draft value. */
  children: (draft: T, setDraft: (value: T) => void) => ReactNode
  /** Sends exactly one request. Resolves when the server has accepted it. */
  onSave: (value: T) => Promise<unknown>
  /** False for a VIEW grant: the field renders with no edit affordance at all. */
  editable: boolean
  /**
   * Why this field cannot be changed **on this record**, however editable the section is.
   *
   * ⚠️ **Deliberately not the same thing as `editable: false`, and the difference is the point.**
   * A VIEW grant means "this is not yours to edit" and gets **no affordance at all** — a disabled
   * button that produces a 403 tells somebody to keep trying. A locked field means "this is
   * editable in general and fixed on this record", which is a fact about the data that the operator
   * cannot discover by looking: the shared retail customer's VAT treatment is fixed at `DOMESTIC`
   * by a CHECK constraint, and hiding the control would leave someone hunting for a setting that
   * exists everywhere else.
   *
   * So the control is shown, disabled, with this reason beside it. `editable: false` still wins:
   * a role that may not edit is told nothing about why the record is special.
   */
  lockedReason?: string
  /** Refuses a save that cannot succeed — an empty required value, an uninterpretable amount. */
  isValid?: (value: T) => boolean
  id?: string
}

export function FieldEditor<T>({
  label,
  value,
  display,
  children,
  onSave,
  editable,
  lockedReason,
  isValid,
  id,
}: FieldEditorProps<T>) {
  const { t } = useTranslation('common')
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<T>(value)
  const [saving, setSaving] = useState(false)
  const [justSaved, setJustSaved] = useState(false)
  // The error itself, not a message built from it: turning one into the other is `Refusal`'s job
  // and is stated in exactly one place.
  const [error, setError] = useState<unknown>()

  const start = () => {
    setDraft(value)
    setError(undefined)
    setJustSaved(false)
    setEditing(true)
  }

  const cancel = () => {
    setEditing(false)
    setError(undefined)
  }

  const save = async () => {
    if (isValid && !isValid(draft)) return
    setSaving(true)
    setError(undefined)
    try {
      await onSave(draft)
      setEditing(false)
      setJustSaved(true)
    } catch (caught) {
      /*
       * The field stays open, holding what was typed. Closing it would discard the operator's
       * input and leave the old value on screen as though nothing had happened — which is how
       * somebody comes to believe a change was saved when it was refused.
       */
      setError(caught)
    } finally {
      setSaving(false)
    }
  }

  if (!editing) {
    const locked = editable && lockedReason !== undefined

    return (
      <div className="border-b py-2">
        <div className="flex items-baseline justify-between gap-4">
          <Label className="text-muted-foreground w-48 shrink-0 text-sm">{label}</Label>
          <div className="flex flex-1 items-baseline gap-2">
            <span className="flex-1 text-sm">{display}</span>
            {justSaved && (
              <span className="text-muted-foreground flex items-center gap-1 text-xs">
                <CheckIcon aria-hidden /> {t('field.saved')}
              </span>
            )}
            {editable && (
              // Shown and disabled rather than absent: the field is editable on every other record
              // of this kind, and a control that vanishes reads as a bug rather than as a rule.
              <Button variant="ghost" size="sm" onClick={start} disabled={locked}>
                {t('field.edit')}
              </Button>
            )}
          </div>
        </div>
        {locked && (
          <p className="text-muted-foreground mt-1 flex items-start gap-1 pl-52 text-sm">
            <WarningCircleIcon aria-hidden className="mt-0.5 shrink-0" />
            {lockedReason}
          </p>
        )}
      </div>
    )
  }

  return (
    <div className={cn('border-b py-2', error !== undefined && 'border-destructive')}>
      <div className="flex items-baseline justify-between gap-4">
        <Label htmlFor={id} className="text-muted-foreground w-48 shrink-0 text-sm">
          {label}
        </Label>
        <div className="flex flex-1 items-center gap-2">
          <div className="flex-1">{children(draft, setDraft)}</div>
          <Button size="sm" onClick={() => void save()} disabled={saving}>
            {saving ? t('field.saving') : t('field.save')}
          </Button>
          <Button variant="ghost" size="sm" onClick={cancel} disabled={saving}>
            {t('field.cancel')}
          </Button>
        </div>
      </div>
      <Refusal error={error} className="mt-1 pl-52" />
    </div>
  )
}

/**
 * A value the current role is not allowed to see.
 *
 * Rendered differently from an empty one on purpose: the backend omits a withheld field entirely
 * (Jackson's `non_null` inclusion), so "absent" means two completely different things — "nobody has
 * set this" and "this is not yours to see" — and a screen that draws them the same way tells the
 * operator something false.
 */
export function HiddenValue() {
  const { t } = useTranslation('common')
  return (
    <span className="text-muted-foreground" title={t('field.hiddenTitle')}>
      —
    </span>
  )
}

/** A value that is genuinely not set. */
export function UnsetValue() {
  return <span className="text-muted-foreground/60">—</span>
}
