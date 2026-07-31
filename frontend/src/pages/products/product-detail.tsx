import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import {
  getProductControllerProductQueryKey,
  useProductControllerChangeEan,
  useProductControllerChangeSellingPrice,
  useProductControllerChangeSerialTracking,
  useProductControllerChangeSupplier,
  useProductControllerChangeUnitOfMeasure,
  useProductControllerChangeVatClass,
  useProductControllerDeactivate,
  useProductControllerProduct,
  useProductControllerReactivate,
  useProductControllerRename,
} from '@/api/generated/endpoints/product/product'
import { ProtectedField, Section, type Money, type ProductView } from '@/api/generated/model'
import { idOptions, nameFor, useSuppliers, useUnitsOfMeasure, useVatClasses } from '@/api/lookups'
import { hiddenInResponse, usePermissions } from '@/auth/permissions'
import { MoneyInput } from '@/components/decimal/decimal-input'
import { FieldEditor, HiddenValue, UnsetValue } from '@/components/field-editor/field-editor'
import { OptionSelect } from '@/components/option-select'
import { Refusal } from '@/components/refusal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { formatMoney, formatUnitCost } from '@/lib/decimal'

import { ProductBundlePanel, ProductStockPanel } from './product-panels'

/**
 * One product: what it is, what it costs, where its stock is, and what it is made of.
 *
 * Its own route rather than a sheet over the list, because a product is something somebody links
 * to, returns to, and keeps open beside a supplier's website.
 */
