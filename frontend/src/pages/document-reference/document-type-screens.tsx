import type { TFunction } from 'i18next'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'

import type { DocumentSide } from '@/api/generated/model'
import { FieldEditor, UnsetValue } from '@/components/field-editor/field-editor'
import { Refusal } from '@/components/refusal'
import { SegmentedControl } from '@/components/segmented-control'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import { AadeInvoiceTypeSelect } from './aade-invoice-type-select'
import { useAadeInvoiceTypes } from './aade-invoice-types-data'
import { aadeDisplay } from './values'
import { StockBehaviourControl } from './stock-behaviour'
import {
  fromAnswer,
  isCoherent,
  isDraft,
  sortCodeOf,
  toAnswer,
  type DocumentTypeView,
  type NewDocumentTypeBody,
  type StockAnswer,
} from './values'

/**
 * Sales and purchase document types share these screens, because they are the same screen.
 *
 * ⚠️ **One implementation, two thin callers** — `SalesDocumentTypesList` and its purchase twin pass
 * their own generated hooks in. The two records are field-for-field identical (`description`, the
 * two nullable stock flags, `requiresMydataTransmission`, the nullable AADE reference, `active`),
 * and the only difference is which `side` the AADE picker asks for. Two copies would be two places
 * for the three-state control to drift, and `CLAUDE.md` asks for duplication to be looked for rather
 * than accumulated.
 *
 * **The purchase twin's *behaviour* is untouched by R2 — that is F6's** — and nothing here changes
 * it: these are screens over routes R1a already shipped.
 */


// ================================================================================================
// Detail
// ================================================================================================

export interface DocumentTypeDetailProps {
  t: TFunction
  side: DocumentSide
  type: DocumentTypeView
  editable: boolean
  backTo: string
  backLabel: string
  onDescribe: (description: string) => Promise<unknown>
  onStockBehaviour: (affectsStock: boolean, transfersStock: boolean) => Promise<unknown>
  onSortCode: (sortCode: number) => Promise<unknown>
  onMydata: (required: boolean) => Promise<unknown>
  onAade: (aadeInvoiceTypeId: number | null) => Promise<unknown>
  onActivate: () => void
  onDeactivate: () => void
  activationError: unknown
  activationPending: boolean
}

