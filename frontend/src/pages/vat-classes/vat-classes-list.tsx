import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useTaxLookupControllerVatClasses } from '@/api/generated/endpoints/tax-lookup/tax-lookup'
import { Section } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { unwrapList } from '@/components/data-table/list-response'
import { SearchFilter } from '@/components/data-table/search-filter'
import { useListState } from '@/components/data-table/use-list-state'
import { PlusIcon } from '@/components/icons'
import { Button } from '@/components/ui/button'

import { vatClassColumns } from './vat-class-columns'

/**
 * VAT classes.
 *
 * ⚠️ **A rate is never edited in place, so this screen offers no way to.** Editing one would
 * retroactively change what every invoice already issued under that class appears to have charged.
 * A rate change is **a new class plus a deactivation of the old one**, which is why "Add" and
 * "Deactivate" are the whole of the write surface here and why the detail page shows the rate as
 * plain text rather than as a disabled field — a disabled field invites somebody to look for the
 * permission that unlocks it, and there is none on any installation.
 *
 * The search box matches the code and the description (target list row 6). ⚠️ **It does not match
 * the rate**, deliberately: eight distinct percentages across nine rows means a rate does not
 * identify a class, so a box that matched one would be right most of the time.
 */
export function VatClassesList() {
  const { t } = useTranslation('common')
  const permissions = usePermissions()

  const [activeOnly, setActiveOnly] = useState(true)
  const [search, setSearch] = useState<string | undefined>(undefined)

  const list = useListState('GET /api/vat-classes')

  const vatClasses = useTaxLookupControllerVatClasses({
    ...(activeOnly ? { active: true } : {}),
    ...(search ? { search } : {}),
    ...list.params,
  })

  /*
   * The counterpart column shows a CODE, and the row only carries the counterpart's id. Resolving
   * it needs every class, including deactivated ones — a live class may map to a retired one, and
   * looking the id up in the filtered list would render a blank cell that reads as "no mapping"
   * when there is one. So this is a second, unfiltered query rather than a lookup into the rows on
   * screen.
   */
  const all = useTaxLookupControllerVatClasses()
  const codeById = new Map(unwrapList(all.data).rows.map((entry) => [entry.id, entry.code] as const))

  const columns = vatClassColumns(t, (id) => codeById.get(id))

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-4">
          <SearchFilter
            id="vat-class-search"
            onChange={setSearch}
            label={t('vatClasses.filter.search')}
            placeholder={t('vatClasses.filter.searchPlaceholder')}
          />
          <label className="flex items-center gap-2 pb-2 text-sm">
            <input
              type="checkbox"
              checked={activeOnly}
              onChange={(event) => setActiveOnly(event.target.checked)}
            />
            {t('vatClasses.filter.activeOnly')}
          </label>
        </div>

        {permissions.canEdit(Section.TAX_AND_CHARGES) && (
          <Button size="sm" nativeButton={false} render={<Link to="/settings/vat-classes/new" />}>
            <PlusIcon /> {t('vatClasses.create')}
          </Button>
        )}
      </div>

      <DataTable
        data={vatClasses.data}
        columns={columns}
        list={list}
        isLoading={vatClasses.isLoading}
        emptyMessage={t('vatClasses.empty')}
        getRowId={(row) => String(row.id)}
      />
    </div>
  )
}
