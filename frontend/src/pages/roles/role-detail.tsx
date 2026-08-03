import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import {
  getRoleControllerRoleQueryKey,
  useRoleControllerDeactivate,
  useRoleControllerHolders,
  useRoleControllerReactivate,
  useRoleControllerDescribe,
  useRoleControllerRename,
  useRoleControllerRole,
} from '@/api/generated/endpoints/role/role'
import { Section, type RoleView } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { FieldEditor, UnsetValue } from '@/components/field-editor/field-editor'
import { Refusal } from '@/components/refusal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'

import { userColumns } from '@/pages/users/user-columns'

import { RoleGrants } from './role-grants'

/**
 * One role: what it is called, what it may see, and who holds it.
 *
 * **Not the master-data screen with different fields.** A role is a permission document, so the
 * bulk of this page is the grid in `RoleGrants` rather than a list of attributes, and the holders
 * table at the bottom is not decoration: `POST /api/roles/{id}/deactivate` is **refused while
 * anybody still holds the role**, and a refusal naming a count rather than the people is a dead end.
 * `GET /api/roles/{id}/users` exists for exactly that, and includes deactivated accounts, because
 * they hold the role too and are equally in the way of retiring it.
 *
 * The description is an ordinary `FieldEditor`. ⚠️ **It was read-only until 2026-08-03**, rendered
 * as plain text with the reason beside it — deliberately NOT through `FieldEditor` with
 * `editable: false`, because in this application that means *"not yours to edit"* and would have
 * told a full-access administrator something false. There was simply no route: backend queue item
 * 5, now `PATCH /api/roles/{id}/description`. Both notes came out with it, here and on the create
 * form, which is what that item said would happen when it landed.
 */
export function RoleDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // The route parameter is text. A database id is a count, not a value.
  // eslint-disable-next-line no-restricted-syntax
  const roleId = Number(id)

  const query = useRoleControllerRole(roleId, {
    query: { enabled: Number.isInteger(roleId) },
  })
  const role = query.data

  const holders = useRoleControllerHolders(roleId, {
    query: { enabled: Number.isInteger(roleId) },
  })

  const editable = permissions.canEdit(Section.USERS_AND_ROLES)

  const applyResponse = (updated: RoleView) => {
    queryClient.setQueryData(getRoleControllerRoleQueryKey(roleId), updated)
    void queryClient.invalidateQueries({ queryKey: ['/api/roles'] })
  }

  const rename = useRoleControllerRename()
  const describe = useRoleControllerDescribe()
  const deactivate = useRoleControllerDeactivate()
  const reactivate = useRoleControllerReactivate()

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  if (!role) return <p className="text-muted-foreground text-sm">{t('roles.notFound')}</p>

  const systemRole = role.systemRole === true
  const ownRole = roleId === permissions.roleId

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link to="/roles" className="text-muted-foreground text-sm hover:underline">
            ← {t('roles.backToList')}
          </Link>
          <h1 className="text-lg font-semibold">{role.name}</h1>
          {systemRole && (
            <Badge variant="secondary" title={t('roles.flag.systemTitle')}>
              {t('roles.flag.system')}
            </Badge>
          )}
          {role.fullAccess === true && <Badge variant="secondary">{t('roles.flag.fullAccess')}</Badge>}
          {ownRole && <Badge variant="outline">{t('roles.flag.yours')}</Badge>}
          {role.active === false && <Badge variant="outline">{t('roles.flag.inactive')}</Badge>}
        </div>

        {editable &&
          (role.active === false ? (
            <Button
              size="sm"
              variant="outline"
              disabled={reactivate.isPending}
              onClick={() =>
                reactivate.mutate({ id: roleId }, { onSuccess: () => void query.refetch() })
              }
            >
              {t('roles.reactivate')}
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              // Disabled only for a system role, whose refusal is structural. Deactivating a role
              // somebody holds is left live on purpose: the backend answers 422 naming the count,
              // and the holders below name the people — which is a better answer than this screen
              // guessing at one.
              disabled={deactivate.isPending || systemRole}
              onClick={() => {
                if (window.confirm(t('roles.deactivateConfirm'))) {
                  deactivate.mutate({ id: roleId }, { onSuccess: () => void query.refetch() })
                }
              }}
            >
              {t('roles.deactivate')}
            </Button>
          ))}
      </div>

      <Refusal error={deactivate.error ?? reactivate.error} />

      <Card>
        <CardHeader>
          <CardTitle>{t('roles.fields')}</CardTitle>
        </CardHeader>
        <CardContent>
          <FieldEditor
            label={t('roles.column.name')}
            value={role.name ?? ''}
            display={role.name ?? <UnsetValue />}
            editable={editable}
            {...(systemRole
              ? { lockedReason: t('roles.locked.systemRole', { name: role.name ?? '' }) }
              : {})}
            isValid={(value) => value.trim() !== ''}
            onSave={async (name) => {
              applyResponse(await rename.mutateAsync({ id: roleId, data: { name } }))
            }}
          >
            {(draft, setDraft) => (
              <Input
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                aria-label={t('roles.column.name')}
              />
            )}
          </FieldEditor>

          <FieldEditor
            label={t('roles.column.description')}
            value={role.description ?? ''}
            display={role.description ?? <UnsetValue />}
            editable={editable}
            {...(systemRole
              ? { lockedReason: t('roles.locked.systemRole', { name: role.name ?? '' }) }
              : {})}
            onSave={async (description) => {
              applyResponse(await describe.mutateAsync({ id: roleId, data: { description } }))
            }}
          >
            {(draft, setDraft) => (
              <Input
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                aria-label={t('roles.column.description')}
              />
            )}
          </FieldEditor>
        </CardContent>
      </Card>

      <RoleGrants
        role={role}
        roleId={roleId}
        editable={editable}
        permissions={permissions}
        onChanged={applyResponse}
      />

      <Card>
        <CardHeader>
          <CardTitle>{t('roles.holders')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <p className="text-muted-foreground text-sm">{t('roles.holdersBody')}</p>
          <DataTable
            data={holders.data}
            columns={userColumns(t)}
            isLoading={holders.isLoading}
            emptyMessage={t('roles.noHolders')}
            getRowId={(row) => String(row.id)}
          />
        </CardContent>
      </Card>
    </div>
  )
}
