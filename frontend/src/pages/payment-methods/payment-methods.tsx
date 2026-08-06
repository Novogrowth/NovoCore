import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import {
  getPaymentMethodControllerPaymentMethodQueryKey,
  usePaymentMethodControllerChangeSortCode,
  usePaymentMethodControllerDeactivate,
  usePaymentMethodControllerDescribe,
  usePaymentMethodControllerPaymentMethod,
  usePaymentMethodControllerPaymentMethods,
  usePaymentMethodControllerReactivate,
} from '@/api/generated/endpoints/payment-method/payment-method'
import { Section, type PaymentMethodView } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { useListState } from '@/components/data-table/use-list-state'
import { FieldEditor, UnsetValue } from '@/components/field-editor/field-editor'
import { WarningCircleIcon } from '@/components/icons'
import { Refusal } from '@/components/refusal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { sortCodeOf } from '@/pages/document-reference/values'

import { paymentMethodColumns } from './payment-method-columns'

/**
 * The business's payment methods.
 *
 * <h2>⚠️ This screen exists because of a SCOPING ERROR, not an implementation gap</h2>
 *
 * The owner's nine-table specification asked for
 * *Τρόποι πληρωμής [ID, abbreviation, description, active/inactive, myDATA code]*. Establishing that
 * `SettlementMethod` is a Java enum was carried into R2's scope as **"nothing to edit"** — so
 * delivery methods got a full CRUD screen and payment methods got nothing, from the same
 * specification, in the same step.
 *
 * **The enum decision was right for the wrong scope.** Adding a payment method genuinely needs code,
 * so **no create** is correct and stays. It never justified no screen.
 *
 * <h2>The seed-only convention, second instance</h2>
 *
 * This is `AadeInvoiceTypesList`'s shape and follows the convention that screen established: **no
 * Add control, a permanent line saying who authors the rows, and an absence test naming the omission
 * as permanent rather than "not yet".**
 *
 * The reason is concrete: a new method needs an `AccountSystemKey`, a `settlesImmediately` and a
 * `subjectToCashLimit`. 📌 The owner's own Go list has **Cheque** and **Foreign bank account**, which
 * are not values of this enum — adding either *is* that code change.
 *
 * <h2>⚠️ The myDATA code is READ, never edited</h2>
 *
 * It lives on the enum and is not a column — see `PaymentMethodView`. It renders as plain text with
 * its reason, `frontend/README.md`'s third field state, exactly as the AADE code does.
 */
const BASE = '/settings/payment-methods'

export function PaymentMethodsList() {
  const { t } = useTranslation('common')
  const [activeOnly, setActiveOnly] = useState(false)
  const list = useListState('GET /api/payment-methods')

  const methods = usePaymentMethodControllerPaymentMethods({
    ...(activeOnly ? { active: true } : {}),
    ...list.params,
  })

  return (
    <div className="space-y-4">
      {/* ⚠️ Permanent, and the reason it is a sentence rather than a missing button: a list with
          no Add control and no explanation reads as unfinished work. */}
      <p className="border-muted-foreground/30 text-muted-foreground flex items-start gap-2 rounded-md border border-dashed px-3 py-2 text-sm">
        <WarningCircleIcon aria-hidden className="mt-0.5 shrink-0" />
        {t('paymentMethods.seedOnlyBanner')}
      </p>

      <div className="flex flex-wrap items-end gap-3">
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input
            type="checkbox"
            checked={activeOnly}
            onChange={(event) => setActiveOnly(event.target.checked)}
          />
          {t('paymentMethods.filter.activeOnly')}
        </label>
      </div>

      <DataTable
        data={methods.data}
        columns={paymentMethodColumns(t)}
        list={list}
        isLoading={methods.isLoading}
        emptyMessage={t('paymentMethods.empty')}
        getRowId={(row: PaymentMethodView) => String(row.id)}
      />
    </div>
  )
}

