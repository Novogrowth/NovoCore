import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import {
  getUserControllerUserQueryKey,
  useUserControllerChangePassword,
  useUserControllerChangeRole,
  useUserControllerDeactivate,
  useUserControllerReactivate,
  useUserControllerRename,
  useUserControllerUser,
} from '@/api/generated/endpoints/user/user'
import { useRoleControllerRoles } from '@/api/generated/endpoints/role/role'
import { Section, type UserView } from '@/api/generated/model'
import { idOptions } from '@/api/lookups'
import { usePermissions } from '@/auth/permissions'
import { FieldEditor, UnsetValue } from '@/components/field-editor/field-editor'
import { OptionSelect } from '@/components/option-select'
import { PasswordHandoff } from '@/components/password/password-handoff'
import { Refusal } from '@/components/refusal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { generatePassword } from '@/lib/generated-password'

/**
 * One account.
 *
 * **Three of its four fields are read-only or fixed, and each for a different reason worth keeping
 * apart.** The username cannot be changed because there is no route — it is what the audit log and
 * every record's `created_by` refer to. The language is the account holder's own preference, set
 * through `PATCH /api/me/language` by whoever is signed in, so an administrator sees it and does not
 * set it. The role is editable in general and **fixed on your own account**: `UserServiceImpl`
 * refuses a caller changing their own role, because moving yourself into a role you can edit is one
 * person granting themselves anything.
 *
 * **The password is not a field at all.** It is never displayed, never returned by any route, and
 * setting one is a hand-off with its own dialog — see `PasswordHandoff`. `UserView` has no hash
 * field and no accessor anywhere returns one, so there is nothing here to render even in principle.
 */
