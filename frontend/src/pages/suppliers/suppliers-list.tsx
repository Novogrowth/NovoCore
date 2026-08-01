import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useSupplierControllerSuppliers } from '@/api/generated/endpoints/supplier/supplier'
import { Section } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { SearchFilter } from '@/components/data-table/search-filter'
import { useListState } from '@/components/data-table/use-list-state'
import { PlusIcon } from '@/components/icons'
import { Button } from '@/components/ui/button'

import { supplierColumns } from './supplier-columns'

/**
 * Suppliers.
 *
 * The second master-data screen, and deliberately the same shape as Products: one list endpoint,
 * an active-only default, and a table that pages itself. `GET /api/suppliers` does not page on the
 * server today, so `DataTable` pages it in the browser and will stop doing so — without this file
 * changing — on the first regeneration after the backend adds paging.
 *
 * The search box sends `search=` — name, email and phone, matched anywhere, case- and
 * accent-insensitively. **It is not `match-suggestions` under another name**, even though the two
 * look at the same three columns: that route feeds the never-silently-guess matching flow, where
 * each candidate is a proposed identity for a party on an incoming document and a human confirms it
 * one at a time. `by-vat-number` stays exact, because it is the authoritative auto-link.
 */
export function SuppliersList() {
  const { t } = useTranslation('common')
  const permissions = usePermissions()

  // Active only, by default: an inactive supplier is a historical record rather than something
  // somebody is looking for.
  const [activeOnly, setActiveOnly] = useState(true)
  const [search, setSearch] = useState<string | undefined>(undefined)

  const list = useListState('GET /api/suppliers')

  const suppliers = useSupplierControllerSuppliers({
    ...(activeOnly ? { active: true } : {}),
    ...(search ? { search } : {}),
    ...list.params,
  })

  const columns = supplierColumns(t)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-4">
          <SearchFilter
            id="supplier-search"
            onChange={setSearch}
            label={t('suppliers.filter.search')}
            placeholder={t('suppliers.filter.searchPlaceholder')}
          />
          <label className="flex items-center gap-2 pb-2 text-sm">
            <input
              type="checkbox"
              checked={activeOnly}
              onChange={(event) => setActiveOnly(event.target.checked)}
            />
            {t('suppliers.filter.activeOnly')}
          </label>
        </div>

        {/* Creating needs a VAT status, and an exempt one needs an exemption reason from
            TAX_AND_CHARGES — so the action is absent for a role that could not complete the form,
            rather than present and doomed. The same rule the products screen applies. */}
        {permissions.canEdit(Section.SUPPLIERS) &&
          permissions.canView(Section.TAX_AND_CHARGES) && (
            <Button size="sm" nativeButton={false} render={<Link to="/suppliers/new" />}>
              <PlusIcon /> {t('suppliers.create')}
            </Button>
          )}
      </div>

      <DataTable
        data={suppliers.data}
        columns={columns}
        list={list}
        isLoading={suppliers.isLoading}
        emptyMessage={t('suppliers.empty')}
        getRowId={(row) => String(row.id)}
      />
    </div>
  )
}
