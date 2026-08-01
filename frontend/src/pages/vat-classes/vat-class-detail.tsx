import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import {
  getTaxLookupControllerVatClassQueryKey,
  useTaxLookupControllerDeactivateVatClass,
  useTaxLookupControllerDescribeVatClass,
  useTaxLookupControllerReactivateVatClass,
  useTaxLookupControllerVatClass,
  useTaxLookupControllerVatClasses,
} from '@/api/generated/endpoints/tax-lookup/tax-lookup'
import { Section, type VatClassView } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { unwrapList } from '@/components/data-table/list-response'
import { FieldEditor, UnsetValue } from '@/components/field-editor/field-editor'
import { Refusal } from '@/components/refusal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import { trimRate } from './vat-class-columns'

/**
 * One VAT class.
 *
 * ⚠️ **Exactly one field on this page is editable, and the other two are not editable by anybody on
 * any installation.**
 *
 * | Field | | |
 * |---|---|---|
 * | `description` | editable | `PATCH …/description` |
 * | `ratePercent` | **no route exists** | a rate change is a new class plus a deactivation |
 * | `code` | **no route exists** | it is the class's identity — the ΑΑΔΕ / Go code |
 *
 * The rate and the code render as **plain text**, not through `FieldEditor` with `editable: false`.
 * That distinction is load-bearing and is the same call `RoleDetail` makes about a role's
 * description: in this application `editable: false` means *"not yours to edit"* — a statement about
 * the viewer's grant — and using it here would tell an administrator holding `TAX_AND_CHARGES:FULL`
 * something false. These fields are not anybody's to edit. A **disabled** control would be worse
 * still: it invites a hunt for the permission that unlocks it.
 *
 * **The reduced counterpart is shown and not managed.** The mapping is real, applicable data — this
 * business ships to reduced-VAT islands, so the seeded 24→17, 13→9, 6→4, 4→3 chain is intentional
 * rather than incidental. `PUT`/`DELETE …/reduced-counterpart` exist and are deliberately **not**
 * wired up in F4: which rate an island order is charged at carries statutory weight and is its own
 * decision. `vat-classes.test.tsx` asserts the absence, so building it later is a deliberate act
 * with a test to update.
 */
export function VatClassDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // The route parameter is text. A database id is a count, not a value.
  // eslint-disable-next-line no-restricted-syntax
  const vatClassId = Number(id)

  const query = useTaxLookupControllerVatClass(vatClassId, {
    query: { enabled: Number.isInteger(vatClassId) },
  })
  const vatClass = query.data

  // Every class, so the counterpart's code can be shown instead of its id — including deactivated
  // ones, since a live class may map to a retired one.
  const all = useTaxLookupControllerVatClasses()
  const codeById = new Map(unwrapList(all.data).rows.map((entry) => [entry.id, entry.code] as const))

  const editable = permissions.canEdit(Section.TAX_AND_CHARGES)

  const applyResponse = (updated: VatClassView) => {
    queryClient.setQueryData(getTaxLookupControllerVatClassQueryKey(vatClassId), updated)
    void queryClient.invalidateQueries({ queryKey: ['/api/vat-classes'] })
  }

  const describe = useTaxLookupControllerDescribeVatClass()
  const deactivate = useTaxLookupControllerDeactivateVatClass()
  const reactivate = useTaxLookupControllerReactivateVatClass()

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  if (!vatClass) return <p className="text-muted-foreground text-sm">{t('vatClasses.notFound')}</p>

  const counterpartCode =
    vatClass.reducedCounterpartId === undefined
      ? undefined
      : codeById.get(vatClass.reducedCounterpartId)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link to="/settings/vat-classes" className="text-muted-foreground text-sm hover:underline">
            ← {t('vatClasses.backToList')}
          </Link>
          <h1 className="text-lg font-semibold">{vatClass.code}</h1>
          {vatClass.active === false && (
            <Badge variant="outline">{t('vatClasses.flag.inactive')}</Badge>
          )}
        </div>

        {editable &&
          (vatClass.active === false ? (
            <Button
              size="sm"
              variant="outline"
              disabled={reactivate.isPending}
              onClick={() =>
                reactivate.mutate({ id: vatClassId }, { onSuccess: () => void query.refetch() })
              }
            >
              {t('vatClasses.reactivate')}
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              disabled={deactivate.isPending}
              onClick={() =>
                deactivate.mutate({ id: vatClassId }, { onSuccess: () => void query.refetch() })
              }
            >
              {t('vatClasses.deactivate')}
            </Button>
          ))}
      </div>

      {/* Both mutations above are fire-and-refetch, so their refusals belong here rather than
          beside a field. Without this a refused deactivation changes nothing on screen and the
          button reads as dead — which is the exact defect `Refusal` was extracted for. */}
      <Refusal error={deactivate.error ?? reactivate.error} />

      <Card>
        <CardHeader>
          <CardTitle>{t('vatClasses.detailTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-1">
          <ReadOnlyField
            label={t('vatClasses.column.code')}
            value={vatClass.code}
            reason={t('vatClasses.codeIsIdentity')}
          />

          <ReadOnlyField
            label={t('vatClasses.column.rate')}
            value={
              vatClass.ratePercent === undefined
                ? undefined
                : t('vatClasses.ratePercent', { rate: trimRate(vatClass.ratePercent) })
            }
            reason={t('vatClasses.rateIsPermanent')}
          />

          <FieldEditor<string>
            label={t('vatClasses.column.description')}
            value={vatClass.description ?? ''}
            display={vatClass.description ?? <UnsetValue />}
            editable={editable}
            isValid={(draft) => draft.trim() !== ''}
            onSave={async (draft) => {
              const updated = await describe.mutateAsync({
                id: vatClassId,
                data: { description: draft.trim() },
              })
              applyResponse(updated)
            }}
          >
            {(draft, setDraft) => (
              <Input value={draft} onChange={(event) => setDraft(event.target.value)} />
            )}
          </FieldEditor>

          <ReadOnlyField
            label={t('vatClasses.column.reducedCounterpart')}
            value={counterpartCode}
            reason={t('vatClasses.counterpartNotManaged')}
          />
        </CardContent>
      </Card>
    </div>
  )
}

/**
 * A field no route can change — shown, explained, and with **no affordance at all**.
 *
 * Not `FieldEditor` with `editable: false` (that means "not yours to edit", which is about the
 * viewer) and not `lockedReason` (that means "fixed on this record", which implies it is editable on
 * others). Both would say something untrue here.
 */
function ReadOnlyField({
  label,
  value,
  reason,
}: {
  label: string
  value: string | undefined
  reason: string
}) {
  return (
    <div className="border-b py-2">
      <div className="flex items-baseline justify-between gap-4">
        <Label className="text-muted-foreground w-48 shrink-0 text-sm">{label}</Label>
        <span className="flex-1 text-sm">{value === undefined ? <UnsetValue /> : value}</span>
      </div>
      <p className="text-muted-foreground mt-1 pl-52 text-sm">{reason}</p>
    </div>
  )
}
