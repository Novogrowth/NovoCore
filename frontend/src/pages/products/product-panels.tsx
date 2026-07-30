import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import {
  useBundleControllerComponents,
  useBundleControllerInBundles,
} from '@/api/generated/endpoints/bundle/bundle'
import { useProductControllerStock } from '@/api/generated/endpoints/product/product'
import type { ProductView } from '@/api/generated/model'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatQuantity } from '@/lib/decimal'

/**
 * Where this product's stock is, by location.
 *
 * `StockLevels.byLocation` is a map of `StockLocation` to `Quantity` — the keys are enum values
 * with labels in both languages already, and the values are strings at six decimals. Read-only:
 * stock moves through receipts, sales and write-offs, never by editing a number on a product.
 */
export function ProductStockPanel({ productId }: { productId: number }) {
  const { t, i18n } = useTranslation('common')
  const { t: tEnum } = useTranslation('enums')
  const stock = useProductControllerStock(productId)

  const byLocation = Object.entries(stock.data?.byLocation ?? {})

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('products.stock')}</CardTitle>
      </CardHeader>
      <CardContent>
        {stock.isLoading ? (
          <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
        ) : byLocation.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t('products.noStock')}</p>
        ) : (
          <dl className="space-y-1 text-sm">
            {byLocation.map(([location, quantity]) => (
              <div key={location} className="flex justify-between gap-4">
                <dt className="text-muted-foreground">{tEnum(`StockLocation.${location}`)}</dt>
                <dd className="tabular-nums">{formatQuantity(quantity, i18n.language)}</dd>
              </div>
            ))}
          </dl>
        )}
      </CardContent>
    </Card>
  )
}

/**
 * What this product is made of, or what it is part of.
 *
 * **Read-only in this pass.** `PUT` and `DELETE /api/products/{id}/components` exist, but editing a
 * bundle is a different interaction from editing a field — quantities per component, adding and
 * removing rows — and folding it in here would make the per-field pattern the screen exists to
 * establish harder to read. It is the next piece of work, not this one.
 */
export function ProductBundlePanel({ product }: { product: ProductView }) {
  const { t, i18n } = useTranslation('common')
  const isBundle = product.bundle === true
  const productId = product.id ?? 0

  const components = useBundleControllerComponents(productId, { query: { enabled: isBundle } })
  const inBundles = useBundleControllerInBundles(productId, { query: { enabled: !isBundle } })

  const rows = isBundle ? (components.data?.items ?? []) : (inBundles.data?.items ?? [])

  return (
    <Card>
      <CardHeader>
        <CardTitle>{isBundle ? t('products.components') : t('products.inBundles')}</CardTitle>
      </CardHeader>
      <CardContent>
        {rows.length === 0 ? (
          <p className="text-muted-foreground text-sm">
            {isBundle ? t('products.noComponents') : t('products.notInBundles')}
          </p>
        ) : (
          <ul className="space-y-1 text-sm">
            {rows.map((row) => {
              // Looking at a bundle lists what is in it; looking at a component lists the bundles
              // it belongs to. Either way the OTHER product is the one worth linking to.
              const otherId = isBundle ? row.componentProductId : row.bundleProductId
              return (
                <li
                  key={`${row.bundleProductId}-${row.componentProductId}`}
                  className="flex items-baseline justify-between gap-4"
                >
                  <Link to={`/products/${otherId}`} className="hover:underline">
                    {isBundle ? (row.componentName ?? row.componentSku) : `#${otherId}`}
                  </Link>
                  <span className="text-muted-foreground tabular-nums">
                    {row.quantityPerBundle
                      ? formatQuantity(row.quantityPerBundle, i18n.language)
                      : null}{' '}
                    {row.componentUnitOfMeasure?.code}
                  </span>
                </li>
              )
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
