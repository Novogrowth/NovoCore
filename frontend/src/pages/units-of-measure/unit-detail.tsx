import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import {
  useUnitOfMeasureControllerChangeFractionalQuantityAllowed,
  useUnitOfMeasureControllerDeactivate,
  useUnitOfMeasureControllerReactivate,
  useUnitOfMeasureControllerRecordMydataCode,
  useUnitOfMeasureControllerRename,
  useUnitOfMeasureControllerUnitsOfMeasure,
} from '@/api/generated/endpoints/unit-of-measure/unit-of-measure'
import { Section } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { unwrapList } from '@/components/data-table/list-response'
import { FieldEditor, UnsetValue } from '@/components/field-editor/field-editor'
import { OptionSelect } from '@/components/option-select'
import { Refusal } from '@/components/refusal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * One unit of measure.
 *
 * ⚠️ **There is no `GET /api/units-of-measure/{id}`** — the surface has a list, a create and five
 * mutations, and no single-record read. So this page finds its row in the unfiltered list rather
 * than fetching it, which is why the query below asks for *everything* including deactivated units:
 * a page reached by URL for a deactivated unit must render it, not report it missing.
 *
 * Four fields, and they are unavailable in three different ways:
 *
 * | Field | | |
 * |---|---|---|
 * | `name` | editable | `PATCH …/name` |
 * | `fractionalQuantityAllowed` | editable | `PATCH …/fractional-quantity` |
 * | `mydataCode` | editable **until set, then frozen** | `lockedReason` |
 * | `code` | **no route exists** | plain text — products refer to it |
 *
 * ⚠️ **The myDATA code is the `lockedReason` case, and it is the first one that is not about a
 * single special record.** Recording a code is allowed exactly once; a second call is refused. It is
 * therefore *"editable in general, fixed on this record"* — which is exactly what `lockedReason`
 * means — so the control is shown, **disabled, with the reason**, rather than hidden. Hiding it
 * would leave somebody hunting for a field every unmapped unit visibly has.
 */
export function UnitDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // The route parameter is text. A database id is a count, not a value.
  // eslint-disable-next-line no-restricted-syntax
  const unitId = Number(id)

  const query = useUnitOfMeasureControllerUnitsOfMeasure()
  const unit = unwrapList(query.data).rows.find((entry) => entry.id === unitId)

  const editable = permissions.canEdit(Section.PRODUCTS)

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['/api/units-of-measure'] })
  }

  const rename = useUnitOfMeasureControllerRename()
  const changeFractional = useUnitOfMeasureControllerChangeFractionalQuantityAllowed()
  const recordMydataCode = useUnitOfMeasureControllerRecordMydataCode()
  const deactivate = useUnitOfMeasureControllerDeactivate()
  const reactivate = useUnitOfMeasureControllerReactivate()

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  if (!unit) return <p className="text-muted-foreground text-sm">{t('units.notFound')}</p>

  const mydataFrozen = unit.mydataCode !== undefined

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link
            to="/settings/units-of-measure"
            className="text-muted-foreground text-sm hover:underline"
          >
            ← {t('units.backToList')}
          </Link>
          <h1 className="text-lg font-semibold">{unit.code}</h1>
          {unit.active === false && <Badge variant="outline">{t('units.flag.inactive')}</Badge>}
        </div>

        {editable &&
          (unit.active === false ? (
            <Button
              size="sm"
              variant="outline"
              disabled={reactivate.isPending}
              onClick={() => reactivate.mutate({ id: unitId }, { onSuccess: () => void refresh() })}
            >
              {t('units.reactivate')}
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              disabled={deactivate.isPending}
              onClick={() => deactivate.mutate({ id: unitId }, { onSuccess: () => void refresh() })}
            >
              {t('units.deactivate')}
            </Button>
          ))}
      </div>

      {/*
       * ⚠️ Deactivation is refused while any product still uses the unit, and the 422 NAMES the
       * products. That message is the whole value of the refusal — "in use" alone leaves somebody
       * searching the catalogue — so it must reach the screen.
       */}
      <Refusal error={deactivate.error ?? reactivate.error} />

      <Card>
        <CardHeader>
          <CardTitle>{t('units.detailTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-1">
          <div className="border-b py-2">
            <div className="flex items-baseline justify-between gap-4">
              <Label className="text-muted-foreground w-48 shrink-0 text-sm">
                {t('units.column.code')}
              </Label>
              <span className="flex-1 text-sm">{unit.code}</span>
            </div>
            {/* No route changes a code, on any installation — so no affordance, not a disabled one. */}
            <p className="text-muted-foreground mt-1 pl-52 text-sm">{t('units.codeIsPermanent')}</p>
          </div>

          <FieldEditor<string>
            label={t('units.column.name')}
            value={unit.name ?? ''}
            display={unit.name ?? <UnsetValue />}
            editable={editable}
            isValid={(draft) => draft.trim() !== ''}
            onSave={async (draft) => {
              await rename.mutateAsync({ id: unitId, data: { name: draft.trim() } })
              await refresh()
            }}
          >
            {(draft, setDraft) => (
              <Input value={draft} onChange={(event) => setDraft(event.target.value)} />
            )}
          </FieldEditor>

          <FieldEditor<string>
            label={t('units.column.fractional')}
            value={unit.fractionalQuantityAllowed ? 'true' : 'false'}
            display={
              unit.fractionalQuantityAllowed
                ? t('units.fractional.allowed')
                : t('units.fractional.wholeOnly')
            }
            editable={editable}
            onSave={async (draft) => {
              // `allowed` is a BOXED Boolean with `Required.field` behind it, so an omitted value is
              // a 400 rather than a silent `false`. Sent explicitly regardless.
              await changeFractional.mutateAsync({
                id: unitId,
                data: { allowed: draft === 'true' },
              })
              await refresh()
            }}
          >
            {(draft, setDraft) => (
              <OptionSelect
                value={draft}
                onValueChange={(chosen) => setDraft(chosen ?? 'false')}
                options={[
                  { value: 'true', label: t('units.fractional.allowed') },
                  { value: 'false', label: t('units.fractional.wholeOnly') },
                ]}
              />
            )}
          </FieldEditor>

          <FieldEditor<string>
            label={t('units.column.mydataCode')}
            value={unit.mydataCode ?? ''}
            display={
              unit.mydataCode ?? (
                <Badge variant="outline" title={t('units.mydataMissingTitle')}>
                  {t('units.mydataMissing')}
                </Badge>
              )
            }
            editable={editable}
            // Editable in general, fixed on this record — the `lockedReason` case, once set.
            {...(mydataFrozen ? { lockedReason: t('units.mydataFrozen') } : {})}
            isValid={(draft) => draft.trim() !== ''}
            onSave={async (draft) => {
              await recordMydataCode.mutateAsync({
                id: unitId,
                data: { mydataCode: draft.trim() },
              })
              await refresh()
            }}
          >
            {(draft, setDraft) => (
              <Input value={draft} onChange={(event) => setDraft(event.target.value)} />
            )}
          </FieldEditor>
        </CardContent>
      </Card>
    </div>
  )
}
