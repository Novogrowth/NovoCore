import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { useProductControllerCreate } from '@/api/generated/endpoints/product/product'
import { ProductType, type Money } from '@/api/generated/model'
import { ApiError } from '@/api/http'
import { useSuppliers, useUnitsOfMeasure, useVatClasses } from '@/api/lookups'
import { MoneyInput } from '@/components/decimal/decimal-input'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

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
          type,
          unitOfMeasureId,
          defaultVatClassId,
          sellingPrice,
          ...(supplierId !== undefined ? { supplierId } : {}),
          ...(supplierSku.trim() ? { supplierSku: supplierSku.trim() } : {}),
        },
      },
      {
        // Straight to what was just made: the operator is almost always about to look at it.
        onSuccess: (product) => void navigate(`/products/${product.id}`),
      },
    )
  }

  const refusal = create.error instanceof ApiError ? create.error.detail : undefined

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
              <Label htmlFor="type">{t('products.column.type')}</Label>
              <Select value={type} onValueChange={(value) => setType(value as ProductType)}>
                <SelectTrigger id="type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {Object.values(ProductType).map((value) => (
                    <SelectItem key={value} value={value}>
                      {tEnum(`ProductType.${value}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <Label htmlFor="unit">{t('products.column.unit')}</Label>
              <Select
                value={unitOfMeasureId === undefined ? '' : String(unitOfMeasureId)}
                onValueChange={(value) => setUnitOfMeasureId(idFrom(value))}
              >
                <SelectTrigger id="unit">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {units.items.map((unit) => (
                    <SelectItem key={unit.id} value={String(unit.id)}>
                      {unit.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <Label htmlFor="vat-class">{t('products.column.vatClass')}</Label>
              <Select
                value={defaultVatClassId === undefined ? '' : String(defaultVatClassId)}
                onValueChange={(value) => setDefaultVatClassId(idFrom(value))}
              >
                <SelectTrigger id="vat-class">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {vatClasses.items.map((vatClass) => (
                    <SelectItem key={vatClass.id} value={String(vatClass.id)}>
                      {vatClass.description}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
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
                  <Select
                    value={supplierId === undefined ? '' : String(supplierId)}
                    onValueChange={(value) => setSupplierId(idFrom(value))}
                  >
                    <SelectTrigger id="supplier">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {suppliers.items.map((supplier) => (
                        <SelectItem key={supplier.id} value={String(supplier.id)}>
                          {supplier.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
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

          {refusal && (
            <p role="alert" className="text-destructive text-sm">
              {refusal}
            </p>
          )}

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
