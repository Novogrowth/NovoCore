import { PlusIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useBundleControllerAllBundles } from '@/api/generated/endpoints/bundle/bundle'
import { useProductControllerProducts } from '@/api/generated/endpoints/product/product'
import { Section } from '@/api/generated/model'
import { useSuppliers } from '@/api/lookups'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { useListState } from '@/components/data-table/use-list-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import { productColumns } from './product-columns'

/**
 * Products, and the bundles among them.
 *
 * Two tabs over two endpoints and one table: `GET /api/products` and `GET /api/bundles` return the
 * same `ProductView`, so a bundle list is the same columns over a different query rather than a
 * second implementation of the same screen.
 *
 * Neither endpoint pages on the server today, so `DataTable` pages them in the browser — and will
 * stop doing so, without this file changing, on the first regeneration after the backend adds
 * paging. That is what `useListState` reads from the generated capability map.
 */
export function ProductsList() {
  const { t, i18n } = useTranslation('common')
  const permissions = usePermissions()
  const suppliers = useSuppliers()

  const [tab, setTab] = useState<'products' | 'bundles'>('products')
  // Active only, by default: an inactive product is a historical record rather than something
  // somebody is looking for.
  const [activeOnly, setActiveOnly] = useState(true)
  const [search, setSearch] = useState('')

  const list = useListState('GET /api/products')

  /*
   * The SKU and EAN filters are the endpoint's own parameters, not a filter applied to rows already
   * fetched: a search that only looks at the current page finds nothing on page two, and this list
   * is paged in the browser today.
   */
  const products = useProductControllerProducts(
    {
      ...(activeOnly ? { active: true } : {}),
      ...(search ? { sku: search } : {}),
      ...list.params,
    },
    { query: { enabled: tab === 'products' } },
  )

  const bundles = useBundleControllerAllBundles({ query: { enabled: tab === 'bundles' } })

  const query = tab === 'products' ? products : bundles
  const columns = productColumns(t, i18n.language, suppliers)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex gap-1" role="tablist">
          <Button
            role="tab"
            aria-selected={tab === 'products'}
            variant={tab === 'products' ? 'default' : 'ghost'}
            size="sm"
            onClick={() => setTab('products')}
          >
            {t('products.tab.products')}
          </Button>
          <Button
            role="tab"
            aria-selected={tab === 'bundles'}
            variant={tab === 'bundles' ? 'default' : 'ghost'}
            size="sm"
            onClick={() => setTab('bundles')}
          >
            {t('products.tab.bundles')}
          </Button>
        </div>

        {/* Creating needs a VAT class, and choosing one needs TAX_AND_CHARGES — so the action is
            absent for a role that could not complete the form, rather than present and doomed. */}
        {permissions.canEdit(Section.PRODUCTS) && permissions.canView(Section.TAX_AND_CHARGES) && (
          <Button size="sm" nativeButton={false} render={<Link to="/products/new" />}>
            <PlusIcon /> {t('products.create')}
          </Button>
        )}
      </div>

      {tab === 'products' && (
        <div className="flex flex-wrap items-end gap-4">
          <div className="space-y-1">
            <Label htmlFor="product-search">{t('products.filter.sku')}</Label>
            <Input
              id="product-search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('products.filter.skuPlaceholder')}
              className="w-56"
            />
          </div>
          <label className="flex items-center gap-2 pb-2 text-sm">
            <input
              type="checkbox"
              checked={activeOnly}
              onChange={(event) => setActiveOnly(event.target.checked)}
            />
            {t('products.filter.activeOnly')}
          </label>
        </div>
      )}

      <DataTable
        data={query.data}
        columns={columns}
        list={list}
        isLoading={query.isLoading}
        emptyMessage={t('products.empty')}
        getRowId={(row) => String(row.id)}
      />
    </div>
  )
}
