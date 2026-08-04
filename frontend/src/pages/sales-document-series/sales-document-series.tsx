import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { useSalesDocumentTypeControllerDocumentTypes } from '@/api/generated/endpoints/sales-document-type/sales-document-type'
import {
  getSalesDocumentSeriesControllerOneSeriesQueryKey,
  useSalesDocumentSeriesControllerChangeAbbreviation,
  useSalesDocumentSeriesControllerChangeChannel,
  useSalesDocumentSeriesControllerChangeDocumentType,
  useSalesDocumentSeriesControllerChangeGetsMark,
  useSalesDocumentSeriesControllerClearChannel,
  useSalesDocumentSeriesControllerClearTransformationTarget,
  useSalesDocumentSeriesControllerCreate,
  useSalesDocumentSeriesControllerDeactivate,
  useSalesDocumentSeriesControllerDescribe,
  useSalesDocumentSeriesControllerMapTransformationTarget,
  useSalesDocumentSeriesControllerOneSeries,
  useSalesDocumentSeriesControllerReactivate,
  useSalesDocumentSeriesControllerSeries,
} from '@/api/generated/endpoints/sales-document-series/sales-document-series'
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
 * The business's own **sales** document series — ΑΛΠ, ΑΛΠW, ΤΠΔΑ, and the owner's rest.
 *
 * ⚠️ **This screen carries channel and the purchase one does not**, which is the whole difference
 * between the two records. And since R1b a sales invoice's channel **comes from its series** rather
 * than being settable on the invoice — so what is chosen here decides how every future sale in this
 * series is attributed. The screen says so rather than leaving it to be discovered at F5.
 *
 * ⚠️ **A series with no channel is normal, not incomplete.** Six of the owner's are — ΠΡΦ, ΔΠΠ, ΔΑ,
 * ΣΑΥΤ, ΠΣΑΥΤ — so the control offers "not a sales channel" as a named option, and choosing it sends
 * `DELETE …/channel`. ⚠️ R1b **refuses** to record an invoice against such a series (422, naming
 * self-supply): `sales_invoice.channel` is `NOT NULL` and was deliberately not relaxed, because the
 * constraint is what holds R3's question open.
 */
const BASE = '/settings/sales-document-series'

export function SalesDocumentSeriesList() {
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const [activeOnly, setActiveOnly] = useState(false)
  const list = useListState('GET /api/sales-document-series')

  const series = useSalesDocumentSeriesControllerSeries({
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

        {permissions.canEdit(Section.SALES) && (
          <Button size="sm" nativeButton={false} render={<Link to={`${BASE}/new`} />}>
            <PlusIcon /> {t('docSeries.createSales')}
          </Button>
        )}
      </div>

      <DataTable
        data={series.data}
        columns={seriesColumns(t, BASE, { showChannel: true })}
        list={list}
        isLoading={series.isLoading}
        emptyMessage={t('docSeries.emptySales')}
        getRowId={(row: SeriesView) => String(row.id)}
      />
    </div>
  )
}

export function SalesDocumentSeriesDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // The route parameter is text. A database id is a count, not a value.
  // eslint-disable-next-line no-restricted-syntax
  const seriesId = Number(id)

  const query = useSalesDocumentSeriesControllerOneSeries(seriesId, {
    query: { enabled: Number.isInteger(seriesId) },
  })

  // Every series, so the transformation target renders an abbreviation rather than a raw id —
  // including inactive ones, since a live series may legitimately transform into a retired one.
  const all = useSalesDocumentSeriesControllerSeries()
  const types = useSalesDocumentTypeControllerDocumentTypes()

  const describe = useSalesDocumentSeriesControllerDescribe()
  const abbreviation = useSalesDocumentSeriesControllerChangeAbbreviation()
  const documentType = useSalesDocumentSeriesControllerChangeDocumentType()
  const getsMark = useSalesDocumentSeriesControllerChangeGetsMark()
  const changeChannel = useSalesDocumentSeriesControllerChangeChannel()
  const clearChannel = useSalesDocumentSeriesControllerClearChannel()
  const mapTarget = useSalesDocumentSeriesControllerMapTransformationTarget()
  const clearTarget = useSalesDocumentSeriesControllerClearTransformationTarget()
  const deactivate = useSalesDocumentSeriesControllerDeactivate()
  const reactivate = useSalesDocumentSeriesControllerReactivate()

  const applyResponse = (updated: SeriesView) => {
    queryClient.setQueryData(getSalesDocumentSeriesControllerOneSeriesQueryKey(seriesId), updated)
    void queryClient.invalidateQueries({ queryKey: ['/api/sales-document-series'] })
  }

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  if (!query.data) return <p className="text-muted-foreground text-sm">{t('docSeries.notFound')}</p>

  return (
    <SeriesDetail
      t={t}
      series={query.data}
      editable={permissions.canEdit(Section.SALES)}
      showChannel
      backTo={BASE}
      backLabel={t('docSeries.backToSalesList')}
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
      onChannel={async (channel) =>
        applyResponse(
          channel === null
            ? // ⚠️ DELETE, never a PUT of null: `SeriesChannelRequest.channel` is @Mandatory, and
              // "this series is not a sales channel" is a real configuration rather than a blanked
              // field.
              await clearChannel.mutateAsync({ id: seriesId })
            : await changeChannel.mutateAsync({ id: seriesId, data: { channel } }),
        )
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

export function SalesDocumentSeriesCreate() {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const create = useSalesDocumentSeriesControllerCreate()

  // ⚠️ ACTIVE types only. R1b refuses to record an invoice against a series whose document type is
  // inactive, so offering one here would build a series that cannot be used the moment it exists.
  const types = useSalesDocumentTypeControllerDocumentTypes({ active: true })
  const all = useSalesDocumentSeriesControllerSeries()

  return (
    <SeriesCreateForm
      t={t}
      showChannel
      titleKey="docSeries.createSales"
      backTo={BASE}
      backLabel={t('docSeries.backToSalesList')}
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
