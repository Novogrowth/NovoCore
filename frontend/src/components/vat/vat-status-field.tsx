import { useTranslation } from 'react-i18next'

import { VatStatus } from '@/api/generated/model'
import { idOptions, type Lookup } from '@/api/lookups'
import { FieldEditor } from '@/components/field-editor/field-editor'
import { OptionSelect } from '@/components/option-select'
import { NEEDS_EXEMPTION_REASON, NEEDS_VAT_NUMBER } from '@/lib/vat-status'

/**
 * A party's VAT status and its exemption reason, edited together.
 *
 * **Together because the API says so, not as a layout preference.** `PATCH …/vat-status` takes
 * `vatStatus` and `vatExemptionReasonId` in one body for suppliers and customers alike, and the
 * backend refuses a status that requires a reason without one. Two separate editors would offer a
 * combination guaranteed to be refused, and would send back whichever half was not being edited as
 * though somebody had set it.
 *
 * **Shared because suppliers and customers are the same problem.** Extracted out of
 * `pages/suppliers/` before Customers was built rather than after, so the two screens cannot come
 * to disagree about what `EXEMPT` requires — which is what a copy would eventually do.
 *
 * ⚠️ **The rules it applies are mirrored, not fetched.** `VatStatus` arrives as a bare string enum;
 * what each value requires is in `VatStatus.java` and nowhere on the wire. See `lib/vat-status.ts`.
 */

export interface VatStatusValue {
  vatStatus: VatStatus
  vatExemptionReasonId: number | undefined
}

export interface VatStatusFieldProps {
  value: VatStatusValue
  /**
   * The party's current VAT number, which is a **different route** and so cannot be set from here.
   * Needed anyway, because `INTRA_EU_B2B` is refused without one and the operator deserves to know
   * that before pressing Save rather than after.
   */
  vatNumber: string | undefined
  exemptionReasons: Lookup<{ id?: number; description?: string }>
  editable: boolean
  /** Passed straight through: a record whose VAT treatment is fixed says so, disabled, not hidden. */
  lockedReason?: string
  onSave: (value: VatStatusValue) => Promise<unknown>
}

export function VatStatusField({
  value,
  vatNumber,
  exemptionReasons,
  editable,
  lockedReason,
  onSave,
}: VatStatusFieldProps) {
  const { t } = useTranslation('common')
  const { t: tEnum } = useTranslation('enums')

  const hasVatNumber = (vatNumber ?? '') !== ''

  return (
    <FieldEditor<VatStatusValue>
      label={t('vatStatus.label')}
      value={value}
      display={
        <span>
          {tEnum(`VatStatus.${value.vatStatus}`)}
          {NEEDS_EXEMPTION_REASON.has(value.vatStatus) && (
            <span className="text-muted-foreground">
              {' · '}
              {reasonName(exemptionReasons, value.vatExemptionReasonId) ??
                t('vatStatus.noReason')}
            </span>
          )}
        </span>
      }
      // Changing the status needs the reason list to choose from, and that list lives under
      // TAX_AND_CHARGES. Without it the field is read-only rather than pretending otherwise.
      editable={editable && exemptionReasons.permitted}
      {...(lockedReason !== undefined ? { lockedReason } : {})}
      isValid={(draft) =>
        (!NEEDS_EXEMPTION_REASON.has(draft.vatStatus) ||
          draft.vatExemptionReasonId !== undefined) &&
        (!NEEDS_VAT_NUMBER.has(draft.vatStatus) || hasVatNumber)
      }
      onSave={onSave}
    >
      {(draft, setDraft) => {
        const needsReason = NEEDS_EXEMPTION_REASON.has(draft.vatStatus)
        const missingVatNumber = NEEDS_VAT_NUMBER.has(draft.vatStatus) && !hasVatNumber

        return (
          <div className="space-y-2">
            <OptionSelect
              aria-label={t('vatStatus.label')}
              options={Object.values(VatStatus).map((status) => ({
                value: status,
                label: tEnum(`VatStatus.${status}`),
              }))}
              value={draft.vatStatus}
              onValueChange={(next) => {
                const chosen = (next ?? VatStatus.DOMESTIC) as VatStatus
                setDraft({
                  vatStatus: chosen,
                  // A reason belongs to the status that required it. Carrying it onto a status
                  // that does not is how a party ends up filed under an article nobody chose.
                  vatExemptionReasonId: NEEDS_EXEMPTION_REASON.has(chosen)
                    ? draft.vatExemptionReasonId
                    : undefined,
                })
              }}
            />

            {needsReason && (
              <OptionSelect
                aria-label={t('vatStatus.exemptionReason')}
                options={idOptions(exemptionReasons.items, (reason) => reason.description)}
                value={
                  draft.vatExemptionReasonId === undefined
                    ? null
                    : String(draft.vatExemptionReasonId)
                }
                onValueChange={(next) => setDraft({ ...draft, vatExemptionReasonId: idFrom(next) })}
              />
            )}

            {/*
             * Why saving is refused, said before the button is pressed rather than after.
             *
             * `FieldEditor.isValid` blocks the save silently — it returns early with nothing on
             * screen — so a field that relies on it owes the operator the reason in the form
             * itself. A Save button that does nothing and explains nothing is the defect the
             * Products deactivate button shipped with.
             */}
            {needsReason && draft.vatExemptionReasonId === undefined && (
              <p className="text-muted-foreground text-sm">{t('vatStatus.reasonRequired')}</p>
            )}
            {missingVatNumber && (
              <p className="text-muted-foreground text-sm">{t('vatStatus.vatNumberRequired')}</p>
            )}
          </div>
        )
      }}
    </FieldEditor>
  )
}

function reasonName(
  lookup: Lookup<{ id?: number; description?: string }>,
  id: number | undefined,
): string | undefined {
  if (id === undefined) return undefined
  if (!lookup.permitted) return `#${id}`
  return lookup.items.find((reason) => reason.id === id)?.description ?? `#${id}`
}

/** A select's value is text; an id is a count. The rule's documented escape applies. */
function idFrom(value: string | null): number | undefined {
  if (value === null || value === '') return undefined
  // eslint-disable-next-line no-restricted-syntax
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : undefined
}
