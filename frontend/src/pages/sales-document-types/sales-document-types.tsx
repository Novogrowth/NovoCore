import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'

import {
  getSalesDocumentTypeControllerDocumentTypeQueryKey,
  useSalesDocumentTypeControllerClearAadeInvoiceType,
  useSalesDocumentTypeControllerCreate,
  useSalesDocumentTypeControllerDeactivate,
  useSalesDocumentTypeControllerDescribe,
  useSalesDocumentTypeControllerDocumentType,
  useSalesDocumentTypeControllerDocumentTypes,
  useSalesDocumentTypeControllerChangeMydataTransmissionRequired,
  useSalesDocumentTypeControllerChangeStockBehaviour,
  useSalesDocumentTypeControllerMapToAadeInvoiceType,
  useSalesDocumentTypeControllerReactivate,
} from '@/api/generated/endpoints/sales-document-type/sales-document-type'
import { DocumentSide, Section } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { unwrapList } from '@/components/data-table/list-response'
import { useListState } from '@/components/data-table/use-list-state'
import { PlusIcon, WarningCircleIcon } from '@/components/icons'
import { Button } from '@/components/ui/button'
import { documentTypeColumns } from '@/pages/document-reference/document-type-columns'
import {
  DocumentTypeCreateForm,
  DocumentTypeDetail,
} from '@/pages/document-reference/document-type-screens'
import { isDraft, type DocumentTypeView } from '@/pages/document-reference/values'

/**
 * The business's own **sales** document types — the owner's fifteen, authored by him.
 *
 * ⚠️ **This is NOT the AADE codification.** That is `/settings/aade-invoice-types`, seeded by Flyway
 * with 55 rows and with no create control anywhere. This list is the business's own, ships EMPTY,
 * and points *at* the codification through a nullable reference — because six of the owner's
 * nineteen types (Προσφορά, Δελτίο Αποστολής, Παραγγελία and the rest) are operational documents
 * with no AADE invoice type at all.
 *
 * ⚠️ **Governed by `SALES`, not `SETTINGS`**, although it lives under the Settings heading — the
 * principle `UnitsList` states: a section is about who needs to see something, and this list is read
 * when recording a sale and by nobody else.
 *
 * **No search box.** No R2 entity is on `PROGRESS.md`'s 16-row search target list, no R2 endpoint
 * accepts `search=`, and the largest of these lists is 55 rows returned whole. Adopting a row by
 * reflex would mean backend work with nothing behind it.
 */
const BASE = '/settings/sales-document-types'

export function SalesDocumentTypesList() {
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const [activeOnly, setActiveOnly] = useState(false)
  const list = useListState('GET /api/sales-document-types')

  const types = useSalesDocumentTypeControllerDocumentTypes({
    ...(activeOnly ? { active: true } : {}),
    ...list.params,
  })

  const rows = unwrapList(types.data).rows
  // ⚠️ A standing to-do rather than a diagnostic, on `UnitsList`'s precedent: a type whose stock
  // question is unanswered is inactive and offered by no form, and a decision nobody can see is a
  // decision nobody finishes. The backend has a route for exactly this (`/drafts`); the count is
  // taken from the rows already in hand rather than by asking twice.
  const drafts = rows.filter(isDraft).length

  return (
    <div className="space-y-4">
      {drafts > 0 && (
        <p className="border-muted-foreground/30 text-muted-foreground flex items-start gap-2 rounded-md border border-dashed px-3 py-2 text-sm">
          <WarningCircleIcon aria-hidden className="mt-0.5 shrink-0" />
          {t('docTypes.draftBanner', { count: drafts })}
        </p>
      )}

      <div className="flex flex-wrap items-end justify-between gap-3">
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input
            type="checkbox"
            checked={activeOnly}
            onChange={(event) => setActiveOnly(event.target.checked)}
          />
          {t('docTypes.filter.activeOnly')}
        </label>

        {permissions.canEdit(Section.SALES) && (
          <Button size="sm" nativeButton={false} render={<Link to={`${BASE}/new`} />}>
            <PlusIcon /> {t('docTypes.createSales')}
          </Button>
        )}
      </div>

      <DataTable
        data={types.data}
        columns={documentTypeColumns(t, BASE)}
        list={list}
        isLoading={types.isLoading}
        emptyMessage={t('docTypes.emptySales')}
        getRowId={(row: DocumentTypeView) => String(row.id)}
      />
    </div>
  )
}

