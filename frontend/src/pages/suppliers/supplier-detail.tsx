import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import {
  getSupplierControllerSupplierQueryKey,
  useSupplierControllerChangeContactDetails,
  useSupplierControllerChangeVatNumber,
  useSupplierControllerChangeVatStatus,
  useSupplierControllerDeactivate,
  useSupplierControllerReactivate,
  useSupplierControllerRename,
  useSupplierControllerSupplier,
} from '@/api/generated/endpoints/supplier/supplier'
import { Section, VatStatus, type SupplierView } from '@/api/generated/model'
import { idOptions, nameFor, useVatExemptionReasons } from '@/api/lookups'
import { usePermissions } from '@/auth/permissions'
import { FieldEditor, UnsetValue } from '@/components/field-editor/field-editor'
import { OptionSelect } from '@/components/option-select'
import { Refusal } from '@/components/refusal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'

import { NEEDS_EXEMPTION_REASON, NEEDS_VAT_NUMBER } from './vat-status-rules'

/**
 * One supplier.
 *
 * The same per-field shape as a product, with one difference that comes from the API rather than
 * from taste: **VAT status and its exemption reason are one route and therefore one editor.**
 * `PATCH /api/suppliers/{id}/vat-status` takes both, and the backend refuses a status that requires
 * a reason without one — so editing them separately would offer a combination guaranteed to be
 * refused. Contact details are the same case, one route for email and phone.
 */
