import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { usePurchaseDocumentTypeControllerDocumentTypes } from '@/api/generated/endpoints/purchase-document-type/purchase-document-type'
import {
  getPurchaseDocumentSeriesControllerOneSeriesQueryKey,
  usePurchaseDocumentSeriesControllerChangeAbbreviation,
  usePurchaseDocumentSeriesControllerChangeDocumentType,
  usePurchaseDocumentSeriesControllerChangeGetsMark,
  usePurchaseDocumentSeriesControllerClearTransformationTarget,
  usePurchaseDocumentSeriesControllerCreate,
  usePurchaseDocumentSeriesControllerDeactivate,
  usePurchaseDocumentSeriesControllerDescribe,
  usePurchaseDocumentSeriesControllerMapTransformationTarget,
  usePurchaseDocumentSeriesControllerOneSeries,
  usePurchaseDocumentSeriesControllerReactivate,
  usePurchaseDocumentSeriesControllerSeries,
} from '@/api/generated/endpoints/purchase-document-series/purchase-document-series'
import { Section } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { unwrapList } from '@/components/data-table/list-response'
import { useListState } from '@/components/data-table/use-list-state'
import { PlusIcon } from '@/components/icons'
import { Button } from '@/components/ui/button'
import { seriesColumns } from '@/pages/document-reference/series-columns'
import { SeriesCreateForm, SeriesDetail } from '@/pages/document-reference/series-screens'
import type { SeriesView } from '@/pages/document-reference/values'

/**
 * The business's own **purchase** document series.
 *
 * ⚠️⚠️ **THERE IS NO CHANNEL HERE, AND ITS ABSENCE IS THE DECISION RATHER THAN AN OMISSION.**
 * `purchase_document_series` has no channel column, no channel route, and
 * `PurchaseDocumentSeriesView` has no channel component. Channel is where a *sale* came from and
 * never applies to a purchase; a nullable column that could only ever be null invites somebody to
 * fill it, and a purchase series carrying `ECOMMERCE` would be storable, meaningless and
 * indistinguishable from data. So `showChannel` is `false` here and `true` on the sales twin, and
 * that is the only difference between the two screens.
 *
 * ⚠️ **A test asserts no channel control renders**, because *"there is no route"* and *"the route
 * silently does nothing"* look identical to an operator — the same reason R1a wrote a test asserting
 * the column's absence rather than trusting the schema to speak for itself.
 *
 * ⚠️ **The R2 freeze cannot fire on this screen.** `inUse` is always `false`: measured 2026-08-04,
 * nothing in the schema can reference a purchase series except another series' transformation
 * target, so no purchase document names one until **F6**. The correction routes exist anyway,
 * because a purchase abbreviation is typed by hand exactly as a sales one is, and
 * `DocumentReferenceGraphIT` turns the day F6 changes this into a red build rather than a silent
 * gap.
 */
const BASE = '/settings/purchase-document-series'

export function PurchaseDocumentSeriesList() {
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const [activeOnly, setActiveOnly] = useState(false)
  const list = useListState('GET /api/purchase-document-series')

  const series = usePurchaseDocumentSeriesControllerSeries({
    ...(activeOnly ? { active: true } : {}),
    ...list.params,
  })

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input
            type="checkbox"
            checked={activeOnly}
            onChange={(event) => setActiveOnly(event.target.checked)}
          />
          {t('docSeries.filter.activeOnly')}
        </label>

        {permissions.canEdit(Section.PURCHASING) && (
          <Button size="sm" nativeButton={false} render={<Link to={`${BASE}/new`} />}>
            <PlusIcon /> {t('docSeries.createPurchase')}
          </Button>
        )}
      </div>

      <DataTable
        data={series.data}
        columns={seriesColumns(t, BASE, { showChannel: false })}
        list={list}
        isLoading={series.isLoading}
        emptyMessage={t('docSeries.emptyPurchase')}
        getRowId={(row: SeriesView) => String(row.id)}
      />
    </div>
  )
}

export function PurchaseDocumentSeriesDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // The route parameter is text. A database id is a count, not a value.
  // eslint-disable-next-line no-restricted-syntax
  const seriesId = Number(id)

  const query = usePurchaseDocumentSeriesControllerOneSeries(seriesId, {
    query: { enabled: Number.isInteger(seriesId) },
  })

  const all = usePurchaseDocumentSeriesControllerSeries()
  const types = usePurchaseDocumentTypeControllerDocumentTypes()

  const describe = usePurchaseDocumentSeriesControllerDescribe()
  const abbreviation = usePurchaseDocumentSeriesControllerChangeAbbreviation()
  const documentType = usePurchaseDocumentSeriesControllerChangeDocumentType()
  const getsMark = usePurchaseDocumentSeriesControllerChangeGetsMark()
  const mapTarget = usePurchaseDocumentSeriesControllerMapTransformationTarget()
  const clearTarget = usePurchaseDocumentSeriesControllerClearTransformationTarget()
  const deactivate = usePurchaseDocumentSeriesControllerDeactivate()
  const reactivate = usePurchaseDocumentSeriesControllerReactivate()

  const applyResponse = (updated: SeriesView) => {
    queryClient.setQueryData(getPurchaseDocumentSeriesControllerOneSeriesQueryKey(seriesId), updated)
    void queryClient.invalidateQueries({ queryKey: ['/api/purchase-document-series'] })
  }

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  if (!query.data) return <p className="text-muted-foreground text-sm">{t('docSeries.notFound')}</p>

  return (
    <SeriesDetail
      t={t}
      series={query.data}
      editable={permissions.canEdit(Section.PURCHASING)}
      // ⚠️ No `onChannel` is passed either. The prop is optional and this side has no route to
      // call — `showChannel={false}` alone would leave a caller able to wire one up.
      showChannel={false}
      backTo={BASE}
      backLabel={t('docSeries.backToPurchaseList')}
      siblings={unwrapList(all.data).rows}
      documentTypes={unwrapList(types.data).rows.map((type) => ({
        id: type.id,
        description: type.description,
      }))}
      onDescribe={async (description) =>
        applyResponse(await describe.mutateAsync({ id: seriesId, data: { description } }))
      }
      onAbbreviation={async (value) =>
        applyResponse(
          await abbreviation.mutateAsync({ id: seriesId, data: { abbreviation: value } }),
        )
      }
      onDocumentType={async (documentTypeId) =>
        applyResponse(await documentType.mutateAsync({ id: seriesId, data: { documentTypeId } }))
      }
      onGetsMark={async (value) =>
        applyResponse(await getsMark.mutateAsync({ id: seriesId, data: { getsMark: value } }))
      }
      onTransformationTarget={async (targetSeriesId) =>
        applyResponse(
          targetSeriesId === null
            ? await clearTarget.mutateAsync({ id: seriesId })
            : await mapTarget.mutateAsync({ id: seriesId, data: { targetSeriesId } }),
        )
      }
      onActivate={() =>
        reactivate.mutate({ id: seriesId }, { onSuccess: () => void query.refetch() })
      }
      onDeactivate={() =>
        deactivate.mutate({ id: seriesId }, { onSuccess: () => void query.refetch() })
      }
      activationError={reactivate.error ?? deactivate.error}
      activationPending={reactivate.isPending || deactivate.isPending}
    />
  )
}

export function PurchaseDocumentSeriesCreate() {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const create = usePurchaseDocumentSeriesControllerCreate()

  // ⚠️ ACTIVE types only, matching the sales side: a series whose type is inactive is one the
  // recording path refuses, so offering one would build a series that cannot be used.
  const types = usePurchaseDocumentTypeControllerDocumentTypes({ active: true })
  const all = usePurchaseDocumentSeriesControllerSeries()

  return (
    <SeriesCreateForm
      t={t}
      showChannel={false}
      titleKey="docSeries.createPurchase"
      backTo={BASE}
      backLabel={t('docSeries.backToPurchaseList')}
      documentTypes={unwrapList(types.data).rows.map((type) => ({
        id: type.id,
        description: type.description,
      }))}
      siblings={unwrapList(all.data).rows}
      onSubmit={(data) =>
        create.mutate({ data }, { onSuccess: (created) => void navigate(`${BASE}/${created.id}`) })
      }
      error={create.error}
      pending={create.isPending}
    />
  )
}
