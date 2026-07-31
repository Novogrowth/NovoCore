import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { useCustomerControllerCreate } from '@/api/generated/endpoints/customer/customer'
import { VatStatus } from '@/api/generated/model'
import { idOptions, useVatExemptionReasons } from '@/api/lookups'
import { OptionSelect } from '@/components/option-select'
import { Refusal } from '@/components/refusal'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import { NEEDS_EXEMPTION_REASON, NEEDS_VAT_NUMBER } from '@/lib/vat-status'

/**
 * Creating a customer.
 *
 * Batched, like the product create form and for the same reason: `POST /api/customers` either
 * creates a customer or does not, which is the opposite of editing one, where each route is its own
 * request and batching would invent partial states.
 *
 * **`vatStatus` is never defaulted silently.** `NewCustomer`'s javadoc says so in as many words, and
 * spells out the cost: quietly assuming `DOMESTIC` for an intra-EU business *"would produce an
 * invoice charging Greek VAT on a reverse-charged supply"*. So the form starts with no status
 * chosen and will not submit without one.
 *
 * **`vatClassOverrideId` is deliberately absent**, though `NewCustomer` accepts it. The override is
 * deferred out of F2 as its own follow-up — see `novocore-frontend-roadmap.md` — and a create form
 * is the worst place to introduce a field whose accounting implications have not been settled.
 */
export function CustomerCreate() {
  const { t } = useTranslation('common')
  const { t: tEnum } = useTranslation('enums')
  const navigate = useNavigate()

  const exemptionReasons = useVatExemptionReasons()
  const create = useCustomerControllerCreate()

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [vatNumber, setVatNumber] = useState('')
  const [vatStatus, setVatStatus] = useState<VatStatus | undefined>()
  const [vatExemptionReasonId, setVatExemptionReasonId] = useState<number | undefined>()

  const needsReason = vatStatus !== undefined && NEEDS_EXEMPTION_REASON.has(vatStatus)
  const needsVatNumber = vatStatus !== undefined && NEEDS_VAT_NUMBER.has(vatStatus)

  /*
   * The backend's own two rules, applied before the request rather than reported after it. Both
   * are refusals with messages, so getting this wrong is not dangerous — it is just a round trip
   * that tells the operator something the form already knew.
   */
  const complete =
    name.trim() !== '' &&
    vatStatus !== undefined &&
    (!needsReason || vatExemptionReasonId !== undefined) &&
    (!needsVatNumber || vatNumber.trim() !== '')

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!complete || vatStatus === undefined) return

    create.mutate(
      {
        data: {
          name: name.trim(),
          vatStatus,
          ...(email.trim() ? { email: email.trim() } : {}),
          ...(phone.trim() ? { phone: phone.trim() } : {}),
          ...(vatNumber.trim() ? { vatNumber: vatNumber.trim() } : {}),
          ...(vatExemptionReasonId !== undefined ? { vatExemptionReasonId } : {}),
        },
      },
      { onSuccess: (customer) => void navigate(`/customers/${customer.id}`) },
    )
  }

  return (
    <Card className="max-w-2xl">
      <CardHeader>
        <CardTitle>{t('customers.create')}</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="customer-name">{t('customers.column.name')}</Label>
              <Input
                id="customer-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="customer-vat-number">{t('customers.column.vatNumber')}</Label>
              <Input
                id="customer-vat-number"
                value={vatNumber}
                onChange={(e) => setVatNumber(e.target.value)}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="customer-email">{t('customers.column.email')}</Label>
              <Input
                id="customer-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="customer-phone">{t('customers.column.phone')}</Label>
              <Input
                id="customer-phone"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="customer-vat-status">{t('vatStatus.label')}</Label>
              <OptionSelect
                id="customer-vat-status"
                options={Object.values(VatStatus).map((status) => ({
                  value: status,
                  label: tEnum(`VatStatus.${status}`),
                }))}
                value={vatStatus ?? null}
                onValueChange={(value) => {
                  const next = (value ?? undefined) as VatStatus | undefined
                  setVatStatus(next)
                  // The reason belongs to the status that required it.
                  if (next === undefined || !NEEDS_EXEMPTION_REASON.has(next)) {
                    setVatExemptionReasonId(undefined)
                  }
                }}
              />
            </div>

            {needsReason && (
              <div className="space-y-1">
                <Label htmlFor="customer-exemption-reason">
                  {t('vatStatus.exemptionReason')}
                </Label>
                <OptionSelect
                  id="customer-exemption-reason"
                  options={idOptions(exemptionReasons.items, (reason) => reason.description)}
                  value={
                    vatExemptionReasonId === undefined ? null : String(vatExemptionReasonId)
                  }
                  onValueChange={(value) => setVatExemptionReasonId(idFrom(value))}
                />
              </div>
            )}
          </div>

          {/* Said in the form, because a disabled button explains nothing on its own. */}
          {needsVatNumber && vatNumber.trim() === '' && (
            <p className="text-muted-foreground text-sm">
              {t('vatStatus.vatNumberRequiredToCreate')}
            </p>
          )}

          <Refusal error={create.error} />

          <div className="flex gap-2">
            <Button type="submit" disabled={!complete || create.isPending}>
              {create.isPending ? t('customers.creating') : t('customers.create')}
            </Button>
            <Button type="button" variant="ghost" onClick={() => void navigate('/customers')}>
              {t('field.cancel')}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

/** A select's value is text; an id is a count. The rule's documented escape applies. */
function idFrom(value: string | null): number | undefined {
  if (value === null || value === '') return undefined
  // eslint-disable-next-line no-restricted-syntax
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : undefined
}
