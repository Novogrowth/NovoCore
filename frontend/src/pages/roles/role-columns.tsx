import type { ColumnDef } from '@tanstack/react-table'
import type { TFunction } from 'i18next'
import { Link } from 'react-router-dom'

import { AccessLevel, type RoleView } from '@/api/generated/model'
import { sortableHeader } from '@/components/data-table/sortable-header'
import { UnsetValue } from '@/components/field-editor/field-editor'
import { Badge } from '@/components/ui/badge'

/**
 * The role list's columns.
 *
 * **The "grants" column counts rather than lists.** A role can hold seventeen sections and the list
 * has to stay readable; what an administrator needs from a list is which roles are configured and
 * which are empty, and the grid on the detail page answers everything else.
 *
 * ⚠️ **A full-access role holds everything with no grant rows at all**, so counting the map would
 * report "0 sections" for Owner and Admin — the two roles that can see the most. `fullAccess` is
 * checked first, and says so in words.
 */
export function roleColumns(t: TFunction): ColumnDef<RoleView, unknown>[] {
  return [
    {
      accessorKey: 'name',
      header: sortableHeader(t('roles.column.name')),
      cell: ({ row }) => (
        <Link to={`/roles/${row.original.id}`} className="font-medium hover:underline">
          {row.original.name}
        </Link>
      ),
    },
    {
      id: 'description',
      accessorFn: (role) => role.description,
      header: sortableHeader(t('roles.column.description')),
      cell: ({ row }) => row.original.description ?? <UnsetValue />,
    },
    {
      id: 'grants',
      header: t('roles.column.grants'),
      /*
       * Not sortable, and this is the interesting one on this screen.
       *
       * The cell renders three different kinds of thing — "everything", "nothing", and a count —
       * so there is no single value to order by. Ordering by the count alone would put the two
       * most privileged roles in the system at the bottom with a count of zero, which is exactly
       * the trap this column's own javadoc warns about, arriving through a different door.
       */
      enableSorting: false,
      cell: ({ row }) => {
        if (row.original.fullAccess === true) return t('roles.everything')
        const granted = Object.values(row.original.sectionGrants ?? {}).filter(
          (level) => level !== AccessLevel.NONE,
        ).length
        return granted === 0 ? t('roles.nothing') : t('roles.sectionCount', { count: granted })
      },
    },
    {
      id: 'flags',
      header: t('roles.column.flags'),
      enableSorting: false,
      cell: ({ row }) => (
        <div className="flex gap-1">
          {row.original.systemRole === true && (
            <Badge variant="secondary" title={t('roles.flag.systemTitle')}>
              {t('roles.flag.system')}
            </Badge>
          )}
          {row.original.active === false && (
            <Badge variant="outline">{t('roles.flag.inactive')}</Badge>
          )}
        </div>
      ),
    },
  ]
}
