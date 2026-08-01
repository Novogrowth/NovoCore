import type { ColumnDef } from '@tanstack/react-table'
import type { TFunction } from 'i18next'
import { Link } from 'react-router-dom'

import type { UserView } from '@/api/generated/model'
import { sortableHeader } from '@/components/data-table/sortable-header'
import { UnsetValue } from '@/components/field-editor/field-editor'
import { Badge } from '@/components/ui/badge'

/**
 * The account list's columns — used by the users list **and** by a role's holders table.
 *
 * One definition rather than two, because the second one is the whole reason a role can be
 * deactivated at all: the refusal names a count, and this is what turns that count into people.
 */
export function userColumns(t: TFunction): ColumnDef<UserView, unknown>[] {
  return [
    {
      accessorKey: 'username',
      header: sortableHeader(t('users.column.username')),
      cell: ({ row }) => (
        <Link to={`/users/${row.original.id}`} className="font-medium hover:underline">
          {row.original.username}
        </Link>
      ),
    },
    {
      id: 'displayName',
      accessorFn: (user) => user.displayName,
      header: sortableHeader(t('users.column.displayName')),
      cell: ({ row }) => row.original.displayName ?? <UnsetValue />,
    },
    {
      // By the role's name, not its id: the cell shows the name, and an id order would look
      // random to anybody who cannot see it.
      id: 'role',
      accessorFn: (user) => user.role?.name,
      header: sortableHeader(t('users.column.role')),
      cell: ({ row }) =>
        row.original.role?.id !== undefined ? (
          <Link to={`/roles/${row.original.role.id}`} className="hover:underline">
            {row.original.role.name}
          </Link>
        ) : (
          <UnsetValue />
        ),
    },
    {
      id: 'flags',
      header: t('users.column.flags'),
      enableSorting: false,
      cell: ({ row }) => (
        <div className="flex gap-1">
          {row.original.active === false && (
            <Badge variant="outline">{t('users.flag.inactive')}</Badge>
          )}
        </div>
      ),
    },
  ]
}
