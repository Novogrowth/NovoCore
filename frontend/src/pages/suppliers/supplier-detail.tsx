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
import { useVatExemptionReasons } from '@/api/lookups'
import { usePermissions } from '@/auth/permissions'
import { FieldEditor, UnsetValue } from '@/components/field-editor/field-editor'
import { Refusal } from '@/components/refusal'
import { VatStatusField } from '@/components/vat/vat-status-field'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'

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

          {/* Shared with Customers — one route shape, one rule set, one control. */}
          <VatStatusField
            value={{
              vatStatus: supplier.vatStatus ?? VatStatus.DOMESTIC,
              vatExemptionReasonId: supplier.vatExemptionReasonId,
            }}
            vatNumber={supplier.vatNumber}
            exemptionReasons={exemptionReasons}
            editable={editable}
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
          />
        </CardContent>
      </Card>
    </div>
  )
}
