import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'

import { useCustomerControllerCustomers } from '@/api/generated/endpoints/customer/customer'
import { useProductControllerProducts } from '@/api/generated/endpoints/product/product'
import { useSalesDocumentSeriesControllerSeries } from '@/api/generated/endpoints/sales-document-series/sales-document-series'
import {
  useSalesControllerPreview,
  useSalesControllerRecord,
} from '@/api/generated/endpoints/sales/sales'
import { useTaxLookupControllerChargeTypes } from '@/api/generated/endpoints/tax-lookup/tax-lookup'
import {
  SalesLineType,
  SettlementMethod,
  type Money,
  type NewSalesInvoice,
  type NewSalesInvoiceLine,
  type Quantity,
  type UnitCost,
} from '@/api/generated/model'
import { idOptions } from '@/api/lookups'
import { MoneyInput, QuantityInput, UnitCostInput } from '@/components/decimal/decimal-input'
import { PlusIcon, XIcon } from '@/components/icons'
import { OptionSelect } from '@/components/option-select'
import { Refusal } from '@/components/refusal'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { formatMoney } from '@/lib/decimal'

/**
 * One line as the form holds it, before it becomes a `NewSalesInvoiceLine`.
 *
 * ⚠️ The decimal fields hold the **wire types** (`Quantity` is a string, `UnitCost` is
 * `{amount, currency}`) rather than plain numbers or a locally-parsed value. `MoneyInput` and its
 * siblings are text inputs backed by `decimal.js` and hand back exactly what goes on the wire, so
 * nothing here ever converts a money value through a JavaScript number — which an ESLint rule
 * enforces and an IEEE-754 double could not carry anyway.
 */
interface LineDraft {
  key: number
  lineType: SalesLineType
  productId: string | null
  chargeTypeId: string | null
  quantity: Quantity | undefined
  unitPrice: UnitCost | undefined
  description: string
}

const emptyLine = (key: number): LineDraft => ({
  key,
  lineType: SalesLineType.PRODUCT,
  productId: null,
  chargeTypeId: null,
  quantity: '1',
  unitPrice: undefined,
  description: '',
})

/**
 * The only currency this form offers.
 *
 * Novocore does not convert between currencies (ADR 0005) and the backend refuses an invoice that
 * mixes two, so a per-line currency picker would offer a combination whose refusal is certain. A
 * document in another currency is a real gap and is not this step's.
 */
const CURRENCY = 'EUR'

/**
 * A chosen id, as the wire wants it.
 *
 * The ESLint rule against `Number(...)` is about *amounts*, and its documented escape is to say on
 * the line that the value is a count. Stated once here rather than at six call sites.
 */
// eslint-disable-next-line no-restricted-syntax -- an id is a count, not a value
const identifier = (chosen: string | null): number => Number(chosen)

/** A select with a label, which `OptionSelect` deliberately does not carry itself. */
function LabelledSelect({
  id,
  label,
  value,
  onValueChange,
  options,
}: {
  id: string
  label: string
  value: string | null
  onValueChange: (value: string | null) => void
  options: readonly { value: string; label: string }[]
}) {
  return (
    <div className="space-y-1">
      <Label htmlFor={id}>{label}</Label>
      <OptionSelect id={id} value={value} onValueChange={onValueChange} options={options} />
    </div>
  )
}

/**
 * Recording a sale that already exists.
 *
 * <h2>⚠️ NOTHING HERE ISSUES ANYTHING</h2>
 *
 * Novocore never obtains a ΜΑΡΚ. Prosvasis Go issued this document, AADE holds it, and this form
 * writes down what was issued. So **the document number is an input, not an output** — there is no
 * sequence, no counter and no "next number" preview to render, and the button says *Record*.
 *
 * <h2>The series is mandatory, and it is why there is no channel field and no document-type field</h2>
 *
 * A sale's channel comes from its series — ΑΛΠW is the web series, so an invoice in it is a web sale
 * *by definition* rather than because somebody remembered to tick a box. The document type comes
 * through the series too, and it is what decides whether recording the sale moves stock at all.
 * `NewSalesInvoice` has no `channel` and no `documentTypeId` component; two independently settable
 * references could disagree about what kind of document a row is.
 *
 * ⚠️ **The picker offers active series only, and that is a convenience rather than the guard.** The
 * backend refuses an inactive series, a series whose document type is inactive, and a channel-less
 * one, and every refusal is rendered below. R2b's finding is exactly this shape in reverse — a
 * create screen's filter was the *only* thing enforcing a rule, and the edit screen had none.
 *
 * <h2>⚠️ PREVIEW BEFORE SUBMIT IS STRUCTURAL, NOT A COURTESY</h2>
 *
 * A rounding difference larger than `ledger.rounding.threshold` makes the server refuse the record
 * until somebody accepts it, and **the acceptance control cannot be rendered from anything this form
 * knows on its own**: whether a difference exceeds the threshold is a comparison between figures
 * Novocore computes from the lines and a threshold held in Settings. `POST /api/sales-invoices/preview`
 * answers with both, plus `roundingNeedsAcceptance`, from *the same `compute(...)` the record path
 * uses* — so a preview refuses exactly what a record would.
 *
 * That is also why the acceptance fields appear **only** when the preview asks for them: supplying
 * `roundingAcceptedBy` when the difference is small is silently dropped by the server, so a form
 * that always showed the field would invite somebody to type a name that is thrown away.
 */
