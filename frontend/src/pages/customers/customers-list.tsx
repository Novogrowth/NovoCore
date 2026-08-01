import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useCustomerControllerCustomers } from '@/api/generated/endpoints/customer/customer'
import { Section } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { SearchFilter } from '@/components/data-table/search-filter'
import { useListState } from '@/components/data-table/use-list-state'
import { PlusIcon } from '@/components/icons'
import { Button } from '@/components/ui/button'

import { customerColumns } from './customer-columns'

/**
 * Customers.
 *
 * Structurally the suppliers list. The search box sends `search=` — name, ΑΦΜ, email and phone,
 * matched anywhere. A partial ΑΦΜ read off a document is exactly what it is for.
 *
 * `by-vat-number` is untouched and stays exact: it is the authoritative auto-link for the AADE/VIES
 * adapter, and the reason it may be applied without asking anybody is that it cannot match
 * approximately. `match-suggestions` likewise belongs to the never-silently-guess flow.
 */
export function CustomersList() {
  const { t } = useTranslation('common')
  const permissions = usePermissions()

  const [activeOnly, setActiveOnly] = useState(true)
  const [search, setSearch] = useState<string | undefined>(undefined)

  const list = useListState('GET /api/customers')

  const customers = useCustomerControllerCustomers({
    ...(activeOnly ? { active: true } : {}),
    ...(search ? { search } : {}),
    ...list.params,
  })

  const columns = customerColumns(t)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-4">
          <SearchFilter
            id="customer-search"
            onChange={setSearch}
            label={t('customers.filter.search')}
            placeholder={t('customers.filter.searchPlaceholder')}
          />
          <label className="flex items-center gap-2 pb-2 text-sm">
            <input
              type="checkbox"
              checked={activeOnly}
              onChange={(event) => setActiveOnly(event.target.checked)}
            />
            {t('customers.filter.activeOnly')}
          </label>
        </div>

        {/* Creating needs a VAT status, and an exempt one needs an exemption reason from
            TAX_AND_CHARGES — so the action is absent for a role that could not complete the form. */}
        {permissions.canEdit(Section.CUSTOMERS) &&
          permissions.canView(Section.TAX_AND_CHARGES) && (
            <Button size="sm" nativeButton={false} render={<Link to="/customers/new" />}>
              <PlusIcon /> {t('customers.create')}
            </Button>
          )}
      </div>

      <DataTable
        data={customers.data}
        columns={columns}
        list={list}
        isLoading={customers.isLoading}
        emptyMessage={t('customers.empty')}
        getRowId={(row) => String(row.id)}
      />
    </div>
  )
}
