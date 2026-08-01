import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { useRoleControllerRoles } from '@/api/generated/endpoints/role/role'
import { useUserControllerCreate } from '@/api/generated/endpoints/user/user'
import { idOptions } from '@/api/lookups'
import { OptionSelect } from '@/components/option-select'
import { PasswordHandoff } from '@/components/password/password-handoff'
import { Refusal } from '@/components/refusal'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { generatePassword } from '@/lib/generated-password'

/**
 * Creating an account.
 *
 * **The first password is generated here and handed over once**, by the same dialog a reset uses.
 * `NewUser.rawPassword` is required by `PasswordPolicy`, so an account cannot be created without
 * one — there is no "send them an invitation" route — and inventing a memorable password on
 * somebody else's behalf is how a whole office ends up sharing a pattern.
 *
 * **The hand-off opens only after the backend has answered `201`.** Showing a password before the
 * account exists would mean an operator copying a value that a refused request never set.
 *
 * ⚠️ **`username` is normalised and constrained by the backend**: lower-cased and trimmed, then
 * required to match `[a-z0-9._-]{3,100}`. The refusal says so in full, so this form does not
 * re-implement the pattern — it would be a second copy of a rule that already explains itself.
 */
export function UserCreate() {
  const { t } = useTranslation('common')
  const navigate = useNavigate()

  const roles = useRoleControllerRoles({ active: true })
  const create = useUserControllerCreate()

  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [roleId, setRoleId] = useState<string | null>(null)

  /** Generated once per submission, and never re-generated behind the operator's back. */
  const [password, setPassword] = useState<string | undefined>()
  const [createdId, setCreatedId] = useState<number | undefined>()

  const complete = username.trim() !== '' && displayName.trim() !== '' && roleId !== null

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!complete || roleId === null) return

    const generated = generatePassword()
    setPassword(generated)

    create.mutate(
      {
        data: {
          username: username.trim(),
          displayName: displayName.trim(),
          rawPassword: generated,
          // A select's value is text; a role id is a count. The documented escape.
          // eslint-disable-next-line no-restricted-syntax
          roleId: Number(roleId),
        },
      },
      { onSuccess: (user) => setCreatedId(user.id) },
    )
  }

  return (
    <>
      <Card className="max-w-2xl">
        <CardHeader>
          <CardTitle>{t('users.create')}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1">
                <Label htmlFor="user-username">{t('users.column.username')}</Label>
                <Input
                  id="user-username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="user-display-name">{t('users.column.displayName')}</Label>
                <Input
                  id="user-display-name"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  required
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="user-role">{t('users.column.role')}</Label>
                <OptionSelect
                  id="user-role"
                  options={idOptions(roles.data?.items ?? [], (role) => role.name)}
                  value={roleId}
                  onValueChange={setRoleId}
                />
              </div>
            </div>

            <p className="text-muted-foreground text-sm">{t('users.password.generatedOnCreate')}</p>

            <Refusal error={create.error} />

            <div className="flex gap-2">
              <Button type="submit" disabled={!complete || create.isPending}>
                {create.isPending ? t('users.creating') : t('users.create')}
              </Button>
              <Button type="button" variant="ghost" onClick={() => void navigate('/users')}>
                {t('field.cancel')}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {/* Opens only once the account exists, and is the one time this value is ever on screen. */}
      {createdId !== undefined && password !== undefined && (
        <PasswordHandoff
          applied
          password={password}
          username={username.trim()}
          onDone={() => void navigate(`/users/${createdId}`)}
        />
      )}
    </>
  )
}