export function PaymentMethodDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // ⚠️ The enum constant IS the identity — there is no surrogate id, so no Number() here.
  const paymentMethodId = Number(id)

  const query = usePaymentMethodControllerPaymentMethod(paymentMethodId)

  const describe = usePaymentMethodControllerDescribe()
  const sortCode = usePaymentMethodControllerChangeSortCode()
  const deactivate = usePaymentMethodControllerDeactivate()
  const reactivate = usePaymentMethodControllerReactivate()

  const editable = permissions.canEdit(Section.SALES)

  const applyResponse = (updated: PaymentMethodView) => {
    queryClient.setQueryData(
      getPaymentMethodControllerPaymentMethodQueryKey(paymentMethodId),
      updated,
    )
    void queryClient.invalidateQueries({ queryKey: ['/api/payment-methods'] })
  }

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  const row = query.data
  if (!row) return <p className="text-muted-foreground text-sm">{t('paymentMethods.notFound')}</p>

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link to={BASE} className="text-muted-foreground text-sm hover:underline">
            ← {t('paymentMethods.backToList')}
          </Link>
          <h1 className="text-lg font-semibold">{row.abbreviation}</h1>
          {row.active === false && (
            <Badge variant="outline">{t('paymentMethods.flag.inactive')}</Badge>
          )}
        </div>

        {editable &&
          (row.active === false ? (
            <Button
              size="sm"
              variant="outline"
              disabled={reactivate.isPending}
              onClick={() =>
                reactivate.mutate(
                  { id: paymentMethodId },
                  { onSuccess: () => void query.refetch() },
                )
              }
            >
              {t('paymentMethods.reactivate')}
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              disabled={deactivate.isPending}
              onClick={() =>
                deactivate.mutate(
                  { id: paymentMethodId },
                  { onSuccess: () => void query.refetch() },
                )
              }
            >
              {t('paymentMethods.deactivate')}
            </Button>
          ))}
      </div>

      {/* ⚠️ Setting is refused, holding is not — the rule this control turns on. */}
      {editable && row.active !== false && (
        <p className="text-muted-foreground text-sm">{t('paymentMethods.deactivateMeaning')}</p>
      )}

      <Refusal error={deactivate.error ?? reactivate.error} />

      <Card>
        <CardHeader>
          <CardTitle>{t('paymentMethods.detailTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-1">
          <ReadOnlyField
            label={t('paymentMethods.column.method')}
            value={row.aadePaymentMethodDescription}
            reason={t('paymentMethods.methodIsIdentity')}
          />

          <ReadOnlyField
            label={t('paymentMethods.column.abbreviation')}
            value={row.abbreviation}
            reason={t('paymentMethods.abbreviationIsSeeded')}
          />

          <FieldEditor<string>
            label={t('paymentMethods.column.description')}
            value={row.description ?? ''}
            display={row.description ?? <UnsetValue />}
            editable={editable}
            isValid={(value) => value.trim() !== ''}
            onSave={async (value) =>
              applyResponse(
                await describe.mutateAsync({
                  id: paymentMethodId,
                  data: { description: value.trim() },
                }),
              )
            }
          >
            {(value, setValue) => (
              <Input value={value} onChange={(event) => setValue(event.target.value)} />
            )}
          </FieldEditor>

          <FieldEditor<string>
            label={t('paymentMethods.column.sortCode')}
            value={String(row.sortCode)}
            display={String(row.sortCode)}
            editable={editable}
            isValid={(value) => value.trim() !== ''}
            onSave={async (value) =>
              applyResponse(
                await sortCode.mutateAsync({
                  id: paymentMethodId,
                  data: { sortCode: sortCodeOf(value.trim()) },
                }),
              )
            }
          >
            {(value, setValue) => (
              <Input
                inputMode="numeric"
                value={value}
                onChange={(event) => setValue(event.target.value.replace(/[^0-9]/g, ''))}
              />
            )}
          </FieldEditor>

          {/* ⚠️ Read from the ENUM, not stored. Third field state: no route on any installation. */}
          <ReadOnlyField
            label={t('paymentMethods.column.mydataCode')}
            value={
              row.aadePaymentMethodCode === undefined
                ? t('paymentMethods.mydataOpen')
                : String(row.aadePaymentMethodCode)
            }
            reason={t('paymentMethods.mydataFromEnum')}
          />

          <ReadOnlyField
            label={t('paymentMethods.column.behaviour')}
            value={[
              row.settlesImmediately
                ? t('paymentMethods.settlesImmediately')
                : t('paymentMethods.settlesLater'),
              row.subjectToCashLimit ? t('paymentMethods.cashLimited') : null,
            ]
              .filter(Boolean)
              .join(' · ')}
            reason={t('paymentMethods.behaviourFromEnum')}
          />
        </CardContent>
      </Card>
    </div>
  )
}

/** A field no route can change — `VatClassDetail`'s component and the same argument. */
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