export function SupplierDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const { t: tEnum } = useTranslation('enums')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // The route parameter is text. A database id is a count, not a value, and the lint rule's
  // documented escape is to say so on the line.
  // eslint-disable-next-line no-restricted-syntax
  const supplierId = Number(id)

  const query = useSupplierControllerSupplier(supplierId, {
    query: { enabled: Number.isInteger(supplierId) },
  })
  const supplier = query.data

  const exemptionReasons = useVatExemptionReasons()
  const editable = permissions.canEdit(Section.SUPPLIERS)

  /** Writes the PATCH response straight into the cache — every route returns the whole record. */
  const applyResponse = (updated: SupplierView) => {
    queryClient.setQueryData(getSupplierControllerSupplierQueryKey(supplierId), updated)
    void queryClient.invalidateQueries({ queryKey: ['/api/suppliers'] })
  }

  const rename = useSupplierControllerRename()
  const changeVatNumber = useSupplierControllerChangeVatNumber()
  const changeContactDetails = useSupplierControllerChangeContactDetails()
  const changeVatStatus = useSupplierControllerChangeVatStatus()
  const deactivate = useSupplierControllerDeactivate()
  const reactivate = useSupplierControllerReactivate()

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  if (!supplier) return <p className="text-muted-foreground text-sm">{t('suppliers.notFound')}</p>

  const statusLabel = (status: VatStatus | undefined) =>
    status ? tEnum(`VatStatus.${status}`) : undefined

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link to="/suppliers" className="text-muted-foreground text-sm hover:underline">
            ← {t('suppliers.backToList')}
          </Link>
          <h1 className="text-lg font-semibold">{supplier.name}</h1>
          {supplier.active === false && (
            <Badge variant="outline">{t('suppliers.flag.inactive')}</Badge>
          )}
        </div>

        {editable &&
          (supplier.active === false ? (
            <Button
              size="sm"
              variant="outline"
              disabled={reactivate.isPending}
              onClick={() =>
                reactivate.mutate({ id: supplierId }, { onSuccess: () => void query.refetch() })
              }
            >
              {t('suppliers.reactivate')}
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              disabled={deactivate.isPending}
              onClick={() => {
                if (window.confirm(t('suppliers.deactivateConfirm'))) {
                  deactivate.mutate({ id: supplierId }, { onSuccess: () => void query.refetch() })
                }
              }}
            >
              {t('suppliers.deactivate')}
            </Button>
          ))}
      </div>

      {/* A refused deactivation explains itself. Products shipped without this and the button
          read as dead; the mechanism is shared so this screen cannot repeat it. */}
      <Refusal error={deactivate.error ?? reactivate.error} />

      <Card>
        <CardHeader>
          <CardTitle>{t('suppliers.fields')}</CardTitle>
        </CardHeader>
        <CardContent>
          <FieldEditor
            label={t('suppliers.column.name')}
            value={supplier.name ?? ''}
            display={supplier.name ?? <UnsetValue />}
            editable={editable}
            isValid={(value) => value.trim() !== ''}
            onSave={async (name) => {
              applyResponse(await rename.mutateAsync({ id: supplierId, data: { name } }))
            }}
          >
            {(draft, setDraft) => (
              <Input
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                aria-label={t('suppliers.column.name')}
              />
            )}
          </FieldEditor>

          <FieldEditor
            label={t('suppliers.column.vatNumber')}
            value={supplier.vatNumber ?? ''}
            display={supplier.vatNumber ?? <UnsetValue />}
            editable={editable}
            onSave={async (vatNumber) => {
              applyResponse(
                await changeVatNumber.mutateAsync({ id: supplierId, data: { vatNumber } }),
              )
            }}
          >
            {(draft, setDraft) => (
              <Input
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                aria-label={t('suppliers.column.vatNumber')}
              />
            )}
          </FieldEditor>

          {/* One route, two values — so one editor. Splitting them would send whichever half was
              not being edited back as it stands, which reads as an edit nobody made. */}
          <FieldEditor<{ email: string; phone: string }>
            label={t('suppliers.column.contact')}
            value={{ email: supplier.email ?? '', phone: supplier.phone ?? '' }}
            display={
              supplier.email || supplier.phone ? (
                [supplier.email, supplier.phone].filter(Boolean).join(' · ')
              ) : (
                <UnsetValue />
              )
            }
            editable={editable}
            onSave={async (contact) => {
              applyResponse(
                await changeContactDetails.mutateAsync({ id: supplierId, data: contact }),
              )
            }}
          >
            {(draft, setDraft) => (
              <div className="flex flex-wrap gap-2">
                <Input
                  value={draft.email}
                  onChange={(event) => setDraft({ ...draft, email: event.target.value })}
                  aria-label={t('suppliers.column.email')}
                  placeholder={t('suppliers.column.email')}
                />
                <Input
                  value={draft.phone}
                  onChange={(event) => setDraft({ ...draft, phone: event.target.value })}
                  aria-label={t('suppliers.column.phone')}
                  placeholder={t('suppliers.column.phone')}
                />
              </div>
            )}
          </FieldEditor>

          <FieldEditor<{ vatStatus: VatStatus; vatExemptionReasonId: number | undefined }>
            label={t('suppliers.column.vatStatus')}
            value={{
              vatStatus: supplier.vatStatus ?? VatStatus.DOMESTIC,
              vatExemptionReasonId: supplier.vatExemptionReasonId,
            }}
            display={
              supplier.vatStatus ? (
                <span>
                  {statusLabel(supplier.vatStatus)}
                  {NEEDS_EXEMPTION_REASON.has(supplier.vatStatus) && (
                    <span className="text-muted-foreground">
                      {' · '}
                      {nameFor(
                        exemptionReasons,
                        supplier.vatExemptionReasonId,
                        (reason) => reason.description,
                      ) ?? t('suppliers.vatStatus.noReason')}
                    </span>
                  )}
                </span>
              ) : (
                <UnsetValue />
              )
            }
            // Changing the status needs the reason list to choose from, and that list lives under
            // TAX_AND_CHARGES. Without it the field is read-only rather than pretending otherwise.
            editable={editable && exemptionReasons.permitted}
            isValid={(draft) =>
              (!NEEDS_EXEMPTION_REASON.has(draft.vatStatus) ||
                draft.vatExemptionReasonId !== undefined) &&
              (!NEEDS_VAT_NUMBER.has(draft.vatStatus) || (supplier.vatNumber ?? '') !== '')
            }
            onSave={async (draft) => {
              applyResponse(
                await changeVatStatus.mutateAsync({
                  id: supplierId,
                  data: {
                    vatStatus: draft.vatStatus,
                    ...(draft.vatExemptionReasonId !== undefined
                      ? { vatExemptionReasonId: draft.vatExemptionReasonId }
                      : {}),
                  },
                }),
              )
            }}
          >
            {(draft, setDraft) => {
              const needsReason = NEEDS_EXEMPTION_REASON.has(draft.vatStatus)
              const missingVatNumber =
                NEEDS_VAT_NUMBER.has(draft.vatStatus) && (supplier.vatNumber ?? '') === ''

              return (
                <div className="space-y-2">
                  <OptionSelect
                    aria-label={t('suppliers.column.vatStatus')}
                    options={Object.values(VatStatus).map((status) => ({
                      value: status,
                      label: tEnum(`VatStatus.${status}`),
                    }))}
                    value={draft.vatStatus}
                    onValueChange={(value) => {
                      const next = (value ?? VatStatus.DOMESTIC) as VatStatus
                      setDraft({
                        vatStatus: next,
                        // A reason belongs to the status that required it. Carrying it onto a
                        // status that does not is how a supplier ends up filed under an article
                        // nobody chose for it.
                        vatExemptionReasonId: NEEDS_EXEMPTION_REASON.has(next)
                          ? draft.vatExemptionReasonId
                          : undefined,
                      })
                    }}
                  />

                  {/* Shown only when the chosen status requires it — the backend's own rule, not a
                      preference, and the reason the two are edited together at all. */}
                  {needsReason && (
                    <OptionSelect
                      aria-label={t('suppliers.column.exemptionReason')}
                      options={idOptions(exemptionReasons.items, (reason) => reason.description)}
                      value={
                        draft.vatExemptionReasonId === undefined
                          ? null
                          : String(draft.vatExemptionReasonId)
                      }
                      onValueChange={(value) =>
                        setDraft({ ...draft, vatExemptionReasonId: idFrom(value) })
                      }
                    />
                  )}

                  {/*
                   * Why saving is refused, said before the button is pressed rather than after.
                   *
                   * `FieldEditor.isValid` blocks the save silently — it returns early with nothing
                   * on screen — so a screen that relies on it owes the operator the reason in the
                   * form itself. A VAT number is a different route, so this one cannot be fixed
                   * from here and says where to go.
                   */}
                  {needsReason && draft.vatExemptionReasonId === undefined && (
                    <p className="text-muted-foreground text-sm">
                      {t('suppliers.vatStatus.reasonRequired')}
                    </p>
                  )}
                  {missingVatNumber && (
                    <p className="text-muted-foreground text-sm">
                      {t('suppliers.vatStatus.vatNumberRequired')}
                    </p>
                  )}
                </div>
              )
            }}
          </FieldEditor>
        </CardContent>
      </Card>
    </div>
  )
}

/** A select's value is text; an id is a count. The rule's documented escape applies. */
function idFrom(value: string | null): number | undefined {
  if (value === null || value === '') return undefined
  // eslint-disable-next-line no-restricted-syntax
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : undefined
}