export function UserDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // The route parameter is text. A database id is a count, not a value.
  // eslint-disable-next-line no-restricted-syntax
  const userId = Number(id)

  const query = useUserControllerUser(userId, {
    query: { enabled: Number.isInteger(userId) },
  })
  const user = query.data

  const editable = permissions.canEdit(Section.USERS_AND_ROLES)
  // Only an active role can be assigned — `requireActiveRole` refuses an inactive one — so an
  // inactive role must not be offered as a choice.
  const roles = useRoleControllerRoles({ active: true }, { query: { enabled: editable } })

  const applyResponse = (updated: UserView) => {
    queryClient.setQueryData(getUserControllerUserQueryKey(userId), updated)
    void queryClient.invalidateQueries({ queryKey: ['/api/users'] })
  }

  const rename = useUserControllerRename()
  const changeRole = useUserControllerChangeRole()
  const deactivate = useUserControllerDeactivate()
  const reactivate = useUserControllerReactivate()
  const changePassword = useUserControllerChangePassword()

  /*
   * The generated password, held in one component's state and nowhere else.
   *
   * Not a query key, not written to the query cache, not in the URL. `applied` is what the dialog
   * gates its close on: before the backend has accepted it, abandoning costs nothing; afterwards,
   * this is the only time anybody will ever see the value.
   */
  const [handoff, setHandoff] = useState<string | undefined>()
  const [applied, setApplied] = useState(false)

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  if (!user) return <p className="text-muted-foreground text-sm">{t('users.notFound')}</p>

  const isSelf = userId === permissions.userId

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link to="/users" className="text-muted-foreground text-sm hover:underline">
            ← {t('users.backToList')}
          </Link>
          <h1 className="text-lg font-semibold">{user.displayName ?? user.username}</h1>
          {isSelf && <Badge variant="outline">{t('users.flag.you')}</Badge>}
          {user.active === false && <Badge variant="outline">{t('users.flag.inactive')}</Badge>}
        </div>

        {editable && (
          <div className="flex gap-2">
            <Button
              size="sm"
              variant="outline"
              onClick={() => {
                changePassword.reset()
                setApplied(false)
                setHandoff(generatePassword())
              }}
            >
              {t('users.password.action')}
            </Button>

            {user.active === false ? (
              <Button
                size="sm"
                variant="outline"
                disabled={reactivate.isPending}
                onClick={() =>
                  reactivate.mutate({ id: userId }, { onSuccess: () => void query.refetch() })
                }
              >
                {t('users.reactivate')}
              </Button>
            ) : (
              <Button
                size="sm"
                variant="outline"
                disabled={deactivate.isPending}
                onClick={() => {
                  if (window.confirm(t('users.deactivateConfirm'))) {
                    deactivate.mutate({ id: userId }, { onSuccess: () => void query.refetch() })
                  }
                }}
              >
                {t('users.deactivate')}
              </Button>
            )}
          </div>
        )}
      </div>

      <Refusal error={deactivate.error ?? reactivate.error} />

      <Card>
        <CardHeader>
          <CardTitle>{t('users.fields')}</CardTitle>
        </CardHeader>
        <CardContent>
          {/* Plain text: there is no route that changes a username, deliberately. */}
          <div className="border-b py-2">
            <div className="flex items-baseline justify-between gap-4">
              <Label className="text-muted-foreground w-48 shrink-0 text-sm">
                {t('users.column.username')}
              </Label>
              <span className="flex-1 text-sm">{user.username ?? <UnsetValue />}</span>
            </div>
            {editable && (
              <p className="text-muted-foreground mt-1 pl-52 text-sm">{t('users.usernameFixed')}</p>
            )}
          </div>

          <FieldEditor
            label={t('users.column.displayName')}
            value={user.displayName ?? ''}
            display={user.displayName ?? <UnsetValue />}
            editable={editable}
            isValid={(value) => value.trim() !== ''}
            onSave={async (displayName) => {
              applyResponse(await rename.mutateAsync({ id: userId, data: { displayName } }))
            }}
          >
            {(draft, setDraft) => (
              <Input
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                aria-label={t('users.column.displayName')}
              />
            )}
          </FieldEditor>

          <FieldEditor
            label={t('users.column.role')}
            value={user.role?.id === undefined ? '' : String(user.role.id)}
            display={user.role?.name ?? <UnsetValue />}
            editable={editable}
            // Editable in general, fixed on this record — the `lockedReason` case exactly. Shown
            // disabled rather than hidden, because the role is editable on every other account.
            {...(isSelf ? { lockedReason: t('users.locked.ownAccount') } : {})}
            isValid={(value) => value !== ''}
            onSave={async (roleId) => {
              applyResponse(
                await changeRole.mutateAsync({
                  id: userId,
                  // A select's value is text; a role id is a count. The documented escape.
                  // eslint-disable-next-line no-restricted-syntax
                  data: { roleId: Number(roleId) },
                }),
              )
            }}
          >
            {(draft, setDraft) => (
              <OptionSelect
                aria-label={t('users.column.role')}
                options={idOptions(roles.data?.items ?? [], (role) => role.name)}
                value={draft === '' ? null : draft}
                onValueChange={(value) => setDraft(value ?? '')}
              />
            )}
          </FieldEditor>

          {/* The account holder's own preference, not an administrator's setting. */}
          <div className="border-b py-2">
            <div className="flex items-baseline justify-between gap-4">
              <Label className="text-muted-foreground w-48 shrink-0 text-sm">
                {t('language.label')}
              </Label>
              <span className="flex-1 text-sm">{user.language ?? <UnsetValue />}</span>
            </div>
            {editable && (
              <p className="text-muted-foreground mt-1 pl-52 text-sm">{t('users.languageIsTheirs')}</p>
            )}
          </div>
        </CardContent>
      </Card>

      {handoff !== undefined && (
        <PasswordHandoff
          password={handoff}
          username={user.username ?? ''}
          applied={applied}
          applying={changePassword.isPending}
          error={changePassword.error}
          onApply={() =>
            changePassword.mutate(
              { id: userId, data: { password: handoff } },
              { onSuccess: () => setApplied(true) },
            )
          }
          onCancel={() => setHandoff(undefined)}
          onDone={() => setHandoff(undefined)}
        />
      )}
    </div>
  )
}
