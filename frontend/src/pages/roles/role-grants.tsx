import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useUserControllerSections } from '@/api/generated/endpoints/user/user'
import {
  useRoleControllerGrant,
  useRoleControllerRestrictField,
} from '@/api/generated/endpoints/role/role'
import {
  AccessLevel,
  ProtectedField,
  Section,
  type RoleView,
  type SectionView,
} from '@/api/generated/model'
import type { Permissions } from '@/auth/permissions'
import { Refusal } from '@/components/refusal'
import { SegmentedControl, type SegmentedOption } from '@/components/segmented-control'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { WarningCircleIcon } from '@/components/icons'

/**
 * What a role may see, as a grid — sections down, {none, view, full} across.
 *
 * **A grid because `sectionGrants` is a map, not a list.** Seventeen sections each holding one of
 * three values is a table of rows that all look alike, and the whole value of showing it that way is
 * that a role reads in one pass. A form of seventeen selects hides two thirds of every answer behind
 * a click.
 *
 * **The rows come from `GET /api/sections`, never from the role's own map.** A section a role has
 * never been granted has no key in `sectionGrants`, so building the grid from the role would draw
 * only the rows somebody had already touched — and the missing rows are exactly the ones an
 * administrator is looking for. `/api/sections` exists for this: it is the catalogue, and it carries
 * `available` so a section nobody has built yet can be granted and *say* that it leads nowhere.
 *
 * **Each cell is its own request.** `PUT …/grants/{section}` replaces one cell and is idempotent,
 * and one request per change is the same reasoning `FieldEditor` is built on: a batched grid invents
 * partial states the backend has no transaction to prevent.
 *
 * ## What is unavailable, and why it is shown rather than hidden
 *
 * Three rules, and the client can evaluate all three before sending anything:
 *
 * - **A system role cannot be edited at all.** Owner and Admin are refused by `editableRole`, which
 *   is what stops `USERS_AND_ROLES` being removed from the last role that has it.
 * - **Your own role cannot be edited by you.** One person granting themselves access nobody else
 *   approved is the escalation `refuseIfCallerHolds` closes; it needs a second administrator.
 * - **You cannot confer a level you do not hold.** Per-section, per-level — a caller holding `VIEW`
 *   on Sales may grant `NONE` and `VIEW` there and not `FULL`. **`NONE` is never locked**: revoking
 *   is always allowed, and must not require the access being taken away.
 *
 * ⚠️ **These reasons are mirrors of backend rules and can drift from them.** They are here to stop
 * the screen offering a request whose refusal is already known — not to replace it: every one of
 * these guards answers `422` with a fuller sentence than any of these, and `Refusal` shows that
 * sentence whenever a request does get sent. The mirror is the weaker copy, deliberately.
 */

const RANK: Record<AccessLevel, number> = {
  [AccessLevel.NONE]: 0,
  [AccessLevel.VIEW]: 1,
  [AccessLevel.FULL]: 2,
}

interface RoleGrantsProps {
  role: RoleView
  /** From the route, so no request is built on an id the response happened to omit. */
  roleId: number
  /** FULL on `USERS_AND_ROLES`. False renders the grid read-only, with no controls at all. */
  editable: boolean
  permissions: Permissions
  onChanged: (updated: RoleView) => void
}

