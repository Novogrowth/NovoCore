import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useRoleControllerRoles } from '@/api/generated/endpoints/role/role'
import { Section } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { SearchFilter } from '@/components/data-table/search-filter'
import { useListState } from '@/components/data-table/use-list-state'
import { PlusIcon } from '@/components/icons'
import { Button } from '@/components/ui/button'

import { roleColumns } from './role-columns'

/**
 * Roles.
 *
 * A short list — three rows today — and deliberately its own screen rather than a tab on Users.
 * They are two entities that reference each other, and the questions differ: "who is in this role"
 * is asked from a role, "what may this person do" from an account.
 *
 * The search box matches the name **and the description**, which is where the answer to "which role
 * lets somebody do X" actually lives — a role name rarely says.
 */
export function RolesList() {
  const { t } = useTranslation('common')
  const permissions = usePermissions()

  const [activeOnly, setActiveOnly] = useState(true)
  const [search, setSearch] = useState<string | undefined>(undefined)

  const list = useListState('GET /api/roles')

  const roles = useRoleControllerRoles({
    ...(activeOnly ? { active: true } : {}),
    ...(search ? { search } : {}),
    ...list.params,
  })

  const columns = roleColumns(t)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-4">
          <SearchFilter
            id="role-search"
            onChange={setSearch}
            label={t('roles.filter.search')}
            placeholder={t('roles.filter.searchPlaceholder')}
          />
          <label className="flex items-center gap-2 pb-2 text-sm">
            <input
              type="checkbox"
              checked={activeOnly}
              onChange={(event) => setActiveOnly(event.target.checked)}
            />
            {t('roles.filter.activeOnly')}
          </label>
        </div>

        {permissions.canEdit(Section.USERS_AND_ROLES) && (
          <Button size="sm" nativeButton={false} render={<Link to="/roles/new" />}>
            <PlusIcon /> {t('roles.create')}
          </Button>
        )}
      </div>

      <DataTable
        data={roles.data}
        columns={columns}
        list={list}
        isLoading={roles.isLoading}
        emptyMessage={t('roles.empty')}
        getRowId={(row) => String(row.id)}
      />
    </div>
  )
}
