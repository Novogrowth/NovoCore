import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import {
  getCustomerControllerCustomerQueryKey,
  useCustomerControllerChangeContactDetails,
  useCustomerControllerChangeVatNumber,
  useCustomerControllerChangeVatStatus,
  useCustomerControllerCustomer,
  useCustomerControllerDeactivate,
  useCustomerControllerReactivate,
  useCustomerControllerRename,
} from '@/api/generated/endpoints/customer/customer'
import { Section, VatStatus, type CustomerView } from '@/api/generated/model'
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
 * One customer.
 *
 * The supplier screen plus the one thing customers have that suppliers do not: **a structural
 * record.** `Πελάτης Λιανικής` carries `systemKey: RETAIL_WALK_IN`, and three things about it are
 * fixed by CHECK constraints rather than by policy — its VAT status is `DOMESTIC`, it holds no VAT
 * number, and it cannot be deactivated. Its name and contact details are ordinary and editable.
 *
 * **Those controls are shown disabled with the reason, never hidden.** Hiding them would leave
 * somebody hunting for a setting that exists on every other customer; leaving them live would send
 * a request the backend refuses — today, for two of the three, with a bare `400` carrying no
 * message at all (see `PROGRESS.md`, backend item 4). Disabled-with-a-reason is the only one of the
 * three that tells the truth.
 *
 * ⚠️ **The reasons are written here rather than fetched, and that is a mirror.** The backend has
 * better sentences than these and discards them. When item 4 is fixed these should be reconsidered:
 * a refusal that explains itself is worth more than a rule restated on the client.
 */
