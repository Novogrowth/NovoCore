import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { useUnitOfMeasureControllerCreate } from '@/api/generated/endpoints/unit-of-measure/unit-of-measure'
import { OptionSelect } from '@/components/option-select'
import { Refusal } from '@/components/refusal'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * Adding a unit of measure.
 *
 * ⚠️ **`fractionalQuantityAllowed` is a primitive `boolean`, so omitting it is a `400` that names
 * no field.** Jackson hands an absent creator property to the canonical constructor as `null` and
 * `FAIL_ON_NULL_FOR_PRIMITIVES` refuses the body before any handler runs — the same mechanism that
 * broke product creation through `NewProduct.serialTracked`, and the reason the spec now declares
 * primitives required. `tsc` therefore refuses a form that omits it, and so does the server.
 *
 * ⚠️ **But it is a required CHOICE here rather than a checkbox, and that is a separate argument.**
 * An unticked checkbox does not omit the field — it sends `false`, which the server accepts happily.
 * A kilogram that cannot be sold by the half would then look exactly like one somebody decided
 * should not be. `F4WriteContractIT` holds both halves against the real backend: an omission is
 * refused, an explicit `false` is created.
 *
 * ⚠️ **The myDATA code is settable once and then frozen**, so it is offered here and can be left
 * empty. There is no correction path: a wrong one means deactivate and replace. Empty is the honest
 * default, since the verified ΑΑΔΕ list has not been supplied for any of the eight seeded units.
 *
 * **The code is never editable afterwards** — products refer to it — while the name is.
 */
export function UnitCreate() {
  const { t } = useTranslation('common')
  const navigate = useNavigate()

  const create = useUnitOfMeasureControllerCreate()

  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [mydataCode, setMydataCode] = useState('')
  /** `null` until chosen, so "not answered" is distinguishable from "answered no". */
  const [fractional, setFractional] = useState<string | null>(null)

  const complete = code.trim() !== '' && name.trim() !== '' && fractional !== null

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!complete || fractional === null) return

    create.mutate(
      {
        data: {
          code: code.trim(),
          name: name.trim(),
          // Always sent: omitting it is a 400 naming no field. Chosen, never defaulted.
          fractionalQuantityAllowed: fractional === 'true',
          ...(mydataCode.trim() ? { mydataCode: mydataCode.trim() } : {}),
        },
      },
      { onSuccess: (created) => void navigate(`/settings/units-of-measure/${created.id}`) },
    )
  }

  return (
    <Card className="max-w-2xl">
      <CardHeader>
        <CardTitle>{t('units.create')}</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-1">
            <Label htmlFor="unit-code">{t('units.column.code')}</Label>
            <Input
              id="unit-code"
              value={code}
              onChange={(event) => setCode(event.target.value)}
              required
            />
            <p className="text-muted-foreground text-sm">{t('units.codeIsPermanentHint')}</p>
          </div>

          <div className="space-y-1">
            <Label htmlFor="unit-name">{t('units.column.name')}</Label>
            <Input
              id="unit-name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              required
            />
          </div>

          <div className="space-y-1">
            <Label htmlFor="unit-fractional">{t('units.column.fractional')}</Label>
            <OptionSelect
              id="unit-fractional"
              value={fractional}
              onValueChange={setFractional}
              options={[
                { value: 'true', label: t('units.fractional.allowed') },
                { value: 'false', label: t('units.fractional.wholeOnly') },
              ]}
            />
            <p className="text-muted-foreground text-sm">{t('units.fractionalHint')}</p>
          </div>

          <div className="space-y-1">
            <Label htmlFor="unit-mydata">{t('units.column.mydataCode')}</Label>
            <Input
              id="unit-mydata"
              value={mydataCode}
              onChange={(event) => setMydataCode(event.target.value)}
            />
            <p className="text-muted-foreground text-sm">{t('units.mydataOnceHint')}</p>
          </div>

          <Refusal error={create.error} />

          <div className="flex gap-2">
            <Button type="submit" disabled={!complete || create.isPending}>
              {create.isPending ? t('units.creating') : t('units.create')}
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={() => void navigate('/settings/units-of-measure')}
            >
              {t('field.cancel')}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
