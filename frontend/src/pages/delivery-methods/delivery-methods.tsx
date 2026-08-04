import { useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'

import {
  getDeliveryMethodControllerDeliveryMethodQueryKey,
  useDeliveryMethodControllerChangeAbbreviation,
  useDeliveryMethodControllerCreate,
  useDeliveryMethodControllerDeactivate,
  useDeliveryMethodControllerDeliveryMethod,
  useDeliveryMethodControllerDeliveryMethods,
  useDeliveryMethodControllerDescribe,
  useDeliveryMethodControllerReactivate,
} from '@/api/generated/endpoints/delivery-method/delivery-method'
import { Section, type DeliveryMethodView } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { useListState } from '@/components/data-table/use-list-state'

import { deliveryMethodColumns } from './delivery-method-columns'
import { FieldEditor, UnsetValue } from '@/components/field-editor/field-editor'
import { PlusIcon } from '@/components/icons'
import { Refusal } from '@/components/refusal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * How goods reach the customer — a courier, our own vehicle, collection from the shop.
 *
 * ⚠️ **Not an AADE codification, and worth saying because it sits beside two that are.** Annex 8.14
 * `Σκοπός Διακίνησης` is the transport *purpose* and belongs with 18b, which is a different question
 * from who carries the parcel. Nothing here is transmitted. This is the business's own list, full
 * CRUD, and it ships empty.
 *
 * ⚠️ **Nothing in the schema references this table yet** — measured 2026-08-04, zero foreign keys —
 * so `inUse` is always `false` and the R2 freeze on the abbreviation cannot fire. That is a fact
 * about the schema rather than about the data: it becomes reachable when a dispatch document gains a
 * delivery method at **18b**, and `DocumentReferenceGraphIT` makes that a red build rather than a
 * silent gap. The correction path exists now because the abbreviation is typed by hand now.
 *
 * Governed by `SALES`, because a delivery method is chosen when a sale is recorded.
 */
const BASE = '/settings/delivery-methods'

export function DeliveryMethodsList() {
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const [activeOnly, setActiveOnly] = useState(false)
  const list = useListState('GET /api/delivery-methods')

  const methods = useDeliveryMethodControllerDeliveryMethods({
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
          {t('deliveryMethods.filter.activeOnly')}
        </label>

        {permissions.canEdit(Section.SALES) && (
          <Button size="sm" nativeButton={false} render={<Link to={`${BASE}/new`} />}>
            <PlusIcon /> {t('deliveryMethods.create')}
          </Button>
        )}
      </div>

      <DataTable
        data={methods.data}
        columns={deliveryMethodColumns(t)}
        list={list}
        isLoading={methods.isLoading}
        emptyMessage={t('deliveryMethods.empty')}
        getRowId={(row: DeliveryMethodView) => String(row.id)}
      />
    </div>
  )
}

export function DeliveryMethodDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  // The route parameter is text. A database id is a count, not a value.
  // eslint-disable-next-line no-restricted-syntax
  const methodId = Number(id)

  const query = useDeliveryMethodControllerDeliveryMethod(methodId, {
    query: { enabled: Number.isInteger(methodId) },
  })

  const describe = useDeliveryMethodControllerDescribe()
  const abbreviation = useDeliveryMethodControllerChangeAbbreviation()
  const deactivate = useDeliveryMethodControllerDeactivate()
  const reactivate = useDeliveryMethodControllerReactivate()

  const editable = permissions.canEdit(Section.SALES)

  const applyResponse = (updated: DeliveryMethodView) => {
    queryClient.setQueryData(getDeliveryMethodControllerDeliveryMethodQueryKey(methodId), updated)
    void queryClient.invalidateQueries({ queryKey: ['/api/delivery-methods'] })
  }

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  const method = query.data
  if (!method) {
    return <p className="text-muted-foreground text-sm">{t('deliveryMethods.notFound')}</p>
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link to={BASE} className="text-muted-foreground text-sm hover:underline">
            ← {t('deliveryMethods.backToList')}
          </Link>
          <h1 className="text-lg font-semibold">{method.abbreviation}</h1>
          {method.active === false && (
            <Badge variant="outline">{t('deliveryMethods.flag.inactive')}</Badge>
          )}
        </div>

        {editable &&
          (method.active === false ? (
            <Button
              size="sm"
              variant="outline"
              disabled={reactivate.isPending}
              onClick={() =>
                reactivate.mutate({ id: methodId }, { onSuccess: () => void query.refetch() })
              }
            >
              {t('deliveryMethods.reactivate')}
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              disabled={deactivate.isPending}
              onClick={() =>
                deactivate.mutate({ id: methodId }, { onSuccess: () => void query.refetch() })
              }
            >
              {t('deliveryMethods.deactivate')}
            </Button>
          ))}
      </div>

      <Refusal error={deactivate.error ?? reactivate.error} />

      <Card>
        <CardHeader>
          <CardTitle>{t('deliveryMethods.detailTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-1">
          <FieldEditor<string>
            label={t('deliveryMethods.column.abbreviation')}
            value={method.abbreviation ?? ''}
            display={method.abbreviation ?? <UnsetValue />}
            editable={editable}
            // ⚠️ `inUse` is the R2 freeze, and here it is always false — nothing references this
            // table. The branch is written anyway so 18b changes a predicate rather than adding a
            // concept, and so this screen behaves identically to the two series screens.
            {...(method.inUse ? { lockedReason: t('deliveryMethods.locked.inUse') } : {})}
            isValid={(value) => value.trim() !== ''}
            onSave={async (value) =>
              applyResponse(
                await abbreviation.mutateAsync({
                  id: methodId,
                  data: { abbreviation: value.trim() },
                }),
              )
            }
          >
            {(value, setValue) => (
              <Input value={value} onChange={(event) => setValue(event.target.value)} />
            )}
          </FieldEditor>

          <FieldEditor<string>
            label={t('deliveryMethods.column.description')}
            value={method.description ?? ''}
            display={method.description ?? <UnsetValue />}
            editable={editable}
            isValid={(value) => value.trim() !== ''}
            onSave={async (value) =>
              applyResponse(
                await describe.mutateAsync({ id: methodId, data: { description: value.trim() } }),
              )
            }
          >
            {(value, setValue) => (
              <Input value={value} onChange={(event) => setValue(event.target.value)} />
            )}
          </FieldEditor>
        </CardContent>
      </Card>
    </div>
  )
}

export function DeliveryMethodCreate() {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const create = useDeliveryMethodControllerCreate()

  const [abbreviation, setAbbreviation] = useState('')
  const [description, setDescription] = useState('')

  const complete = abbreviation.trim() !== '' && description.trim() !== ''

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!complete) return
    create.mutate(
      { data: { abbreviation: abbreviation.trim(), description: description.trim() } },
      { onSuccess: (created) => void navigate(`${BASE}/${created.id}`) },
    )
  }

  return (
    <div className="max-w-2xl space-y-4">
      <Link to={BASE} className="text-muted-foreground text-sm hover:underline">
        ← {t('deliveryMethods.backToList')}
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>{t('deliveryMethods.create')}</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={submit}>
            <div className="space-y-1">
              <Label htmlFor="abbreviation">{t('deliveryMethods.column.abbreviation')}</Label>
              <Input
                id="abbreviation"
                value={abbreviation}
                onChange={(event) => setAbbreviation(event.target.value)}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="description">{t('deliveryMethods.column.description')}</Label>
              <Input
                id="description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>

            <Refusal error={create.error} />

            <Button type="submit" disabled={!complete || create.isPending}>
              {create.isPending ? t('deliveryMethods.creating') : t('deliveryMethods.save')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
