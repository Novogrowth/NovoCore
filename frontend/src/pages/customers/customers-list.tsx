import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useCustomerControllerCustomers } from '@/api/generated/endpoints/customer/customer'
import { Section } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { useListState } from '@/components/data-table/use-list-state'
import { PlusIcon } from '@/components/icons'
import { Button } from '@/components/ui/button'

import { customerColumns } from './customer-columns'

/**
 * Customers.
 *
 * Structurally the suppliers list. The endpoint's only parameter is `active`; `by-vat-number` is an
 * exact lookup for the AADE/VIES adapter and `match-suggestions` belongs to the never-silently-guess
 * matching flow, so neither is a filter box here.
 */
export function CustomersList() {
  const { t } = useTranslation('common')
  const permissions = usePermissions()

  const [activeOnly, setActiveOnly] = useState(true)

  const list = useListState('GET /api/customers')

  const customers = useCustomerControllerCustomers({
    ...(activeOnly ? { active: true } : {}),
    ...list.params,
  })

  const columns = customerColumns(t)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input
            type="checkbox"
            checked={activeOnly}
            onChange={(event) => setActiveOnly(event.target.checked)}
          />
          {t('customers.filter.activeOnly')}
        </label>

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
