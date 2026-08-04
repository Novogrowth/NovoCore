import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import {
  getAadeInvoiceTypeControllerInvoiceTypeQueryKey,
  useAadeInvoiceTypeControllerDeactivate,
  useAadeInvoiceTypeControllerDescribe,
  useAadeInvoiceTypeControllerInvoiceType,
  useAadeInvoiceTypeControllerInvoiceTypes,
  useAadeInvoiceTypeControllerReactivate,
} from '@/api/generated/endpoints/aade-invoice-type/aade-invoice-type'
import { Section, type AadeInvoiceTypeView } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { useListState } from '@/components/data-table/use-list-state'

import { aadeInvoiceTypeColumns } from './aade-invoice-type-columns'
import { FieldEditor, UnsetValue } from '@/components/field-editor/field-editor'
import { WarningCircleIcon } from '@/components/icons'
import { Refusal } from '@/components/refusal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * The AADE myDATA invoice-type codification — **55 rows that nobody here may add to.**
 *
 * <h2>⚠️ There is no create control, and there never will be</h2>
 *
 * This is a `StatutoryCodification`: **AADE authors the rows and Flyway writes them.** The only
 * operations are activate, deactivate and describe — there is no `POST` on the backend either, and
 * `StatutoryCodificationRulesTest` makes the absence of a create path a build failure rather than a
 * convention. If AADE publishes a new code, that is a migration with the artefact it was read from
 * sitting beside it: **a code typed into a form is transmitted to the tax authority, so a wrong one
 * is a compliance defect rather than a data-entry mistake.**
 *
 * **⚠️ The omission is stated on screen, not merely performed.** A list with no Add button and no
 * explanation reads as a screen somebody has not finished. So there is a permanent line at the top
 * saying who authors these rows — the `UnitsList` banner pattern — and
 * `aade-invoice-types.test.tsx` asserts that **no create control renders**, with the reason in the
 * test's own name.
 *
 * **This is the convention's first instance.** A future seed-only screen — `/api/vat-exemption-reasons`
 * is the obvious next one, and has no screen at all today — copies these three things: no Add
 * button, a permanent explanatory line, and an absence test that says *permanent* rather than
 * *not yet*. The distinction matters, because `frontend/README.md`'s fourth field state ("not built
 * yet") also owes an absence test, and the two would otherwise be indistinguishable in a diff.
 *
 * <h2>⚠️ This is NOT the business's document type list</h2>
 *
 * That is `/settings/sales-document-types` and `/settings/purchase-document-types` — the owner's
 * own, user-creatable, full CRUD, pointing *at* this one through a nullable reference. Six of his
 * nineteen types have no row here at all.
 *
 * Governed by `TAX_AND_CHARGES` rather than Sales or Purchasing: it is a tax authority's list, like
 * VAT exemption reasons, and **both** a sales and a purchase document form read it — so neither of
 * those sections could hold it without the other losing access.
 */
const BASE = '/settings/aade-invoice-types'

export function AadeInvoiceTypesList() {
  const { t } = useTranslation('common')
  const [activeOnly, setActiveOnly] = useState(false)
  const list = useListState('GET /api/aade-invoice-types')

  const types = useAadeInvoiceTypeControllerInvoiceTypes({
    ...(activeOnly ? { active: true } : {}),
    ...list.params,
  })

  return (
    <div className="space-y-4">
      {/*
        ⚠️ PERMANENT, and the reason it is a sentence rather than a missing button.
        A list with no Add control and no explanation reads as unfinished work. This says who
        authors the rows, so the absence is legible as a decision. A test asserts the Add control
        is absent; nothing asserts this paragraph, which is why it says what it says.
      */}
      <p className="border-muted-foreground/30 text-muted-foreground flex items-start gap-2 rounded-md border border-dashed px-3 py-2 text-sm">
        <WarningCircleIcon aria-hidden className="mt-0.5 shrink-0" />
        {t('aadeTypes.seedOnlyBanner')}
      </p>

      <div className="flex flex-wrap items-end gap-3">
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input
            type="checkbox"
            checked={activeOnly}
            onChange={(event) => setActiveOnly(event.target.checked)}
          />
          {t('aadeTypes.filter.activeOnly')}
        </label>
      </div>

      <DataTable
        data={types.data}
        columns={aadeInvoiceTypeColumns(t)}
        list={list}
        isLoading={types.isLoading}
        emptyMessage={t('aadeTypes.empty')}
        getRowId={(row: AadeInvoiceTypeView) => String(row.id)}
      />
    </div>
  )
}