export function SalesInvoiceRecord() {
  const { t, i18n } = useTranslation('common')
  const navigate = useNavigate()

  const [seriesId, setSeriesId] = useState<string | null>(null)
  const [customerId, setCustomerId] = useState<string | null>(null)
  const [settlementMethod, setSettlementMethod] = useState<string | null>(
    SettlementMethod.ON_ACCOUNT,
  )
  const [documentNumber, setDocumentNumber] = useState('')
  const [invoiceDate, setInvoiceDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [description, setDescription] = useState('')
  const [statedTotal, setStatedTotal] = useState<Money | undefined>(undefined)
  const [roundingAcceptedBy, setRoundingAcceptedBy] = useState('')
  const [roundingNote, setRoundingNote] = useState('')
  const [nextKey, setNextKey] = useState(1)
  const [lines, setLines] = useState<LineDraft[]>([emptyLine(0)])

  const series = useSalesDocumentSeriesControllerSeries({ active: true })
  const customers = useCustomerControllerCustomers({ active: true })
  const products = useProductControllerProducts({ active: true })
  const chargeTypes = useTaxLookupControllerChargeTypes({ active: true })

  const preview = useSalesControllerPreview()
  const record = useSalesControllerRecord()

  /**
   * The body both routes take — built once, so previewing and submitting cannot disagree about what
   * was asked for. That is the whole reason the two endpoints share a request type.
   */
  function body(): NewSalesInvoice {
    const built: NewSalesInvoiceLine[] = lines.map((line) => ({
      lineType: line.lineType,
      ...(line.lineType === SalesLineType.PRODUCT
        ? { productId: identifier(line.productId) }
        : { chargeTypeId: identifier(line.chargeTypeId) }),
      quantity: line.quantity ?? '0',
      unitPrice: line.unitPrice ?? { amount: '0', currency: CURRENCY },
      ...(line.description ? { description: line.description } : {}),
      serialNumbers: [],
    }))

    return {
      customerId: identifier(customerId),
      seriesId: identifier(seriesId),
      settlementMethod: settlementMethod as SettlementMethod,
      documentNumber,
      invoiceDate,
      ...(description ? { description } : {}),
      ...(statedTotal ? { statedTotal } : {}),
      ...(roundingAcceptedBy ? { roundingAcceptedBy } : {}),
      ...(roundingNote ? { roundingNote } : {}),
      lines: built,
    }
  }

  const priced = preview.data
  const needsAcceptance = priced?.roundingNeedsAcceptance ?? false

  /**
   * Enough to ask the server. ⚠️ Not a mirror of the domain's rules — the backend refuses a
   * channel-less series, an inactive one, a deactivated payment method, a cash sale at the statutory
   * limit and a dozen other things, each with a sentence worth reading. This only stops a request
   * whose refusal would be about a field the operator has not filled in yet.
   */
  const complete =
    seriesId !== null &&
    customerId !== null &&
    documentNumber.trim() !== '' &&
    lines.length > 0 &&
    lines.every(
      (line) =>
        line.unitPrice !== undefined &&
        line.quantity !== undefined &&
        (line.lineType === SalesLineType.PRODUCT ? line.productId : line.chargeTypeId),
    )

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <Link to="/sales/invoices" className="text-sm text-muted-foreground hover:underline">
          ← {t('salesInvoices.backToList')}
        </Link>
        <h1 className="mt-1 text-xl font-semibold">{t('salesInvoices.record')}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t('salesInvoices.recordExplanation')}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('salesInvoices.section.document')}</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <LabelledSelect
            id="series"
            label={t('salesInvoices.field.series')}
            value={seriesId}
            onValueChange={setSeriesId}
            options={idOptions(
              series.data?.items ?? [],
              (item) => `${item.abbreviation} — ${item.description}`,
            )}
          />
          <LabelledSelect
            id="customer"
            label={t('salesInvoices.field.customer')}
            value={customerId}
            onValueChange={setCustomerId}
            options={idOptions(customers.data?.items ?? [], (item) => item.name)}
          />
          <div className="space-y-1">
            <Label htmlFor="documentNumber">{t('salesInvoices.field.documentNumber')}</Label>
            <Input
              id="documentNumber"
              value={documentNumber}
              required
              onChange={(event) => setDocumentNumber(event.target.value)}
            />
            <p className="text-xs text-muted-foreground">
              {t('salesInvoices.documentNumberHint')}
            </p>
          </div>
          <div className="space-y-1">
            <Label htmlFor="invoiceDate">{t('salesInvoices.field.date')}</Label>
            <Input
              id="invoiceDate"
              type="date"
              value={invoiceDate}
              onChange={(event) => setInvoiceDate(event.target.value)}
            />
          </div>
          {/*
           * ⚠️ Every method is offered, including ones the owner has deactivated in Settings — and
           * that is deliberate rather than an oversight. A deactivated method is refused by the
           * server with a sentence naming it ("…is inactive, so it is not for new documents.
           * Invoices already settled by it are unaffected"), which is a better answer than an
           * option quietly missing from a list. R2b built that guard; F5 is the first screen that
           * can reach it.
           */}
          <LabelledSelect
            id="settlementMethod"
            label={t('salesInvoices.field.settlementMethod')}
            value={settlementMethod}
            onValueChange={setSettlementMethod}
            options={Object.values(SettlementMethod).map((method) => ({
              value: method,
              label: t(`SettlementMethod.${method}`, { ns: 'enums' }),
            }))}
          />
          <div className="space-y-1">
            <Label htmlFor="description">{t('salesInvoices.field.description')}</Label>
            <Input
              id="description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('salesInvoices.section.lines')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {lines.map((line, index) => (
            <div key={line.key} className="grid gap-3 rounded-md border p-3 sm:grid-cols-5">
              <LabelledSelect
                id={`line-type-${line.key}`}
                label={t('salesInvoices.line.type')}
                value={line.lineType}
                onValueChange={(value) =>
                  setLines((current) =>
                    current.map((item, position) =>
                      position === index
                        ? {
                            ...item,
                            lineType: value as SalesLineType,
                            // Clearing the other id matters: the record refuses a PRODUCT line that
                            // names a charge type, and vice versa, so a stale id from before the
                            // switch would be refused with a message about the wrong field.
                            productId: null,
                            chargeTypeId: null,
                          }
                        : item,
                    ),
                  )
                }
                options={Object.values(SalesLineType).map((type) => ({
                  value: type,
                  label: t(`SalesLineType.${type}`, { ns: 'enums' }),
                }))}
              />
              {line.lineType === SalesLineType.PRODUCT ? (
                <LabelledSelect
                  id={`line-product-${line.key}`}
                  label={t('salesInvoices.line.product')}
                  value={line.productId}
                  onValueChange={(value) =>
                    setLines((current) =>
                      current.map((item, position) =>
                        position === index ? { ...item, productId: value } : item,
                      ),
                    )
                  }
                  options={idOptions(
                    products.data?.items ?? [],
                    (item) => `${item.sku} — ${item.name}`,
                  )}
                />
              ) : (
                <LabelledSelect
                  id={`line-charge-${line.key}`}
                  label={t('salesInvoices.line.chargeType')}
                  value={line.chargeTypeId}
                  onValueChange={(value) =>
                    setLines((current) =>
                      current.map((item, position) =>
                        position === index ? { ...item, chargeTypeId: value } : item,
                      ),
                    )
                  }
                  options={idOptions(chargeTypes.data?.items ?? [], (item) => item.name)}
                />
              )}
              <div className="space-y-1">
                <Label htmlFor={`line-quantity-${line.key}`}>
                  {t('salesInvoices.line.quantity')}
                </Label>
                <QuantityInput
                  id={`line-quantity-${line.key}`}
                  value={line.quantity}
                  onValueChange={(value) =>
                    setLines((current) =>
                      current.map((item, position) =>
                        position === index ? { ...item, quantity: value } : item,
                      ),
                    )
                  }
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor={`line-price-${line.key}`}>
                  {t('salesInvoices.line.unitPrice')}
                </Label>
                <UnitCostInput
                  id={`line-price-${line.key}`}
                  currency={CURRENCY}
                  value={line.unitPrice}
                  onValueChange={(value) =>
                    setLines((current) =>
                      current.map((item, position) =>
                        position === index ? { ...item, unitPrice: value } : item,
                      ),
                    )
                  }
                />
              </div>
              <div className="flex items-end">
                <Button
                  variant="ghost"
                  size="sm"
                  aria-label={t('salesInvoices.line.remove')}
                  disabled={lines.length === 1}
                  onClick={() =>
                    setLines((current) => current.filter((_, position) => position !== index))
                  }
                >
                  <XIcon />
                </Button>
              </div>
            </div>
          ))}

          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setLines((current) => [...current, emptyLine(nextKey)])
              setNextKey((key) => key + 1)
            }}
          >
            <PlusIcon /> {t('salesInvoices.line.add')}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('salesInvoices.section.compare')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-1">
            <Label htmlFor="statedTotal">{t('salesInvoices.field.statedTotal')}</Label>
            <MoneyInput
              id="statedTotal"
              currency={CURRENCY}
              value={statedTotal}
              onValueChange={setStatedTotal}
            />
          </div>
          <p className="text-xs text-muted-foreground">{t('salesInvoices.statedTotalHint')}</p>

          <Button
            variant="outline"
            size="sm"
            disabled={!complete || preview.isPending}
            onClick={() => preview.mutate({ data: body() })}
          >
            {t('salesInvoices.preview')}
          </Button>
          <Refusal error={preview.error} />

          {priced && (
            <dl className="grid gap-2 text-sm sm:grid-cols-2">
              <Figure label={t('salesInvoices.field.net')}>
                {formatMoney(priced.net, i18n.language)}
              </Figure>
              <Figure label={t('salesInvoices.field.vat')}>
                {formatMoney(priced.vat, i18n.language)}
              </Figure>
              <Figure label={t('salesInvoices.field.gross')}>
                {formatMoney(priced.gross, i18n.language)}
              </Figure>
              <Figure label={t('salesInvoices.field.receivable')}>
                {formatMoney(priced.receivable, i18n.language)}
              </Figure>
              {priced.roundingDifference.amount !== '0.00' && (
                <>
                  <Figure label={t('salesInvoices.field.rounding')}>
                    {formatMoney(priced.roundingDifference, i18n.language)}
                  </Figure>
                  <Figure label={t('salesInvoices.field.threshold')}>
                    {formatMoney(priced.roundingThreshold, i18n.language)}
                  </Figure>
                </>
              )}
            </dl>
          )}

          {/*
           * ⚠️ Shown ONLY when the preview asks for it. Below the threshold the server drops
           * `roundingAcceptedBy` silently, so a permanently visible field would collect a name that
           * goes nowhere — and above it, the record is refused until this is filled in.
           */}
          {needsAcceptance && (
            <div className="space-y-3 rounded-md border border-destructive/40 p-3">
              <p className="text-sm">{t('salesInvoices.acceptanceNeeded')}</p>
              <div className="space-y-1">
                <Label htmlFor="roundingAcceptedBy">
                  {t('salesInvoices.field.roundingAcceptedBy')}
                </Label>
                <Input
                  id="roundingAcceptedBy"
                  value={roundingAcceptedBy}
                  onChange={(event) => setRoundingAcceptedBy(event.target.value)}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="roundingNote">{t('salesInvoices.field.roundingNote')}</Label>
                <Input
                  id="roundingNote"
                  value={roundingNote}
                  onChange={(event) => setRoundingNote(event.target.value)}
                />
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <div className="space-y-3">
        <Refusal error={record.error} />
        <Button
          disabled={!complete || record.isPending || (needsAcceptance && !roundingAcceptedBy)}
          onClick={() =>
            record.mutate(
              { data: body() },
              {
                // No invalidation here: `createQueryClient` invalidates every query on any
                // successful mutation. This is the fourteenth create form and the first that does
                // not carry its own copy of that line.
                onSuccess: (created) => void navigate(`/sales/invoices/${created.id}`),
              },
            )
          }
        >
          {t('salesInvoices.record')}
        </Button>
      </div>
    </div>
  )
}

function Figure({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4 border-b py-1 last:border-0">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="tabular-nums">{children}</dd>
    </div>
  )
}
