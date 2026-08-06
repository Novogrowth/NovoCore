import { useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { useChartOfAccountsControllerPaymentMethodTargets } from '@/api/generated/endpoints/chart-of-accounts/chart-of-accounts'
import { useAadePaymentMethodControllerAadePaymentMethods } from '@/api/generated/endpoints/aade-payment-method/aade-payment-method'
import {
  getPaymentMethodControllerPaymentMethodQueryKey,
  usePaymentMethodControllerChangeAbbreviation,
  usePaymentMethodControllerChangeAccount,
  usePaymentMethodControllerChangeArticle,
  usePaymentMethodControllerChangeSortCode,
  usePaymentMethodControllerCreatePaymentMethod,
  usePaymentMethodControllerDeactivate,
  usePaymentMethodControllerDescribe,
  usePaymentMethodControllerPaymentMethod,
  usePaymentMethodControllerPaymentMethods,
  usePaymentMethodControllerReactivate,
} from '@/api/generated/endpoints/payment-method/payment-method'
import { Section, type PaymentMethodView } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { DataTable } from '@/components/data-table/data-table'
import { useListState } from '@/components/data-table/use-list-state'
import { FieldEditor, UnsetValue } from '@/components/field-editor/field-editor'
import { PlusIcon } from '@/components/icons'
import { OptionSelect } from '@/components/option-select'
import { Refusal } from '@/components/refusal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { sortCodeOf } from '@/pages/document-reference/values'

import { paymentMethodColumns } from './payment-method-columns'

/**
 * The business's payment methods — **full CRUD. The owner authors every row.**
 *
 * <h2>⚠️ This screen was seed-only until R4, and the reversal is a REQUIREMENT correction</h2>
 *
 * R2b built it on the seed-only convention: no Add control, a permanent line saying who authors the
 * rows, and an absence test naming the omission as *permanent* rather than *not yet*. **All three
 * are gone**, because payment methods turned out to be a **business list that REFERENCES an AADE
 * codification** — R1a's two layers, one entity over. The convention itself is untouched and still
 * governs the AADE invoice types and the new AADE payment-method articles.
 *
 * ⭐ R2b's argument *against* creation was never refuted — it **changed sign**. It said a new method
 * needs an account and two behaviour flags that no form can supply. The account is now supplied (as
 * a real account, not an `AccountSystemKey`, because a user-created row cannot inherit a stable
 * handle it is not a member of) and **both flags are derived rather than collected**.
 *
 * <h2>⚠️ The list starts EMPTY and nothing ever seeds it</h2>
 *
 * `V37` ships `payment_method` with no rows. The eight R2b seeded — ΜΕΤΡ, ΚΑΡΤ, ΤΡΑΠ, ΕΠΙΤ, ΑΝΤΙΚ,
 * SKRZ, PPAL, STRP — had **invented** abbreviations, and an empty list removes that rather than
 * making fabrications editable. **Empty is the normal first state here, not an edge case.**
 */
const BASE = '/settings/payment-methods'

/**
 * A chosen id, as the wire wants it.
 *
 * The ESLint rule against `Number(...)` is about *amounts*, and its documented escape is to say on
 * the line that the value is a count. Stated once here rather than at five call sites — the same
 * helper, for the same reason, as `sales-invoice-record.tsx`.
 */
// eslint-disable-next-line no-restricted-syntax -- an id is a count, not a value
const identifier = (chosen: string | null): number => Number(chosen)

export function PaymentMethodsList() {
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const [activeOnly, setActiveOnly] = useState(false)
  const list = useListState('GET /api/payment-methods')

  const methods = usePaymentMethodControllerPaymentMethods({
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
          {t('paymentMethods.filter.activeOnly')}
        </label>

        {permissions.canEdit(Section.SALES) && (
          <Button size="sm" nativeButton={false} render={<Link to={`${BASE}/new`} />}>
            <PlusIcon aria-hidden />
            {t('paymentMethods.create')}
          </Button>
        )}
      </div>

      <DataTable
        data={methods.data}
        columns={paymentMethodColumns(t)}
        list={list}
        isLoading={methods.isLoading}
        emptyMessage={t('paymentMethods.empty')}
        getRowId={(row: PaymentMethodView) => String(row.id)}
      />
    </div>
  )
}

export function PaymentMethodCreate() {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const create = usePaymentMethodControllerCreatePaymentMethod()

  const articles = useAadePaymentMethodControllerAadePaymentMethods({ active: true })
  const accounts = useChartOfAccountsControllerPaymentMethodTargets()

  const [abbreviation, setAbbreviation] = useState('')
  const [description, setDescription] = useState('')
  const [articleId, setArticleId] = useState<string | null>(null)
  const [accountId, setAccountId] = useState<string | null>(null)
  const [sortCode, setSortCode] = useState('')

  /*
   * ⚠️ BOTH PICKERS ARE MANDATORY, AND THE REASON IS NOT SYMMETRY — IT IS THE CASH LIMIT.
   *
   * A future reader meeting two required pickers will reasonably ask why one of them could not be
   * optional, so the answer is here rather than in a commit message.
   *
   * The AADE article was going to be nullable, on R1a's precedent: six of the owner's nineteen
   * document types have no AADE invoice type, and that is a real thing the business issues. It does
   * not transfer. A document type with no code is a real KIND of document; a payment method with no
   * article is an INCOMPLETELY SPECIFIED ROW — the null would record "nobody has said yet" while
   * claiming to record "this has none".
   *
   * ⭐ And it closed a hole rather than merely tidying one. `subjectToCashLimit` derives from the
   * article being annex 8.12 code 3 (Μετρητά). While an article could be absent, a cash-like method
   * created without one would have escaped the statutory €500 limit SILENTLY. Requiring the article
   * makes that unreachable — the model became complete, instead of a second signal being bolted on
   * to detect cash, which would have been two mechanisms for one rule.
   *
   * The account is mandatory for the mirror reason: `settlesImmediately` derives from its KIND, and
   * Επί πιστώσει NAMES Accounts receivable rather than carrying a null the posting code reads by
   * convention.
   */
  const complete =
    abbreviation.trim() !== '' && description.trim() !== '' && articleId !== null && accountId !== null

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!complete) return
    create.mutate(
      {
        data: {
          abbreviation: abbreviation.trim(),
          description: description.trim(),
          aadePaymentMethodId: identifier(articleId),
          accountId: identifier(accountId),
          /*
           * ⚠️ OMITTED when blank — never 0, never "". The service is the ONE allocator and
           * resolves absence to max + 10, which is what stops four callers inventing four schemes
           * against a UNIQUE column. Sending 0 would not be "absent", it would be a sort code of
           * zero, and the second method created would collide with the first.
           */
          ...(sortCode.trim() === '' ? {} : { sortCode: sortCodeOf(sortCode.trim()) }),
        },
      },
      { onSuccess: (created) => void navigate(`${BASE}/${created.id}`) },
    )
  }

  return (
    <div className="max-w-2xl space-y-4">
      <Link to={BASE} className="text-muted-foreground text-sm hover:underline">
        ← {t('paymentMethods.backToList')}
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>{t('paymentMethods.create')}</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={submit}>
            <div className="space-y-1">
              <Label htmlFor="abbreviation">{t('paymentMethods.column.abbreviation')}</Label>
              <Input
                id="abbreviation"
                value={abbreviation}
                onChange={(event) => setAbbreviation(event.target.value)}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="description">{t('paymentMethods.column.description')}</Label>
              <Input
                id="description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="aadePaymentMethodId">{t('paymentMethods.column.article')}</Label>
              <OptionSelect
                id="aadePaymentMethodId"
                value={articleId}
                onValueChange={setArticleId}
                options={(articles.data?.items ?? []).map((article) => ({
                  value: String(article.id),
                  label: `${article.code} — ${article.description}`,
                }))}
              />
            </div>

            {/*
              ⚠️ /api/accounts/payment-method-targets, NOT /api/accounts/settlement-targets.
              The second is narrower and guards the Receipt path: adding Accounts receivable to it
              would let a Receipt settle INTO a receivable, allocating one against itself. Two
              questions, two routes — see ChartOfAccountsService.
            */}
            <div className="space-y-1">
              <Label htmlFor="accountId">{t('paymentMethods.column.account')}</Label>
              <OptionSelect
                id="accountId"
                value={accountId}
                onValueChange={setAccountId}
                options={(accounts.data?.items ?? []).map((account) => ({
                  value: String(account.id),
                  label: account.name ?? String(account.id),
                }))}
              />
              <p className="text-muted-foreground text-sm">{t('paymentMethods.accountHint')}</p>
            </div>

            <div className="space-y-1">
              <Label htmlFor="sortCode">{t('paymentMethods.column.sortCode')}</Label>
              <Input
                id="sortCode"
                inputMode="numeric"
                value={sortCode}
                placeholder={t('paymentMethods.sortCodeAppends')}
                onChange={(event) => setSortCode(event.target.value.replace(/[^0-9]/g, ''))}
              />
              <p className="text-muted-foreground text-sm">
                {t('paymentMethods.sortCodeOptional')}
              </p>
            </div>

            <Refusal error={create.error} />

            <Button type="submit" disabled={!complete || create.isPending}>
              {create.isPending ? t('paymentMethods.creating') : t('paymentMethods.save')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

export function PaymentMethodDetail() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('common')
  const permissions = usePermissions()
  const queryClient = useQueryClient()

  const paymentMethodId = identifier(id ?? null)
  const query = usePaymentMethodControllerPaymentMethod(paymentMethodId)

  const articles = useAadePaymentMethodControllerAadePaymentMethods({ active: true })
  const accounts = useChartOfAccountsControllerPaymentMethodTargets()

  const changeAbbreviation = usePaymentMethodControllerChangeAbbreviation()
  const describe = usePaymentMethodControllerDescribe()
  const changeArticle = usePaymentMethodControllerChangeArticle()
  const changeAccount = usePaymentMethodControllerChangeAccount()
  const sortCode = usePaymentMethodControllerChangeSortCode()
  const deactivate = usePaymentMethodControllerDeactivate()
  const reactivate = usePaymentMethodControllerReactivate()

  const editable = permissions.canEdit(Section.SALES)

  const applyResponse = (updated: PaymentMethodView) => {
    queryClient.setQueryData(
      getPaymentMethodControllerPaymentMethodQueryKey(paymentMethodId),
      updated,
    )
    void queryClient.invalidateQueries({ queryKey: ['/api/payment-methods'] })
  }

  if (query.isLoading) return <p className="text-muted-foreground text-sm">{t('app.loading')}</p>
  const row = query.data
  if (!row) return <p className="text-muted-foreground text-sm">{t('paymentMethods.notFound')}</p>

  /*
   * ⚠️ THE FREEZE, AND IT IS `lockedReason` — the SECOND field state, not the third.
   *
   * These fields are editable in general and fixed on THIS record once a document has been settled
   * by it: shown, DISABLED, with the reason. Never hidden — a hidden control on the one record where
   * a setting is fixed leaves an operator hunting for something every other row has.
   *
   * The mechanism is the series' and is deliberately not reinvented: `inUse` is a boolean ON THE
   * VIEW and the reason is composed HERE, because every `lockedReason` in this application is a
   * client i18n string and the backend localises nothing (Q47(b)). The server's own fuller refusal
   * still arrives through `Refusal` if a request is somehow sent.
   *
   * ⚠️ The freeze covers EVERY field except the sort code, and the account is why: changing where a
   * method posted, after it has posted, would make the ledger disagree with itself.
   */
  const frozen = row.inUse ? t('paymentMethods.locked.inUse') : undefined

  const articleOptions = (articles.data?.items ?? []).map((article) => ({
    value: String(article.id),
    label: `${article.code} — ${article.description}`,
  }))
  const accountOptions = (accounts.data?.items ?? []).map((account) => ({
    value: String(account.id),
    label: account.name ?? String(account.id),
  }))

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link to={BASE} className="text-muted-foreground text-sm hover:underline">
            ← {t('paymentMethods.backToList')}
          </Link>
          <h1 className="text-lg font-semibold">{row.abbreviation}</h1>
          {row.active === false && (
            <Badge variant="outline">{t('paymentMethods.flag.inactive')}</Badge>
          )}
        </div>

        {editable &&
          (row.active === false ? (
            <Button
              size="sm"
              variant="outline"
              disabled={reactivate.isPending}
              onClick={() =>
                reactivate.mutate({ id: paymentMethodId }, { onSuccess: () => void query.refetch() })
              }
            >
              {t('paymentMethods.reactivate')}
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              disabled={deactivate.isPending}
              onClick={() =>
                deactivate.mutate({ id: paymentMethodId }, { onSuccess: () => void query.refetch() })
              }
            >
              {t('paymentMethods.deactivate')}
            </Button>
          ))}
      </div>

      {/* ⚠️ Setting is refused, holding is not — the rule this control turns on. */}
      {editable && row.active !== false && (
        <p className="text-muted-foreground text-sm">{t('paymentMethods.deactivateMeaning')}</p>
      )}

      <Refusal error={deactivate.error ?? reactivate.error} />

      <Card>
        <CardHeader>
          <CardTitle>{t('paymentMethods.detailTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-1">
          <FieldEditor<string>
            label={t('paymentMethods.column.abbreviation')}
            value={row.abbreviation ?? ''}
            display={row.abbreviation ?? <UnsetValue />}
            editable={editable}
            {...(frozen ? { lockedReason: frozen } : {})}
            isValid={(value) => value.trim() !== ''}
            onSave={async (value) =>
              applyResponse(
                await changeAbbreviation.mutateAsync({
                  id: paymentMethodId,
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
            label={t('paymentMethods.column.description')}
            value={row.description ?? ''}
            display={row.description ?? <UnsetValue />}
            editable={editable}
            {...(frozen ? { lockedReason: frozen } : {})}
            isValid={(value) => value.trim() !== ''}
            onSave={async (value) =>
              applyResponse(
                await describe.mutateAsync({
                  id: paymentMethodId,
                  data: { description: value.trim() },
                }),
              )
            }
          >
            {(value, setValue) => (
              <Input value={value} onChange={(event) => setValue(event.target.value)} />
            )}
          </FieldEditor>

          <FieldEditor<string>
            label={t('paymentMethods.column.article')}
            value={String(row.aadePaymentMethodId)}
            display={`${row.aadePaymentMethodCode} — ${row.aadePaymentMethodDescription}`}
            editable={editable}
            {...(frozen ? { lockedReason: frozen } : {})}
            onSave={async (value) =>
              applyResponse(
                await changeArticle.mutateAsync({
                  id: paymentMethodId,
                  data: { aadePaymentMethodId: identifier(value) },
                }),
              )
            }
          >
            {(value, setValue) => (
              <OptionSelect
                id="article"
                options={articleOptions}
                value={value}
                onValueChange={(next) => setValue(next ?? value)}
              />
            )}
          </FieldEditor>

          <FieldEditor<string>
            label={t('paymentMethods.column.account')}
            value={String(row.accountId)}
            display={row.accountName ?? <UnsetValue />}
            editable={editable}
            {...(frozen ? { lockedReason: frozen } : {})}
            onSave={async (value) =>
              applyResponse(
                await changeAccount.mutateAsync({
                  id: paymentMethodId,
                  data: { accountId: identifier(value) },
                }),
              )
            }
          >
            {(value, setValue) => (
              <OptionSelect
                id="account"
                options={accountOptions}
                value={value}
                onValueChange={(next) => setValue(next ?? value)}
              />
            )}
          </FieldEditor>

          {/* ⚠️ NOT frozen, and that is deliberate: reordering is normal and a sort code appears on
              no document. The same exemption V34's four tables carry. */}
          <FieldEditor<string>
            label={t('paymentMethods.column.sortCode')}
            value={String(row.sortCode)}
            display={String(row.sortCode)}
            editable={editable}
            isValid={(value) => value.trim() !== ''}
            onSave={async (value) =>
              applyResponse(
                await sortCode.mutateAsync({
                  id: paymentMethodId,
                  data: { sortCode: sortCodeOf(value.trim()) },
                }),
              )
            }
          >
            {(value, setValue) => (
              <Input
                inputMode="numeric"
                value={value}
                onChange={(event) => setValue(event.target.value.replace(/[^0-9]/g, ''))}
              />
            )}
          </FieldEditor>

          {/*
            ⚠️ DERIVED, and the third field state: plain text with the reason, no control on any
            installation. `settlesImmediately` comes from the account's KIND and `subjectToCashLimit`
            from the article being annex 8.12 code 3. Neither is stored, so neither is settable —
            changing one means changing the account or the article above.
          */}
          <ReadOnlyField
            label={t('paymentMethods.column.behaviour')}
            value={[
              row.settlesImmediately
                ? t('paymentMethods.settlesImmediately')
                : t('paymentMethods.settlesLater'),
              row.subjectToCashLimit ? t('paymentMethods.cashLimited') : null,
            ]
              .filter(Boolean)
              .join(' · ')}
            reason={t('paymentMethods.behaviourDerived')}
          />
        </CardContent>
      </Card>
    </div>
  )
}

/** A field no route can change — `VatClassDetail`'s component and the same argument. */
function ReadOnlyField({
  label,
  value,
  reason,
}: {
  label: string
  value: string | undefined
  reason: string
}) {
  return (
    <div className="border-b py-2">
      <div className="flex items-baseline justify-between gap-4">
        <Label className="text-muted-foreground w-48 shrink-0 text-sm">{label}</Label>
        <span className="flex-1 text-sm">{value === undefined ? <UnsetValue /> : value}</span>
      </div>
      <p className="text-muted-foreground mt-1 pl-52 text-sm">{reason}</p>
    </div>
  )
}