export function AadeInvoiceTypeDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // The route parameter is text. A database id is a count, not a value.
  // eslint-disable-next-line no-restricted-syntax
  const typeId = Number(id)

  const query = useAadeInvoiceTypeControllerInvoiceType(typeId, {
    query: { enabled: Number.isInteger(typeId) },
  })

  const describe = useAadeInvoiceTypeControllerDescribe()
  const deactivate = useAadeInvoiceTypeControllerDeactivate()
  const reactivate = useAadeInvoiceTypeControllerReactivate()

  const editable = permissions.canEdit(Section.TAX_AND_CHARGES)

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  const type = query.data
  if (!type) return <p className="text-muted-foreground text-sm">{t('aadeTypes.notFound')}</p>

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link to={BASE} className="text-muted-foreground text-sm hover:underline">
            ← {t('aadeTypes.backToList')}
          </Link>
          <h1 className="text-lg font-semibold">{type.code}</h1>
          {type.active === false && (
            <Badge variant="outline">{t('aadeTypes.flag.inactive')}</Badge>
          )}
        </div>

        {editable &&
          (type.active === false ? (
            <Button
              size="sm"
              variant="outline"
              disabled={reactivate.isPending}
              onClick={() =>
                reactivate.mutate({ id: typeId }, { onSuccess: () => void query.refetch() })
              }
            >
              {t('aadeTypes.reactivate')}
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              disabled={deactivate.isPending}
              onClick={() =>
                deactivate.mutate({ id: typeId }, { onSuccess: () => void query.refetch() })
              }
            >
              {t('aadeTypes.deactivate')}
            </Button>
          ))}
      </div>

      <Refusal error={deactivate.error ?? reactivate.error} />

      <Card>
        <CardHeader>
          <CardTitle>{t('aadeTypes.detailTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-1">
          {/*
            ⚠️ The code is `frontend/README.md`'s THIRD state — no route exists on any installation
            — so it is plain text with the reason, and NOT a `FieldEditor` with `editable: false`
            (which means "not yours", a statement about the viewer) and NOT a disabled control
            (which invites a hunt for the permission that unlocks it). `VatClassDetail` made this
            call first for a VAT class's code and rate.
          */}
          <ReadOnlyField
            label={t('aadeTypes.column.code')}
            value={type.code}
            reason={t('aadeTypes.codeIsIdentity')}
          />

          <ReadOnlyField
            label={t('aadeTypes.column.group')}
            value={t(`AadeInvoiceGroup.${type.group}`, { ns: 'enums' })}
            reason={t('aadeTypes.groupIsAnnex')}
          />

          <FieldEditor<string>
            label={t('aadeTypes.column.description')}
            value={type.description ?? ''}
            display={type.description ?? <UnsetValue />}
            editable={editable}
            isValid={(value) => value.trim() !== ''}
            onSave={async (value) => {
              const updated = await describe.mutateAsync({
                id: typeId,
                data: { description: value.trim() },
              })
              queryClient.setQueryData(
                getAadeInvoiceTypeControllerInvoiceTypeQueryKey(typeId),
                updated,
              )
              void queryClient.invalidateQueries({ queryKey: ['/api/aade-invoice-types'] })
            }}
          >
            {(value, setValue) => (
              <Input value={value} onChange={(event) => setValue(event.target.value)} />
            )}
          </FieldEditor>

          {/*
            ⚠️ Codes 4 and 12 carry `Για Μελλοντική Χρήση` because annex 8.1's description cell for
            them is EMPTY — that is what AADE published, not a placeholder somebody left behind. It
            is said here because a reader who does not know that will "fix" it.
          */}
          {type.description === FUTURE_USE && (
            <p className="text-muted-foreground pt-2 text-sm">{t('aadeTypes.futureUse')}</p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

/** What V31 seeded for the two codes whose annex 8.1 description cell is empty. */
const FUTURE_USE = 'Για Μελλοντική Χρήση'

/** A field no route can change. See `VatClassDetail` — the same component, the same argument. */
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