export function SalesDocumentTypeDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // The route parameter is text. A database id is a count, not a value.
  // eslint-disable-next-line no-restricted-syntax
  const typeId = Number(id)

  const query = useSalesDocumentTypeControllerDocumentType(typeId, {
    query: { enabled: Number.isInteger(typeId) },
  })

  const describe = useSalesDocumentTypeControllerDescribe()
  const stock = useSalesDocumentTypeControllerChangeStockBehaviour()
  const mydata = useSalesDocumentTypeControllerChangeMydataTransmissionRequired()
  const mapAade = useSalesDocumentTypeControllerMapToAadeInvoiceType()
  const clearAade = useSalesDocumentTypeControllerClearAadeInvoiceType()
  const deactivate = useSalesDocumentTypeControllerDeactivate()
  const reactivate = useSalesDocumentTypeControllerReactivate()

  const applyResponse = (updated: DocumentTypeView) => {
    queryClient.setQueryData(getSalesDocumentTypeControllerDocumentTypeQueryKey(typeId), updated)
    void queryClient.invalidateQueries({ queryKey: ['/api/sales-document-types'] })
  }

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  if (!query.data) return <p className="text-muted-foreground text-sm">{t('docTypes.notFound')}</p>

  return (
    <DocumentTypeDetail
      t={t}
      side={DocumentSide.ISSUED}
      type={query.data}
      editable={permissions.canEdit(Section.SALES)}
      backTo={BASE}
      backLabel={t('docTypes.backToSalesList')}
      onDescribe={async (description) =>
        applyResponse(await describe.mutateAsync({ id: typeId, data: { description } }))
      }
      onStockBehaviour={async (affectsStock, transfersStock) =>
        applyResponse(
          await stock.mutateAsync({ id: typeId, data: { affectsStock, transfersStock } }),
        )
      }
      onMydata={async (required) =>
        applyResponse(await mydata.mutateAsync({ id: typeId, data: { required } }))
      }
      onAade={async (aadeInvoiceTypeId) =>
        applyResponse(
          aadeInvoiceTypeId === null
            ? // ⚠️ DELETE, not a PUT of null — the resource removed is the mapping itself, and a
              // body carrying only null says nothing a caller can be held to.
              await clearAade.mutateAsync({ id: typeId })
            : await mapAade.mutateAsync({ id: typeId, data: { aadeInvoiceTypeId } }),
        )
      }
      onActivate={() =>
        reactivate.mutate({ id: typeId }, { onSuccess: () => void query.refetch() })
      }
      onDeactivate={() =>
        deactivate.mutate({ id: typeId }, { onSuccess: () => void query.refetch() })
      }
      activationError={reactivate.error ?? deactivate.error}
      activationPending={reactivate.isPending || deactivate.isPending}
    />
  )
}

export function SalesDocumentTypeCreate() {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const create = useSalesDocumentTypeControllerCreate()

  return (
    <DocumentTypeCreateForm
      t={t}
      side={DocumentSide.ISSUED}
      titleKey="docTypes.createSales"
      backTo={BASE}
      backLabel={t('docTypes.backToSalesList')}
      onSubmit={(data) =>
        create.mutate(
          { data },
          { onSuccess: (created) => void navigate(`${BASE}/${created.id}`) },
        )
      }
      error={create.error}
      pending={create.isPending}
    />
  )
}
