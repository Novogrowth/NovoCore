import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { useTaxLookupControllerCreateVatClass } from '@/api/generated/endpoints/tax-lookup/tax-lookup'
import { RateInput } from '@/components/decimal/decimal-input'
import { Refusal } from '@/components/refusal'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * Adding a VAT class.
 *
 * ⚠️ **This form is also the rate-change mechanism, and that is worth knowing while filling it in.**
 * There is no route that edits an existing class's rate, because editing one would retroactively
 * change what every invoice already issued under that class appears to have charged. So a statutory
 * rate change is *this form* plus a deactivation of the old class — which is why the form says so
 * rather than leaving somebody to look for an edit screen that does not exist.
 *
 * ⚠️ **The rate is a PERCENTAGE, not a fraction.** `24` means 24%; `0.24` is a quarter of one
 * percent and the backend refuses it with a message naming the factor-of-100 trap. That refusal
 * exists because `0.24` sat comfortably inside the original `0–100` CHECK and was accepted; V10
 * narrowed it to *"zero, or between 1 and 100"* for exactly this reason.
 *
 * **The code is the ΑΑΔΕ / Prosvasis Go code**, not a label of our choosing — `1410` is 24%. It is
 * the class's identity and there is no route to change one either.
 */
export function VatClassCreate() {
  const { t } = useTranslation('common')
  const navigate = useNavigate()

  const create = useTaxLookupControllerCreateVatClass()

  const [code, setCode] = useState('')
  const [description, setDescription] = useState('')
  const [ratePercent, setRatePercent] = useState<string | undefined>(undefined)

  /*
   * All three are required in fact and none of them says so in the generated type: `NewVatClass`
   * declares no `required` list, because every one of its components is reference-typed and the
   * spec generator can only see primitives. The compact constructor rejects a missing one, so this
   * is the guard `tsc` cannot be.
   */
  const complete = code.trim() !== '' && description.trim() !== '' && ratePercent !== undefined

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!complete || ratePercent === undefined) return

    create.mutate(
      { data: { code: code.trim(), description: description.trim(), ratePercent } },
      { onSuccess: (created) => void navigate(`/settings/vat-classes/${created.id}`) },
    )
  }

  return (
    <Card className="max-w-2xl">
      <CardHeader>
        <CardTitle>{t('vatClasses.create')}</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-1">
            <Label htmlFor="vat-class-code">{t('vatClasses.column.code')}</Label>
            <Input
              id="vat-class-code"
              value={code}
              onChange={(event) => setCode(event.target.value)}
              required
            />
            <p className="text-muted-foreground text-sm">{t('vatClasses.codeHint')}</p>
          </div>

          <div className="space-y-1">
            <Label htmlFor="vat-class-description">{t('vatClasses.column.description')}</Label>
            <Input
              id="vat-class-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              required
            />
          </div>

          <div className="space-y-1">
            <Label htmlFor="vat-class-rate">{t('vatClasses.column.rate')}</Label>
            <RateInput id="vat-class-rate" value={ratePercent} onValueChange={setRatePercent} />
            <p className="text-muted-foreground text-sm">{t('vatClasses.rateHint')}</p>
          </div>

          {/* Said on the form, because this form IS how a rate changes. */}
          <p className="text-muted-foreground text-sm">{t('vatClasses.rateIsPermanent')}</p>

          <Refusal error={create.error} />

          <div className="flex gap-2">
            <Button type="submit" disabled={!complete || create.isPending}>
              {create.isPending ? t('vatClasses.creating') : t('vatClasses.create')}
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={() => void navigate('/settings/vat-classes')}
            >
              {t('field.cancel')}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