export function ProductDetail() {
  const { id } = useParams<{ id: string }>()
  const { t, i18n } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // The route parameter is text. `Number()` would be banned here and rightly so — but a database
  // id is a count, not a value, and the lint rule's documented escape is to say so on the line.
  // eslint-disable-next-line no-restricted-syntax
  const productId = Number(id)

  const query = useProductControllerProduct(productId, {
    query: { enabled: Number.isInteger(productId) },
  })
  const product = query.data

  const units = useUnitsOfMeasure()
  const vatClasses = useVatClasses()
  const suppliers = useSuppliers()

  const editable = permissions.canEdit(Section.PRODUCTS)

  /**
   * Writes the PATCH response straight into the cache.
   *
   * Every per-field route returns the complete updated `ProductView`, so the answer to "what does
   * the record look like now" arrived with the response. Refetching would be a second round trip to
   * ask a question already answered, and a visible flicker while it happened.
   */
  const applyResponse = (updated: ProductView) => {
    queryClient.setQueryData(getProductControllerProductQueryKey(productId), updated)
    // The list holds a copy of this row. Marking it stale is enough — it refetches when next shown.
    void queryClient.invalidateQueries({ queryKey: ['/api/products'] })
  }

  const rename = useProductControllerRename()
  const changeEan = useProductControllerChangeEan()
  const changePrice = useProductControllerChangeSellingPrice()
  const changeUnit = useProductControllerChangeUnitOfMeasure()
  const changeVatClass = useProductControllerChangeVatClass()
  const changeSupplier = useProductControllerChangeSupplier()
  const changeSerialTracking = useProductControllerChangeSerialTracking()
  const deactivate = useProductControllerDeactivate()
  const reactivate = useProductControllerReactivate()

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  if (!product) return <p className="text-muted-foreground text-sm">{t('products.notFound')}</p>

  const supplierHidden = hiddenInResponse(product, ProtectedField.PRODUCT_SUPPLIER)
  const lastCostHidden = hiddenInResponse(product, ProtectedField.PRODUCT_LAST_PURCHASE_PRICE)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link to="/products" className="text-muted-foreground text-sm hover:underline">
            ← {t('products.backToList')}
          </Link>
          <h1 className="text-lg font-semibold">{product.name}</h1>
          <span className="text-muted-foreground text-sm">{product.sku}</span>
          {product.bundle && <Badge variant="secondary">{t('products.flag.bundle')}</Badge>}
          {product.serialTracked && (
            <Badge variant="secondary">{t('products.flag.serialTracked')}</Badge>
          )}
          {product.active === false && <Badge variant="outline">{t('products.flag.inactive')}</Badge>}
        </div>

        {editable &&
          (product.active === false ? (
            <Button
              size="sm"
              variant="outline"
              disabled={reactivate.isPending}
              onClick={() =>
                reactivate.mutate({ id: productId }, { onSuccess: () => void query.refetch() })
              }
            >
              {t('products.reactivate')}
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              disabled={deactivate.isPending}
              onClick={() => {
                if (window.confirm(t('products.deactivateConfirm'))) {
                  deactivate.mutate({ id: productId }, { onSuccess: () => void query.refetch() })
                }
              }}
            >
              {t('products.deactivate')}
            </Button>
          ))}
      </div>

      {/*
       * Why the refusal is here and not beside the button: the backend refuses a deactivation for a
       * reason that is a paragraph long — a product that is a component of an active bundle names
       * the bundle and says what to do about it — and this was rendering nothing at all, so the
       * button read as broken. `Refusal` is the same one every field editor below uses.
       */}
      <Refusal error={deactivate.error ?? reactivate.error} />

      <Card>
        <CardHeader>
          <CardTitle>{t('products.fields')}</CardTitle>
        </CardHeader>
        <CardContent>
          <FieldEditor
            label={t('products.column.name')}
            value={product.name ?? ''}
            display={product.name ?? <UnsetValue />}
            editable={editable}
            isValid={(value) => value.trim() !== ''}
            onSave={async (name) => {
              const updated = await rename.mutateAsync({ id: productId, data: { name } })
              applyResponse(updated)
            }}
          >
            {(draft, setDraft) => (
              <Input
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                aria-label={t('products.column.name')}
              />
            )}
          </FieldEditor>

          <FieldEditor
            label={t('products.column.ean')}
            value={product.ean ?? ''}
            display={product.ean ?? <UnsetValue />}
            editable={editable}
            onSave={async (ean) => {
              const updated = await changeEan.mutateAsync({ id: productId, data: { ean } })
              applyResponse(updated)
            }}
          >
            {(draft, setDraft) => (
              <Input
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                aria-label={t('products.column.ean')}
              />
            )}
          </FieldEditor>

          <FieldEditor<Money | undefined>
            label={t('products.column.sellingPrice')}
            value={product.sellingPrice}
            display={
              product.sellingPrice ? formatMoney(product.sellingPrice, i18n.language) : <UnsetValue />
            }
            editable={editable}
            isValid={(value) => value !== undefined}
            onSave={async (sellingPrice) => {
              if (!sellingPrice) return
              const updated = await changePrice.mutateAsync({
                id: productId,
                data: { sellingPrice },
              })
              applyResponse(updated)
            }}
          >
            {(draft, setDraft) => (
              <MoneyInput
                aria-label={t('products.column.sellingPrice')}
                value={draft}
                // The currency of the existing price. Never defaulted — the API refuses a Money
                // without one, and picking EUR here would be this screen inventing a fact.
                currency={draft?.currency ?? product.sellingPrice?.currency ?? 'EUR'}
                onValueChange={setDraft}
              />
            )}
          </FieldEditor>

          <FieldEditor<number | undefined>
            label={t('products.column.unit')}
            value={product.unitOfMeasure?.id}
            display={product.unitOfMeasure?.name ?? <UnsetValue />}
            editable={editable}
            isValid={(value) => value !== undefined}
            onSave={async (unitOfMeasureId) => {
              if (unitOfMeasureId === undefined) return
              const updated = await changeUnit.mutateAsync({
                id: productId,
                data: { unitOfMeasureId },
              })
              applyResponse(updated)
            }}
          >
            {(draft, setDraft) => (
              <OptionSelect
                aria-label={t('products.column.unit')}
                options={idOptions(units.items, (unit) => unit.name)}
                value={draft === undefined ? null : String(draft)}
                onValueChange={(value) => setDraft(idFrom(value))}
              />
            )}
          </FieldEditor>

          <FieldEditor<number | undefined>
            label={t('products.column.vatClass')}
            value={product.defaultVatClassId}
            display={
              nameFor(vatClasses, product.defaultVatClassId, (v) => v.description) ?? <UnsetValue />
            }
            // Editing needs the list to choose from. Without TAX_AND_CHARGES there is no list, so
            // the field is read-only and shows the id rather than pretending it can be changed.
            editable={editable && vatClasses.permitted}
            isValid={(value) => value !== undefined}
            onSave={async (vatClassId) => {
              if (vatClassId === undefined) return
              const updated = await changeVatClass.mutateAsync({
                id: productId,
                data: { vatClassId },
              })
              applyResponse(updated)
            }}
          >
            {(draft, setDraft) => (
              <OptionSelect
                aria-label={t('products.column.vatClass')}
                options={idOptions(vatClasses.items, (vatClass) => vatClass.description)}
                value={draft === undefined ? null : String(draft)}
                onValueChange={(value) => setDraft(idFrom(value))}
              />
            )}
          </FieldEditor>

          <FieldEditor<number | undefined>
            label={t('products.column.supplier')}
            value={product.supplierId}
            display={
              supplierHidden ? (
                <HiddenValue />
              ) : (
                (nameFor(suppliers, product.supplierId, (s) => s.name) ?? <UnsetValue />)
              )
            }
            editable={editable && suppliers.permitted && !supplierHidden}
            onSave={async (supplierId) => {
              const updated = await changeSupplier.mutateAsync({
                id: productId,
                data: { supplierId, supplierSku: product.supplierSku },
              })
              applyResponse(updated)
            }}
          >
            {(draft, setDraft) => (
              <OptionSelect
                aria-label={t('products.column.supplier')}
                options={idOptions(suppliers.items, (supplier) => supplier.name)}
                value={draft === undefined ? null : String(draft)}
                onValueChange={(value) => setDraft(idFrom(value))}
              />
            )}
          </FieldEditor>

          <FieldEditor<boolean>
            label={t('products.column.serialTracked')}
            value={product.serialTracked ?? false}
            display={product.serialTracked ? t('yes') : t('no')}
            editable={editable}
            onSave={async (serialTracked) => {
              const updated = await changeSerialTracking.mutateAsync({
                id: productId,
                data: { serialTracked },
              })
              applyResponse(updated)
            }}
          >
            {(draft, setDraft) => (
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={draft}
                  onChange={(event) => setDraft(event.target.checked)}
                  aria-label={t('products.column.serialTracked')}
                />
                {t('products.serialTrackedHint')}
              </label>
            )}
          </FieldEditor>

          {/* Derived from purchase invoices; there is no route that sets it, and no field that
              offers to. Six decimals, so formatUnitCost rather than formatMoney. */}
          <div className="flex items-baseline justify-between gap-4 py-2">
            <span className="text-muted-foreground w-48 shrink-0 text-sm">
              {t('products.column.lastPurchasePrice')}
            </span>
            <span className="flex-1 text-sm tabular-nums">
              {lastCostHidden ? (
                <HiddenValue />
              ) : product.lastPurchasePrice ? (
                formatUnitCost(product.lastPurchasePrice, i18n.language)
              ) : (
                <UnsetValue />
              )}
            </span>
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <ProductStockPanel productId={productId} />
        <ProductBundlePanel product={product} />
      </div>
    </div>
  )
}

/** A select's value is text; an id is a count. The rule's documented escape applies. */
function idFrom(value: string | null): number | undefined {
  // Base UI hands back null when a select is cleared; an empty selection is not an id.
  if (value === null || value === '') return undefined
  // eslint-disable-next-line no-restricted-syntax
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : undefined
}