export function DocumentTypeDetail({
  t,
  side,
  type,
  editable,
  backTo,
  backLabel,
  onDescribe,
  onStockBehaviour,
  onSortCode,
  onMydata,
  onAade,
  onActivate,
  onDeactivate,
  activationError,
  activationPending,
}: DocumentTypeDetailProps) {
  const aade = useAadeInvoiceTypes(side)
  const draft = isDraft(type)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Link to={backTo} className="text-muted-foreground text-sm hover:underline">
            ← {backLabel}
          </Link>
          <h1 className="text-lg font-semibold">{type.description}</h1>
          {draft && <Badge variant="outline">{t('docTypes.flag.draft')}</Badge>}
          {type.active === false && !draft && (
            <Badge variant="outline">{t('docTypes.flag.inactive')}</Badge>
          )}
        </div>

        {editable &&
          (type.active === false ? (
            <Button
              size="sm"
              variant="outline"
              /*
               * ⚠️ THE R1 CONSTRAINT THIS SCREEN EXISTS TO MEET.
               *
               *   CHECK (active = false OR affects_stock IS NOT NULL AND transfers_stock IS NOT NULL)
               *
               * Disabled with the reason shown below, never hidden and never a refusal after the
               * fact: an operator who cannot see why Activate does nothing cannot act on it. The
               * server refuses this too, with a fuller sentence, and `Refusal` shows that whenever
               * a request is actually sent — this only stops one whose answer is already certain.
               */
              disabled={activationPending || draft}
              onClick={onActivate}
            >
              {t('docTypes.activate')}
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              disabled={activationPending}
              onClick={onDeactivate}
            >
              {t('docTypes.deactivate')}
            </Button>
          ))}
      </div>

      {editable && type.active === false && draft && (
        <p className="text-muted-foreground text-sm">{t('docTypes.cannotActivate')}</p>
      )}

      <Refusal error={activationError} />

      <Card>
        <CardHeader>
          <CardTitle>{t('docTypes.detailTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-1">
          <FieldEditor<string>
            label={t('docTypes.column.description')}
            value={type.description ?? ''}
            display={type.description ?? <UnsetValue />}
            editable={editable}
            isValid={(value) => value.trim() !== ''}
            onSave={(value) => onDescribe(value.trim())}
          >
            {(value, setValue) => (
              <Input value={value} onChange={(event) => setValue(event.target.value)} />
            )}
          </FieldEditor>

          {/* ⚠️ FREELY EDITABLE, and the contrast with the fields around it is the point:
              reordering a list is normal, and a sort code appears on no document. */}
          <FieldEditor<string>
            label={t('docTypes.column.sortCode')}
            value={String(type.sortCode)}
            display={String(type.sortCode)}
            editable={editable}
            isValid={(value) => value.trim() !== ''}
            onSave={(value) => onSortCode(sortCodeOf(value.trim()))}
          >
            {(value, setValue) => (
              <Input
                inputMode="numeric"
                value={value}
                onChange={(event) => setValue(event.target.value.replace(/[^0-9]/g, ''))}
              />
            )}
          </FieldEditor>

          <StockBehaviourFields
            t={t}
            type={type}
            editable={editable}
            onSave={onStockBehaviour}
          />

          <FieldEditor<boolean>
            label={t('docTypes.column.mydata')}
            value={type.requiresMydataTransmission ?? false}
            display={type.requiresMydataTransmission ? t('docTypes.yes') : t('docTypes.no')}
            editable={editable}
            onSave={(value) => onMydata(value)}
          >
            {(value, setValue) => (
              <SegmentedControl
                aria-label={t('docTypes.column.mydata')}
                options={[
                  { value: 'yes', label: t('docTypes.yes') },
                  { value: 'no', label: t('docTypes.no') },
                ]}
                value={value ? 'yes' : 'no'}
                onValueChange={(next) => setValue(next === 'yes')}
              />
            )}
          </FieldEditor>

          <FieldEditor<number | null>
            label={t('docTypes.column.aade')}
            value={type.aadeInvoiceTypeId ?? null}
            display={aadeDisplay(type.aadeInvoiceTypeCode, aade.items, type.aadeInvoiceTypeId, t)}
            editable={editable}
            onSave={(value) => onAade(value)}
          >
            {(value, setValue) => (
              <AadeInvoiceTypeSelect
                t={t}
                side={side}
                value={value}
                onChange={setValue}
                aria-label={t('docTypes.column.aade')}
              />
            )}
          </FieldEditor>
        </CardContent>
      </Card>
    </div>
  )
}

/**
 * ⚠️ **The two stock flags are ONE `FieldEditor`, because they are one request and one decision.**
 *
 * `PUT …/stock-behaviour` takes both at once, and the backend says why: two separate routes would
 * let the incoherent state — transfers stock but does not affect it — be saved and then activated.
 * Splitting them on screen would recreate exactly that, one request apart.
 */
function StockBehaviourFields({
  t,
  type,
  editable,
  onSave,
}: {
  t: TFunction
  type: DocumentTypeView
  editable: boolean
  onSave: (affectsStock: boolean, transfersStock: boolean) => Promise<unknown>
}) {
  const current: [StockAnswer, StockAnswer] = [
    toAnswer(type.affectsStock),
    toAnswer(type.transfersStock),
  ]

  return (
    <FieldEditor<[StockAnswer, StockAnswer]>
      label={t('docTypes.stockBehaviour')}
      value={current}
      display={
        <span>
          {t('docTypes.column.affectsStock')}: <StockWord t={t} value={type.affectsStock} />
          {' · '}
          {t('docTypes.column.transfersStock')}: <StockWord t={t} value={type.transfersStock} />
        </span>
      }
      editable={editable}
      // ⚠️ `undecided` cannot be SAVED from here — `StockBehaviourRequest` boxes both components as
      // @Mandatory, so there is no request that unanswers the question. It can only be the value
      // the row arrived with, which is why the control still offers it disabled with the reason.
      isValid={([affects, transfers]) =>
        affects !== 'undecided' && transfers !== 'undecided' && isCoherent(affects, transfers)
      }
      onSave={([affects, transfers]) => {
        const affectsValue = fromAnswer(affects)
        const transfersValue = fromAnswer(transfers)
        if (affectsValue === undefined || transfersValue === undefined) {
          return Promise.resolve()
        }
        return onSave(affectsValue, transfersValue)
      }}
    >
      {([affects, transfers], setValue) => (
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <Label className="text-muted-foreground w-40 shrink-0 text-sm">
              {t('docTypes.column.affectsStock')}
            </Label>
            <StockBehaviourControl
              t={t}
              value={affects}
              onChange={(next) => setValue([next, transfers])}
              allowUndecided={false}
              aria-label={t('docTypes.column.affectsStock')}
            />
          </div>
          <div className="flex items-center gap-2">
            <Label className="text-muted-foreground w-40 shrink-0 text-sm">
              {t('docTypes.column.transfersStock')}
            </Label>
            <StockBehaviourControl
              t={t}
              value={transfers}
              onChange={(next) => setValue([affects, next])}
              allowUndecided={false}
              aria-label={t('docTypes.column.transfersStock')}
            />
          </div>
          {!isCoherent(affects, transfers) && (
            <p className="text-destructive text-sm">{t('docTypes.stock.incoherent')}</p>
          )}
        </div>
      )}
    </FieldEditor>
  )
}

function StockWord({ t, value }: { t: TFunction; value: boolean | undefined }) {
  if (value === undefined) return <em>{t('docTypes.stock.undecided')}</em>
  return <>{value ? t('docTypes.yes') : t('docTypes.no')}</>
}

// ================================================================================================
// Create
// ================================================================================================

/**
 * Adding a document type.
 *
 * ⚠️ **This is the one place `undecided` can be chosen**, and it is the default. A type whose stock
 * question is unanswered saves as an inactive draft — which is better than either alternative:
 * refusing would make it impossible to write the type down before the answer is known, and
 * defaulting to `false` would record a decision nobody took, invisibly, on a value R1b branches
 * document recording on.
 */
export function DocumentTypeCreateForm({
  t,
  side,
  titleKey,
  backTo,
  backLabel,
  onSubmit,
  error,
  pending,
}: {
  t: TFunction
  side: DocumentSide
  titleKey: string
  backTo: string
  backLabel: string
  onSubmit: (body: NewDocumentTypeBody) => void
  error: unknown
  pending: boolean
}) {
  const [description, setDescription] = useState('')
  const [sortCode, setSortCode] = useState('')
  const [affects, setAffects] = useState<StockAnswer>('undecided')
  const [transfers, setTransfers] = useState<StockAnswer>('undecided')
  /**  is 'nobody has answered', which must stay distinguishable from 'answered no'. */
  const [mydata, setMydata] = useState<'yes' | 'no' | ''>('')
  const [aadeInvoiceTypeId, setAadeInvoiceTypeId] = useState<number | null>(null)

  // `requiresMydataTransmission` is `@Mandatory` on the wire, so it is a required CHOICE rather
  // than a checkbox — an unticked box sends `false`, which the server accepts happily, and "not a
  // tax document" would then be indistinguishable from "nobody said". F4's units-of-measure form
  // made the same call for the same reason.
  const complete =
    description.trim() !== '' && sortCode.trim() !== '' && mydata !== '' &&
    isCoherent(affects, transfers)

  const submit = (event: FormEvent) => {
    event.preventDefault()
    // `complete` already narrows `mydata` away from the unanswered state.
    if (!complete) return

    const affectsValue = fromAnswer(affects)
    const transfersValue = fromAnswer(transfers)

    onSubmit({
      description: description.trim(),
      sortCode: sortCodeOf(sortCode),
      // ⚠️ OMITTED when undecided, never sent as `false`. `NewSalesDocumentType`'s flags are
      // nullable Booleans precisely so an absent one means "not decided" — and the row is then
      // created as an inactive draft rather than as a type that silently never moves stock.
      ...(affectsValue === undefined ? {} : { affectsStock: affectsValue }),
      ...(transfersValue === undefined ? {} : { transfersStock: transfersValue }),
      requiresMydataTransmission: mydata === 'yes',
      ...(aadeInvoiceTypeId === null ? {} : { aadeInvoiceTypeId }),
    })
  }

  return (
    <div className="max-w-2xl space-y-4">
      <Link to={backTo} className="text-muted-foreground text-sm hover:underline">
        ← {backLabel}
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>{t(titleKey)}</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={submit}>
            <div className="space-y-1">
              <Label htmlFor="description">{t('docTypes.column.description')}</Label>
              <Input
                id="description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="sortCode">{t('docTypes.column.sortCode')}</Label>
              <p className="text-muted-foreground text-sm">{t('docTypes.sortCode.help')}</p>
              {/* ⚠️ Digits only, and NOT `<input type="number">` — that is banned by an ESLint
                  rule. A sort code is a plain count, so none of the decimal-input machinery
                  applies; restricting the text is what keeps a fraction or a separator out. */}
              <Input
                id="sortCode"
                inputMode="numeric"
                value={sortCode}
                onChange={(event) => setSortCode(event.target.value.replace(/[^0-9]/g, ''))}
              />
            </div>

            <fieldset className="space-y-2">
              <legend className="text-sm font-medium">{t('docTypes.stockBehaviour')}</legend>
              <p className="text-muted-foreground text-sm">{t('docTypes.stock.help')}</p>

              <div className="flex items-center gap-2">
                <Label className="text-muted-foreground w-40 shrink-0 text-sm">
                  {t('docTypes.column.affectsStock')}
                </Label>
                <StockBehaviourControl
                  t={t}
                  value={affects}
                  onChange={setAffects}
                  allowUndecided
                  aria-label={t('docTypes.column.affectsStock')}
                />
              </div>
              <div className="flex items-center gap-2">
                <Label className="text-muted-foreground w-40 shrink-0 text-sm">
                  {t('docTypes.column.transfersStock')}
                </Label>
                <StockBehaviourControl
                  t={t}
                  value={transfers}
                  onChange={setTransfers}
                  allowUndecided
                  aria-label={t('docTypes.column.transfersStock')}
                />
              </div>

              {!isCoherent(affects, transfers) && (
                <p className="text-destructive text-sm">{t('docTypes.stock.incoherent')}</p>
              )}
              {(affects === 'undecided' || transfers === 'undecided') &&
                isCoherent(affects, transfers) && (
                  <p className="text-muted-foreground text-sm">{t('docTypes.stock.willBeDraft')}</p>
                )}
            </fieldset>

            <div className="space-y-1">
              <Label htmlFor="mydata">{t('docTypes.column.mydata')}</Label>
              <SegmentedControl
                aria-label={t('docTypes.column.mydata')}
                options={[
                  { value: 'yes', label: t('docTypes.yes') },
                  { value: 'no', label: t('docTypes.no') },
                ]}
                // `null` until chosen: "not answered" must be distinguishable from "answered no".
                value={mydata}
                onValueChange={(next) => setMydata(next === 'yes' ? 'yes' : 'no')}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="aade">{t('docTypes.column.aade')}</Label>
              <p className="text-muted-foreground text-sm">{t('docTypes.aade.help')}</p>
              <AadeInvoiceTypeSelect
                id="aade"
                t={t}
                side={side}
                value={aadeInvoiceTypeId}
                onChange={setAadeInvoiceTypeId}
                aria-label={t('docTypes.column.aade')}
              />
            </div>

            <Refusal error={error} />

            <Button type="submit" disabled={!complete || pending}>
              {pending ? t('docTypes.creating') : t('docTypes.save')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
