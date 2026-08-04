import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'

import {
  getPurchaseDocumentTypeControllerDocumentTypeQueryKey,
  usePurchaseDocumentTypeControllerClearAadeInvoiceType,
  usePurchaseDocumentTypeControllerCreate,
  usePurchaseDocumentTypeControllerDeactivate,
  usePurchaseDocumentTypeControllerDescribe,
  usePurchaseDocumentTypeControllerDocumentType,
  usePurchaseDocumentTypeControllerDocumentTypes,
  usePurchaseDocumentTypeControllerChangeMydataTransmissionRequired,
  usePurchaseDocumentTypeControllerChangeStockBehaviour,
  usePurchaseDocumentTypeControllerMapToAadeInvoiceType,
  usePurchaseDocumentTypeControllerReactivate,
} from '@/api/generated/endpoints/purchase-document-type/purchase-document-type'
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
 * The business's own **purchase** document types — the owner's four, authored by him.
 *
 * ⚠️ **The screen is the sales one**, shared through `document-type-screens.tsx`, with exactly one
 * difference: the AADE picker asks for `side=RECEIVED` (15 codes) rather than `ISSUED` (34). The
 * backend enforces the same split — `PurchaseDocumentTypeServiceImpl` refuses a purchase type
 * naming an issuer-side code — so offering all 55 would build a picker most of whose options are
 * certain refusals.
 *
 * ⚠️ **R2 changes no purchase-document-type BEHAVIOUR — that is F6's.** `purchase_document_type`
 * becomes mandatory on a purchase document there, and the one inconsistency R1b left (sales
 * `affects_stock` is read, purchase `affects_stock` is not) is recorded in F6's roadmap footnote.
 * This is a screen over routes R1a already shipped.
 *
 * ⚠️ **Governed by `PURCHASING`, not `SETTINGS`**, although it lives under the Settings heading —
 * the principle `UnitsList` states: a section is about who needs to see something.
 *
 * **No search box**, for the reason stated on the sales list.
 */
const BASE = '/settings/purchase-document-types'

export function PurchaseDocumentTypesList() {
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const [activeOnly, setActiveOnly] = useState(false)
  const list = useListState('GET /api/purchase-document-types')

  const types = usePurchaseDocumentTypeControllerDocumentTypes({
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

        {permissions.canEdit(Section.PURCHASING) && (
          <Button size="sm" nativeButton={false} render={<Link to={`${BASE}/new`} />}>
            <PlusIcon /> {t('docTypes.createPurchase')}
          </Button>
        )}
      </div>

      <DataTable
        data={types.data}
        columns={documentTypeColumns(t, BASE)}
        list={list}
        isLoading={types.isLoading}
        emptyMessage={t('docTypes.emptyPurchase')}
        getRowId={(row: DocumentTypeView) => String(row.id)}
      />
    </div>
  )
}

export function PurchaseDocumentTypeDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // The route parameter is text. A database id is a count, not a value.
  // eslint-disable-next-line no-restricted-syntax
  const typeId = Number(id)

  const query = usePurchaseDocumentTypeControllerDocumentType(typeId, {
    query: { enabled: Number.isInteger(typeId) },
  })

  const describe = usePurchaseDocumentTypeControllerDescribe()
  const stock = usePurchaseDocumentTypeControllerChangeStockBehaviour()
  const mydata = usePurchaseDocumentTypeControllerChangeMydataTransmissionRequired()
  const mapAade = usePurchaseDocumentTypeControllerMapToAadeInvoiceType()
  const clearAade = usePurchaseDocumentTypeControllerClearAadeInvoiceType()
  const deactivate = usePurchaseDocumentTypeControllerDeactivate()
  const reactivate = usePurchaseDocumentTypeControllerReactivate()

  const applyResponse = (updated: DocumentTypeView) => {
    queryClient.setQueryData(getPurchaseDocumentTypeControllerDocumentTypeQueryKey(typeId), updated)
    void queryClient.invalidateQueries({ queryKey: ['/api/purchase-document-types'] })
  }

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  if (!query.data) return <p className="text-muted-foreground text-sm">{t('docTypes.notFound')}</p>

  return (
    <DocumentTypeDetail
      t={t}
      side={DocumentSide.RECEIVED}
      type={query.data}
      editable={permissions.canEdit(Section.PURCHASING)}
      backTo={BASE}
      backLabel={t('docTypes.backToPurchaseList')}
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

export function PurchaseDocumentTypeCreate() {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const create = usePurchaseDocumentTypeControllerCreate()

  return (
    <DocumentTypeCreateForm
      t={t}
      side={DocumentSide.RECEIVED}
      titleKey="docTypes.createPurchase"
      backTo={BASE}
      backLabel={t('docTypes.backToPurchaseList')}
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
