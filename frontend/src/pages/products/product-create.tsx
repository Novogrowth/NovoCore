import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { useProductControllerCreate } from '@/api/generated/endpoints/product/product'
import { ProductType, type Money } from '@/api/generated/model'
import { idOptions, useSuppliers, useUnitsOfMeasure, useVatClasses } from '@/api/lookups'
import { MoneyInput } from '@/components/decimal/decimal-input'
import { OptionSelect } from '@/components/option-select'
import { Refusal } from '@/components/refusal'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * Creating a product.
 *
 * **The one place on this screen where a form batches**, because the backend does: `POST
 * /api/products` either creates a product or does not. That is the opposite of editing, where seven
 * separate routes mean seven separate requests and batching would invent partial states.
 *
 * A VAT class is required and choosing one needs `TAX_AND_CHARGES`, so the list offers this page
 * only to a role holding both grants — a form that cannot be completed should not be reachable.
 */
export function ProductCreate() {
  const { t } = useTranslation('common')
  const { t: tEnum } = useTranslation('enums')
  const navigate = useNavigate()

  const units = useUnitsOfMeasure()
  const vatClasses = useVatClasses()
  const suppliers = useSuppliers()
  const create = useProductControllerCreate()

  const [sku, setSku] = useState('')
  const [name, setName] = useState('')
  const [brand, setBrand] = useState('')
  const [type, setType] = useState<ProductType>(ProductType.GOODS)
  const [unitOfMeasureId, setUnitOfMeasureId] = useState<number | undefined>()
  const [defaultVatClassId, setDefaultVatClassId] = useState<number | undefined>()
  const [sellingPrice, setSellingPrice] = useState<Money | undefined>()
  const [supplierId, setSupplierId] = useState<number | undefined>()
  const [supplierSku, setSupplierSku] = useState('')

  const complete =
    sku.trim() !== '' &&
    name.trim() !== '' &&
    unitOfMeasureId !== undefined &&
    defaultVatClassId !== undefined &&
    sellingPrice !== undefined

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!complete) return

    create.mutate(
      {
        data: {
          sku: sku.trim(),
          name: name.trim(),
          // Optional, and omitted rather than sent blank: the column stores one representation of
          // "no brand", and most of this catalogue is own-blend coffee that genuinely has none.
          ...(brand.trim() ? { brand: brand.trim() } : {}),
          type,
          unitOfMeasureId,
          defaultVatClassId,
          sellingPrice,
          ...(supplierId !== undefined ? { supplierId } : {}),
          ...(supplierSku.trim() ? { supplierSku: supplierSku.trim() } : {}),
          /*
           * ⚠️ Required, and since 8a the contract says so — omitting it is a compile error again.
           *
           * Omitting `serialTracked` broke product creation for every user: it was a primitive
           * `boolean` on a Java record, Jackson passed an **absent** creator property to the
           * constructor as null, and `FAIL_ON_NULL_FOR_PRIMITIVES` refused it with
           * `400 "Malformed request body: Cannot map null into type boolean"` before any handler
           * ran. The spec called the field optional, so this form was written correctly against a
           * contract that was wrong.
           *
           * **The compile-time catch has been true, then false, then true again, and the middle
           * stretch is worth remembering.** From 2026-08-01 the generator marked *primitive*
           * components required, so `tsc` refused a caller that omitted this. Q1's item 7 then boxed
           * the field to `Boolean` so the refusal would name it — `"serialTracked" is required and
           * was not supplied.` — and **a boxed component is not primitive, so the declaration went
           * away with the bad message**: two days in which the server refused an omission and
           * nothing on this side did. 8a's `@Mandatory` restores it by declaring what reflection
           * cannot infer. `spec-hygiene.test.ts` now pins the seven boxed flags **by name**, because
           * a count could not have said which one came back.
           *
           * `false` is not a default invented here: this form has no serial-tracking control, so
           * false is the only value it can honestly claim. A product is made serial-tracked
           * afterwards, on its detail screen — worth revisiting if that ever becomes a create-time
           * decision.
           */
          serialTracked: false,
        },
      },
      {
        // Straight to what was just made: the operator is almost always about to look at it.
        onSuccess: (product) => void navigate(`/products/${product.id}`),
      },
    )
  }


  return (
    <Card className="max-w-2xl">
      <CardHeader>
        <CardTitle>{t('products.create')}</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="sku">{t('products.column.sku')}</Label>
              <Input id="sku" value={sku} onChange={(e) => setSku(e.target.value)} required />
            </div>
            <div className="space-y-1">
              <Label htmlFor="name">{t('products.column.name')}</Label>
              <Input id="name" value={name} onChange={(e) => setName(e.target.value)} required />
            </div>

            <div className="space-y-1">
              <Label htmlFor="brand">{t('products.column.brand')}</Label>
              <Input id="brand" value={brand} onChange={(e) => setBrand(e.target.value)} />
            </div>

            <div className="space-y-1">
              <Label htmlFor="type">{t('products.column.type')}</Label>
              <OptionSelect
                id="type"
                options={Object.values(ProductType).map((value) => ({
                  value,
                  label: tEnum(`ProductType.${value}`),
                }))}
                value={type}
                onValueChange={(value) => setType(value as ProductType)}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="unit">{t('products.column.unit')}</Label>
              <OptionSelect
                id="unit"
                options={idOptions(units.items, (unit) => unit.name)}
                value={unitOfMeasureId === undefined ? null : String(unitOfMeasureId)}
                onValueChange={(value) => setUnitOfMeasureId(idFrom(value))}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="vat-class">{t('products.column.vatClass')}</Label>
              <OptionSelect
                id="vat-class"
                options={idOptions(vatClasses.items, (vatClass) => vatClass.description)}
                value={defaultVatClassId === undefined ? null : String(defaultVatClassId)}
                onValueChange={(value) => setDefaultVatClassId(idFrom(value))}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="selling-price">{t('products.column.sellingPrice')}</Label>
              <MoneyInput
                id="selling-price"
                aria-label={t('products.column.sellingPrice')}
                value={sellingPrice}
                // EUR because the ledger is EUR-only (ADR 0005) and this is a new record with no
                // existing currency to inherit. Stated, not defaulted silently by the API.
                currency={sellingPrice?.currency ?? 'EUR'}
                onValueChange={setSellingPrice}
              />
            </div>

            {suppliers.permitted && (
              <>
                <div className="space-y-1">
                  <Label htmlFor="supplier">{t('products.column.supplier')}</Label>
                  <OptionSelect
                    id="supplier"
                    options={idOptions(suppliers.items, (supplier) => supplier.name)}
                    value={supplierId === undefined ? null : String(supplierId)}
                    onValueChange={(value) => setSupplierId(idFrom(value))}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="supplier-sku">{t('products.column.supplierSku')}</Label>
                  <Input
                    id="supplier-sku"
                    value={supplierSku}
                    onChange={(e) => setSupplierSku(e.target.value)}
                  />
                </div>
              </>
            )}
          </div>

          {/* Not `create.error.detail` directly: a 403 carries no detail by design and a network
              failure is not an ApiError at all, and both used to render as nothing. */}
          <Refusal error={create.error} />

          <div className="flex gap-2">
            <Button type="submit" disabled={!complete || create.isPending}>
              {create.isPending ? t('products.creating') : t('products.create')}
            </Button>
            <Button type="button" variant="ghost" onClick={() => void navigate('/products')}>
              {t('field.cancel')}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

/** A select's value is text; an id is a count, not a value. */
function idFrom(value: string | null): number | undefined {
  // Base UI hands back null when a select is cleared; an empty selection is not an id.
  if (value === null || value === '') return undefined
  // eslint-disable-next-line no-restricted-syntax
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : undefined
}