export function RoleGrants({ role, roleId, editable, permissions, onChanged }: RoleGrantsProps) {
  const { t } = useTranslation('common')
  const { t: tEnum } = useTranslation('enums')

  const sections = useUserControllerSections()
  const grant = useRoleControllerGrant()
  const restrictField = useRoleControllerRestrictField()

  // The row a refusal belongs to. A refusal shown at the top of a grid of seventeen rows does not
  // say which one it is about.
  const [refused, setRefused] = useState<{ key: string; error: unknown } | undefined>()

  /** Why nothing on this role may be changed, or undefined when it may. */
  const roleLock = ((): string | undefined => {
    if (role.systemRole === true) return t('roles.locked.systemRole', { name: role.name ?? '' })
    if (roleId === permissions.roleId) return t('roles.locked.ownRole')
    return undefined
  })()

  const fullAccess = role.fullAccess === true

  const levelOptions = (section: Section): SegmentedOption<AccessLevel>[] =>
    Object.values(AccessLevel).map((level) => {
      const held = permissions.levelOf(section)
      // Revoking is always allowed, including on a section the caller cannot see.
      const cannotConfer = level !== AccessLevel.NONE && RANK[held] < RANK[level]
      return {
        value: level,
        label: tEnum(`AccessLevel.${level}`),
        ...(fullAccess
          ? { disabledReason: t('roles.locked.fullAccess') }
          : cannotConfer
            ? {
                disabledReason: t('roles.locked.cannotConfer', {
                  level: tEnum(`AccessLevel.${level}`),
                  held: tEnum(`AccessLevel.${held}`),
                }),
              }
            : {}),
      }
    })

  const levelOf = (section: Section): AccessLevel => {
    /*
     * A full-access role holds everything without a single grant row — `RoleView.accessTo` says so,
     * and Owner and Admin have none at all. Reading `sectionGrants` alone would draw seventeen rows
     * of NONE for the two roles that can see everything, which is not a cosmetic error: it is the
     * screen stating the opposite of the truth about the most privileged roles in the system.
     */
    if (fullAccess) return AccessLevel.FULL
    return role.sectionGrants?.[section] ?? AccessLevel.NONE
  }

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>{t('roles.grants')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-1">
          {fullAccess && <p className="text-muted-foreground text-sm">{t('roles.fullAccessBody')}</p>}
          {!fullAccess && roleLock !== undefined && editable && (
            <p className="text-muted-foreground flex items-start gap-1 text-sm">
              <WarningCircleIcon aria-hidden className="mt-0.5 shrink-0" />
              {roleLock}
            </p>
          )}

          {sections.isLoading && <p className="text-muted-foreground text-sm">{t('app.loading')}</p>}

          {(sections.data?.items ?? []).map((entry: SectionView) => {
            const section = entry.section
            if (!section) return null
            const current = levelOf(section)

            return (
              <div key={section} className="flex flex-wrap items-center justify-between gap-3 border-b py-2">
                <div className="flex items-center gap-2">
                  <span className="text-sm">{tEnum(`Section.${section}`)}</span>
                  {entry.available === false && (
                    // Grantable, and honest about leading nowhere. The backend stores the grant
                    // deliberately, so that the permission model is complete before the features
                    // are — see `Section.isAvailable`.
                    <Badge variant="outline" title={t('roles.notBuiltTitle')}>
                      {t('roles.notBuilt')}
                    </Badge>
                  )}
                </div>

                <div className="flex flex-col items-end gap-1">
                  {editable ? (
                    <SegmentedControl
                      aria-label={tEnum(`Section.${section}`)}
                      options={levelOptions(section)}
                      value={current}
                      disabled={roleLock !== undefined || grant.isPending}
                      onValueChange={(level) => {
                        setRefused(undefined)
                        grant.mutate(
                          { id: roleId, section, data: { accessLevel: level } },
                          {
                            onSuccess: onChanged,
                            onError: (error) => setRefused({ key: section, error }),
                          },
                        )
                      }}
                    />
                  ) : (
                    // A VIEW grant gets no affordance at all, not a disabled one — the standing
                    // distinction `FieldEditor` draws.
                    <span className="text-sm">{tEnum(`AccessLevel.${current}`)}</span>
                  )}
                  {refused?.key === section && <Refusal error={refused.error} />}
                </div>
              </div>
            )
          })}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('roles.restrictions')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-1">
          <p className="text-muted-foreground text-sm">{t('roles.restrictionsBody')}</p>

          {Object.values(ProtectedField).map((field) => {
            const restricted = role.restrictedFields?.includes(field) ?? false

            return (
              <div key={field} className="flex flex-wrap items-center justify-between gap-3 border-b py-2">
                <span className="text-sm">{tEnum(`ProtectedField.${field}`)}</span>

                <div className="flex flex-col items-end gap-1">
                  {editable ? (
                    <SegmentedControl
                      aria-label={tEnum(`ProtectedField.${field}`)}
                      options={[
                        { value: 'VISIBLE', label: t('roles.field.visible') },
                        { value: 'HIDDEN', label: t('roles.field.hidden') },
                      ]}
                      value={restricted ? 'HIDDEN' : 'VISIBLE'}
                      // No confer rule here: the backend applies none to field restrictions, and a
                      // rule the server does not have is a rule this screen must not invent.
                      disabled={roleLock !== undefined || restrictField.isPending}
                      onValueChange={(choice) => {
                        setRefused(undefined)
                        restrictField.mutate(
                          { id: roleId, field, data: { restricted: choice === 'HIDDEN' } },
                          {
                            onSuccess: onChanged,
                            onError: (error) => setRefused({ key: field, error }),
                          },
                        )
                      }}
                    />
                  ) : (
                    <span className="text-sm">
                      {restricted ? t('roles.field.hidden') : t('roles.field.visible')}
                    </span>
                  )}
                  {refused?.key === field && <Refusal error={refused.error} />}
                </div>
              </div>
            )
          })}
        </CardContent>
      </Card>
    </>
  )
}
