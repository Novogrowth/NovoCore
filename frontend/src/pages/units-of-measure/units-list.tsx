import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import {
  useUnitOfMeasureControllerUnitsOfMeasure,
  useUnitOfMeasureControllerWithoutMydataCode,
} from '@/api/generated/endpoints/unit-of-measure/unit-of-measure'
import { Section } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { unwrapList } from '@/components/data-table/list-response'
import { SearchFilter } from '@/components/data-table/search-filter'
import { useListState } from '@/components/data-table/use-list-state'
import { PlusIcon, WarningCircleIcon } from '@/components/icons'
import { Button } from '@/components/ui/button'

import { unitColumns } from './unit-columns'

/**
 * Units of measure.
 *
 * ⚠️ **This screen lives under Settings and is governed by `PRODUCTS`, not `SETTINGS`.** A section
 * is about who needs to see something rather than about which authority defines it, and the people
 * who add a unit are the people who maintain the catalogue. A role holding `SETTINGS:FULL` and
 * nothing else reaches neither this page nor VAT classes, which is deliberate.
 *
 * The search box matches the code and the name (target list row 7), and **not** the myDATA code —
 * that column is NULL on every seeded row, so searching it would match nothing on every
 * installation that exists today.
 */
export function UnitsList() {
  const { t } = useTranslation('common')
  const permissions = usePermissions()

  const [activeOnly, setActiveOnly] = useState(true)
  const [search, setSearch] = useState<string | undefined>(undefined)

  const list = useListState('GET /api/units-of-measure')

  const units = useUnitOfMeasureControllerUnitsOfMeasure({
    ...(activeOnly ? { active: true } : {}),
    ...(search ? { search } : {}),
    ...list.params,
  })

  /*
   * ⚠️ A standing to-do, not a debug panel.
   *
   * `mydataCode` is nullable because the codes are pending the accountant, and phase 7 cannot
   * transmit a line whose unit has none. Until step 16b this list was answerable only from `psql`,
   * which is how a question waiting on somebody else stays unasked — so it is surfaced here, at the
   * top of the screen whose rows it is about, rather than left as a route nobody calls.
   *
   * Today it returns all eight seeded units.
   */
  const unmapped = useUnitOfMeasureControllerWithoutMydataCode()
  const unmappedCount = unwrapList(unmapped.data).rows.length

  const columns = unitColumns(t)

  return (
    <div className="space-y-4">
      {unmappedCount > 0 && (
        <p className="border-muted-foreground/30 text-muted-foreground flex items-start gap-2 rounded-md border border-dashed px-3 py-2 text-sm">
          <WarningCircleIcon aria-hidden className="mt-0.5 shrink-0" />
          {t('units.unmappedBanner', { count: unmappedCount })}
        </p>
      )}

      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-4">
          <SearchFilter
            id="unit-search"
            onChange={setSearch}
            label={t('units.filter.search')}
            placeholder={t('units.filter.searchPlaceholder')}
          />
          <label className="flex items-center gap-2 pb-2 text-sm">
            <input
              type="checkbox"
              checked={activeOnly}
              onChange={(event) => setActiveOnly(event.target.checked)}
            />
            {t('units.filter.activeOnly')}
          </label>
        </div>

        {permissions.canEdit(Section.PRODUCTS) && (
          <Button
            size="sm"
            nativeButton={false}
            render={<Link to="/settings/units-of-measure/new" />}
          >
            <PlusIcon /> {t('units.create')}
          </Button>
        )}
      </div>

      <DataTable
        data={units.data}
        columns={columns}
        list={list}
        isLoading={units.isLoading}
        emptyMessage={t('units.empty')}
        getRowId={(row) => String(row.id)}
      />
    </div>
  )
}