export function CustomerDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // The route parameter is text. A database id is a count, not a value.
  // eslint-disable-next-line no-restricted-syntax
  const customerId = Number(id)

  const query = useCustomerControllerCustomer(customerId, {
    query: { enabled: Number.isInteger(customerId) },
  })
  const customer = query.data

  const exemptionReasons = useVatExemptionReasons()
  const editable = permissions.canEdit(Section.CUSTOMERS)

  const applyResponse = (updated: CustomerView) => {
    queryClient.setQueryData(getCustomerControllerCustomerQueryKey(customerId), updated)
    void queryClient.invalidateQueries({ queryKey: ['/api/customers'] })
  }

  const rename = useCustomerControllerRename()
  const changeVatNumber = useCustomerControllerChangeVatNumber()
  const changeContactDetails = useCustomerControllerChangeContactDetails()
  const changeVatStatus = useCustomerControllerChangeVatStatus()
  const deactivate = useCustomerControllerDeactivate()
  const reactivate = useCustomerControllerReactivate()

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  if (!customer) return <p className="text-muted-foreground text-sm">{t('customers.notFound')}</p>

  /*
   * `systemKey`, not `systemRecord`.
   *
   * The API returns `"systemRecord": true` and `"mergeable": false`, and the spec declares neither,
   * so the generated type has no idea they exist. `systemKey` IS in the spec and carries the same
   * fact. Recorded as a workaround rather than a preference — see backend item 2.
   */
  const isSystemRecord = customer.systemKey !== undefined

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link to="/customers" className="text-muted-foreground text-sm hover:underline">
            ← {t('customers.backToList')}
          </Link>
          <h1 className="text-lg font-semibold">{customer.name}</h1>
          {isSystemRecord && (
            <Badge variant="secondary" title={t('customers.system.badgeTitle')}>
              {t('customers.system.badge')}
            </Badge>
          )}
          {customer.active === false && (
            <Badge variant="outline">{t('customers.flag.inactive')}</Badge>
          )}
        </div>

        {editable &&
          (customer.active === false ? (
            <Button
              size="sm"
              variant="outline"
              disabled={reactivate.isPending}
              onClick={() =>
                reactivate.mutate({ id: customerId }, { onSuccess: () => void query.refetch() })
              }
            >
              {t('customers.reactivate')}
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              // Disabled rather than absent, for the same reason the fields are.
              disabled={deactivate.isPending || isSystemRecord}
              onClick={() => {
                if (window.confirm(t('customers.deactivateConfirm'))) {
                  deactivate.mutate({ id: customerId }, { onSuccess: () => void query.refetch() })
                }
              }}
            >
              {t('customers.deactivate')}
            </Button>
          ))}
      </div>

      {isSystemRecord && editable && (
        <p className="text-muted-foreground flex items-start gap-1 text-sm">
          {t('customers.system.cannotDeactivate')}
        </p>
      )}

      <Refusal error={deactivate.error ?? reactivate.error} />

      <Card>
        <CardHeader>
          <CardTitle>{t('customers.fields')}</CardTitle>
        </CardHeader>
        <CardContent>
          {/* Ordinary even on the retail record: its name is operator-editable, and the backend
              accepts a rename. Only the VAT treatment and deactivation are fixed. */}
          <FieldEditor
            label={t('customers.column.name')}
            value={customer.name ?? ''}
            display={customer.name ?? <UnsetValue />}
            editable={editable}
            isValid={(value) => value.trim() !== ''}
            onSave={async (name) => {
              applyResponse(await rename.mutateAsync({ id: customerId, data: { name } }))
            }}
          >
            {(draft, setDraft) => (
              <Input
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                aria-label={t('customers.column.name')}
              />
            )}
          </FieldEditor>

          <FieldEditor
            label={t('customers.column.vatNumber')}
            value={customer.vatNumber ?? ''}
            display={customer.vatNumber ?? <UnsetValue />}
            editable={editable}
            {...(isSystemRecord ? { lockedReason: t('customers.system.noVatNumber') } : {})}
            onSave={async (vatNumber) => {
              applyResponse(
                await changeVatNumber.mutateAsync({ id: customerId, data: { vatNumber } }),
              )
            }}
          >
            {(draft, setDraft) => (
              <Input
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                aria-label={t('customers.column.vatNumber')}
              />
            )}
          </FieldEditor>

          <FieldEditor<{ email: string; phone: string }>
            label={t('customers.column.contact')}
            value={{ email: customer.email ?? '', phone: customer.phone ?? '' }}
            display={
              customer.email || customer.phone ? (
                [customer.email, customer.phone].filter(Boolean).join(' · ')
              ) : (
                <UnsetValue />
              )
            }
            editable={editable}
            onSave={async (contact) => {
              applyResponse(
                await changeContactDetails.mutateAsync({ id: customerId, data: contact }),
              )
            }}
          >
            {(draft, setDraft) => (
              <div className="flex flex-wrap gap-2">
                <Input
                  value={draft.email}
                  onChange={(event) => setDraft({ ...draft, email: event.target.value })}
                  aria-label={t('customers.column.email')}
                  placeholder={t('customers.column.email')}
                />
                <Input
                  value={draft.phone}
                  onChange={(event) => setDraft({ ...draft, phone: event.target.value })}
                  aria-label={t('customers.column.phone')}
                  placeholder={t('customers.column.phone')}
                />
              </div>
            )}
          </FieldEditor>

          <VatStatusField
            value={{
              vatStatus: customer.vatStatus ?? VatStatus.DOMESTIC,
              vatExemptionReasonId: customer.vatExemptionReasonId,
            }}
            vatNumber={customer.vatNumber}
            exemptionReasons={exemptionReasons}
            editable={editable}
            {...(isSystemRecord ? { lockedReason: t('customers.system.vatFixed') } : {})}
            onSave={async (draft) => {
              applyResponse(
                await changeVatStatus.mutateAsync({
                  id: customerId,
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

          {/*
           * The VAT class override is NOT here, deliberately.
           *
           * `vatClassOverrideId` and `PATCH …/vat-class-override` exist and are customer-only.
           * "This customer is always taxed at this class regardless of the product" carries real
           * accounting weight and needs the TAX_AND_CHARGES gating worked through, so it was
           * deferred out of F2 as its own follow-up rather than built alongside the fields it
           * happens to sit next to. See `novocore-roadmap.md`.
           */}
        </CardContent>
      </Card>
    </div>
  )
}
