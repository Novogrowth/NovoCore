import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { useRoleControllerCreate } from '@/api/generated/endpoints/role/role'
import { Refusal } from '@/components/refusal'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * Creating a role — a name and a description, and nothing else.
 *
 * **That is not a simplification, it is the API.** `NewRole` carries `name` and `description` only:
 * a role **cannot** be created with grants, and cannot be created full-access at all. Creating a
 * role and granting it access are separate, individually audited acts, which is the more useful
 * trail when the later question is "when did this role gain Settings?".
 *
 * So the form says so rather than pretending otherwise, and lands on the new role's own page, where
 * the grid is — because a role created here grants **nothing** until somebody does the second half.
 */
export function RoleCreate() {
  const { t } = useTranslation('common')
  const navigate = useNavigate()

  const create = useRoleControllerCreate()

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')

  const complete = name.trim() !== ''

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!complete) return

    create.mutate(
      {
        data: {
          name: name.trim(),
          ...(description.trim() ? { description: description.trim() } : {}),
        },
      },
      { onSuccess: (role) => void navigate(`/roles/${role.id}`) },
    )
  }

  return (
    <Card className="max-w-2xl">
      <CardHeader>
        <CardTitle>{t('roles.create')}</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-1">
            <Label htmlFor="role-name">{t('roles.column.name')}</Label>
            <Input id="role-name" value={name} onChange={(e) => setName(e.target.value)} required />
          </div>

          <div className="space-y-1">
            <Label htmlFor="role-description">{t('roles.column.description')}</Label>
            <Input
              id="role-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
            {/* Said here because there is no way to change it later — see `RoleDetail`. */}
            <p className="text-muted-foreground text-sm">{t('roles.descriptionOnlyNow')}</p>
          </div>

          <p className="text-muted-foreground text-sm">{t('roles.createGrantsLater')}</p>

          <Refusal error={create.error} />

          <div className="flex gap-2">
            <Button type="submit" disabled={!complete || create.isPending}>
              {create.isPending ? t('roles.creating') : t('roles.create')}
            </Button>
            <Button type="button" variant="ghost" onClick={() => void navigate('/roles')}>
              {t('field.cancel')}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
