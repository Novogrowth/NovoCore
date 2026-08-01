import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useUserControllerUsers } from '@/api/generated/endpoints/user/user'
import { Section } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { useListState } from '@/components/data-table/use-list-state'
import { PlusIcon } from '@/components/icons'
import { Button } from '@/components/ui/button'

import { userColumns } from './user-columns'

/**
 * User accounts.
 *
 * One row per person who can sign in. Deactivated accounts are kept rather than deleted — the audit
 * log and every record's `created_by` refer to the username, and those have to stay explicable — so
 * the active-only filter is the normal view rather than the whole story.
 */
export function UsersList() {
  const { t } = useTranslation('common')
  const permissions = usePermissions()

  const [activeOnly, setActiveOnly] = useState(true)

  const list = useListState('GET /api/users')

  const users = useUserControllerUsers({
    ...(activeOnly ? { active: true } : {}),
    ...list.params,
  })

  const columns = userColumns(t)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input
            type="checkbox"
            checked={activeOnly}
            onChange={(event) => setActiveOnly(event.target.checked)}
          />
          {t('users.filter.activeOnly')}
        </label>

        {permissions.canEdit(Section.USERS_AND_ROLES) && (
          <Button size="sm" nativeButton={false} render={<Link to="/users/new" />}>
            <PlusIcon /> {t('users.create')}
          </Button>
        )}
      </div>

      <DataTable
        data={users.data}
        columns={columns}
        list={list}
        isLoading={users.isLoading}
        emptyMessage={t('users.empty')}
        getRowId={(row) => String(row.id)}
      />
    </div>
  )
}
