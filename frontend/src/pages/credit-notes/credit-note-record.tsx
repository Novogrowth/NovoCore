import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'

import {
  useSalesControllerInvoice,
  useSalesControllerInvoices,
  useSalesControllerPreviewNote,
  useSalesControllerRecordNote,
} from '@/api/generated/endpoints/sales/sales'
import type {
  Money,
  NewCreditNote,
  NewCreditNoteLine,
  Quantity,
  UnitCost,
} from '@/api/generated/model'
import { MoneyInput, QuantityInput, UnitCostInput } from '@/components/decimal/decimal-input'
import { OptionSelect } from '@/components/option-select'
import { Refusal } from '@/components/refusal'
import { SegmentedControl } from '@/components/segmented-control'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { localIsoDate } from '@/lib/calendar-date'
import { formatMoney } from '@/lib/decimal'

/** One invoice line as the form holds it while deciding what to credit. */
interface LineDraft {
  salesInvoiceLineId: number
  label: string
  include: boolean
  quantity: Quantity | undefined
  unitPrice: UnitCost | undefined
  stockReturned: boolean
}

/**
 * Recording a credit note that already exists.
 *
 * <h2>⚠️⚠️ THIS FORM IS THE THINNEST THING IN F5, DELIBERATELY. DO NOT FINISH IT</h2>
 *
 * **Owner decision, 2026-08-05.** The sales invoice record form is a test harness; this one is
 * thinner still, because **nobody will ever type a credit note.** A credit note is issued by the
 * invoicing software — Prosvasis Go today — and Novocore fetches it back; the scope this form was
 * given in as many words is *"enough to exercise the recording path, its derived fields and its
 * refusals"*, and **the polished version was explicitly ruled out**.
 *
 * So a reader who arrives here wanting to add line search, a returns picker, partial-credit
 * shortcuts or keyboard flow is about to spend effort on a screen scheduled for deletion. The
 * **list** and **detail** screens beside it are permanent product; the recording **path** behind
 * this form is permanent; this form is neither. Full statement in `CLAUDE.md` §1b.
 *
 * <h2>Driven FROM an invoice, because a credit note cannot exist without one</h2>
 *
 * `NewCreditNote.salesInvoiceId` is mandatory, and each line names a `salesInvoiceLineId` rather
 * than a product. So the shape of this form is forced: choose the sale, then choose which of its
 * lines come back and how much of each. There is no free line entry and there cannot be one.
 *
 * ⚠️ **Everything else is DERIVED and visibly labelled as such.** The customer, the channel, the
 * settlement method and the VAT treatment all come from the invoice — `NewCreditNote` has no
 * component for any of them. They are shown as facts about the chosen sale, not as empty fields
 * somebody has to fill in, because a blank field invites an answer that would be discarded.
 *
 * <h2>Preview before submit, for the same structural reason as the invoice form</h2>
 *
 * Whether a rounding difference needs accepting is a comparison between figures the server computes
 * and a threshold held in Settings, so **this form cannot know it**. `POST /api/credit-notes/preview`
 * answers with both, out of the same computation the record path runs. The acceptance control
 * appears only when the preview asks for it — below the threshold `roundingAcceptedBy` is silently
 * dropped, so a permanently visible field would collect a name that goes nowhere.
 */
export function CreditNoteRecord() {
  const { t, i18n } = useTranslation('common')
  const navigate = useNavigate()

  const [invoiceId, setInvoiceId] = useState<string | null>(null)
  const [documentNumber, setDocumentNumber] = useState('')
  const [creditNoteDate, setCreditNoteDate] = useState(() => localIsoDate(new Date()))
  const [description, setDescription] = useState('')
  const [statedTotal, setStatedTotal] = useState<Money | undefined>(undefined)
  const [roundingAcceptedBy, setRoundingAcceptedBy] = useState('')
  const [roundingNote, setRoundingNote] = useState('')
  const [lines, setLines] = useState<LineDraft[]>([])
  /** Which invoice's lines have already been adopted — see the seeding block below. */
  const [seededFor, setSeededFor] = useState<string | null>(null)

  /*
   * The invoices to choose from — the current calendar year, the same range the two list screens
   * open on and for the same reason: `GET /api/sales-invoices` refuses a call with no range at all.
   * ⚠️ A credit note against an older invoice is therefore not reachable from this picker, and that
   * is an accepted limitation of a test harness rather than a decision about the domain.
   */
  const today = new Date()
  const invoices = useSalesControllerInvoices({
    from: `${today.getFullYear()}-01-01`,
    to: localIsoDate(today),
  })

  // eslint-disable-next-line no-restricted-syntax -- an id is a count, not a value
  const chosenId = invoiceId === null ? 0 : Number(invoiceId)
  const invoice = useSalesControllerInvoice(chosenId, { query: { enabled: chosenId !== 0 } })

  const preview = useSalesControllerPreviewNote()
  const record = useSalesControllerRecordNote()

  /**
   * Adopts the chosen invoice's lines as the starting point.
   *
   * Called from the picker rather than from an effect: an effect that resets state on every render
   * of a query result is the shape that wedged a browser tab once already (see
   * `frontend/README.md`'s render-loop note), and the operator choosing an invoice is a discrete
   * event with an obvious place to hang the work.
   */
  function adopt(chosen: string | null) {
    setInvoiceId(chosen)
    setLines([])
    setSeededFor(null)
  }

  const sale = invoice.data

  /*
   * ⚠️ Seeded ONCE per chosen invoice, and `seededFor` is what makes that a fact rather than a
   * hope. The obvious guard — "seed when `lines` is empty" — is wrong in a way that would not show
   * up in a screen test: excluding every line leaves the drafts in place, but crediting an invoice
   * whose lines were all removed and re-chosen would re-seed on a later render. Naming the invoice
   * already seeded terminates unconditionally, which matters because a render-phase update that can
   * repeat is exactly the shape that once wedged a browser tab (`frontend/README.md`).
   */
  if (invoiceId !== null && sale && seededFor !== invoiceId && String(sale.id) === invoiceId) {
    setSeededFor(invoiceId)
    setLines(
      sale.lines.map((line) => ({
        salesInvoiceLineId: line.id,
        label: line.productSku ?? line.chargeTypeName ?? String(line.id),
        include: true,
        quantity: line.quantity,
        unitPrice: line.unitPrice,
        // ⚠️ Not defaulted to true. Crediting a price error returns no goods, and only the operator
        // knows which of the two this is — the two produce different postings.
        stockReturned: false,
      })),
    )
  }

  function body(): NewCreditNote {
    const credited: NewCreditNoteLine[] = lines
      .filter((line) => line.include)
      .map((line) => ({
        salesInvoiceLineId: line.salesInvoiceLineId,
        quantity: line.quantity ?? '0',
        unitPrice: line.unitPrice ?? { amount: '0', currency: 'EUR' },
        stockReturned: line.stockReturned,
      }))

    return {
      // eslint-disable-next-line no-restricted-syntax -- an id is a count, not a value
      salesInvoiceId: Number(invoiceId),
      documentNumber,
      creditNoteDate,
      ...(description ? { description } : {}),
      ...(statedTotal ? { statedTotal } : {}),
      ...(roundingAcceptedBy ? { roundingAcceptedBy } : {}),
      ...(roundingNote ? { roundingNote } : {}),
      lines: credited,
    }
  }

  const priced = preview.data
  const needsAcceptance = priced?.roundingNeedsAcceptance ?? false

  const included = lines.filter((line) => line.include)
  const complete =
    invoiceId !== null &&
    documentNumber.trim() !== '' &&
    included.length > 0 &&
    included.every((line) => line.quantity !== undefined && line.unitPrice !== undefined)

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <Link to="/sales/credit-notes" className="text-sm text-muted-foreground hover:underline">
          ← {t('creditNotes.backToList')}
        </Link>
        <h1 className="mt-1 text-xl font-semibold">{t('creditNotes.record')}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t('creditNotes.recordExplanation')}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('creditNotes.section.document')}</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1">
            <Label htmlFor="credit-invoice">{t('creditNotes.field.invoice')}</Label>
            <OptionSelect
              id="credit-invoice"
              value={invoiceId}
              onValueChange={adopt}
              options={(invoices.data?.items ?? []).map((item) => ({
                value: String(item.id),
                label: `${item.documentNumber} — ${item.customerName}`,
              }))}
            />
            <p className="text-xs text-muted-foreground">{t('creditNotes.invoiceHint')}</p>
          </div>
          <div className="space-y-1">
            <Label htmlFor="credit-number">{t('creditNotes.field.documentNumber')}</Label>
            <Input
              id="credit-number"
              value={documentNumber}
              required
              onChange={(event) => setDocumentNumber(event.target.value)}
            />
            <p className="text-xs text-muted-foreground">{t('creditNotes.documentNumberHint')}</p>
          </div>
          <div className="space-y-1">
            <Label htmlFor="credit-date">{t('creditNotes.field.date')}</Label>
            <Input
              id="credit-date"
              type="date"
              value={creditNoteDate}
              onChange={(event) => setCreditNoteDate(event.target.value)}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="credit-description">{t('creditNotes.field.description')}</Label>
            <Input
              id="credit-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>
        </CardContent>
      </Card>

      {/*
       * ⚠️ DERIVED, and shown as derived rather than as empty fields. `NewCreditNote` has no
       * customer, no channel and no settlement method — the note takes all three from the invoice
       * it corrects. A blank input for any of them would invite an answer the server discards.
       */}
      {sale && (
        <Card>
          <CardHeader>
            <CardTitle>{t('creditNotes.section.derived')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <p className="text-sm text-muted-foreground">{t('creditNotes.derivedExplanation')}</p>
            <dl className="grid gap-2 text-sm sm:grid-cols-2">
              <Figure label={t('creditNotes.field.customer')}>{sale.customerName}</Figure>
              <Figure label={t('creditNotes.field.channel')}>
                {t(`SalesChannel.${sale.channel}`, { ns: 'enums' })}
              </Figure>
              <Figure label={t('creditNotes.field.settlementMethod')}>
                {t(`SettlementMethod.${sale.settlementMethod}`, { ns: 'enums' })}
              </Figure>
              <Figure label={t('creditNotes.field.series')}>
                {sale.seriesAbbreviation ?? '—'}
              </Figure>
            </dl>
          </CardContent>
        </Card>
      )}

      {lines.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>{t('creditNotes.section.lines')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-muted-foreground">{t('creditNotes.linesExplanation')}</p>
            {lines.map((line, index) => (
              <div
                key={line.salesInvoiceLineId}
                className="grid gap-3 rounded-md border p-3 sm:grid-cols-4"
              >
                <div className="space-y-1">
                  <p className="text-sm font-medium">{line.label}</p>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() =>
                      setLines((current) =>
                        current.map((item, position) =>
                          position === index ? { ...item, include: !item.include } : item,
                        ),
                      )
                    }
                  >
                    {line.include ? t('creditNotes.line.exclude') : t('creditNotes.line.include')}
                  </Button>
                </div>
                <div className="space-y-1">
                  <Label htmlFor={`credit-quantity-${line.salesInvoiceLineId}`}>
                    {t('creditNotes.line.quantity')}
                  </Label>
                  <QuantityInput
                    id={`credit-quantity-${line.salesInvoiceLineId}`}
                    value={line.quantity}
                    disabled={!line.include}
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
                  <Label htmlFor={`credit-price-${line.salesInvoiceLineId}`}>
                    {t('creditNotes.line.unitPrice')}
                  </Label>
                  <UnitCostInput
                    id={`credit-price-${line.salesInvoiceLineId}`}
                    currency="EUR"
                    value={line.unitPrice}
                    disabled={!line.include}
                    onValueChange={(value) =>
                      setLines((current) =>
                        current.map((item, position) =>
                          position === index ? { ...item, unitPrice: value } : item,
                        ),
                      )
                    }
                  />
                </div>
                {/*
                 * ⚠️ Two NAMED options, never a tick box. "No goods came back" is a positive answer
                 * about this line — a price correction credits money and returns nothing — and an
                 * unticked box says the same thing as a box nobody looked at.
                 */}
                <div className="space-y-1">
                  <Label>{t('creditNotes.line.stock')}</Label>
                  <SegmentedControl
                    aria-label={`${t('creditNotes.line.stock')} ${line.label}`}
                    disabled={!line.include}
                    value={line.stockReturned ? 'returned' : 'not-returned'}
                    options={[
                      { value: 'returned', label: t('creditNotes.line.stockReturned') },
                      { value: 'not-returned', label: t('creditNotes.line.stockNotReturned') },
                    ]}
                    onValueChange={(value) =>
                      setLines((current) =>
                        current.map((item, position) =>
                          position === index
                            ? { ...item, stockReturned: value === 'returned' }
                            : item,
                        ),
                      )
                    }
                  />
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>{t('creditNotes.section.compare')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-1">
            <Label htmlFor="credit-stated-total">{t('creditNotes.field.statedTotal')}</Label>
            <MoneyInput
              id="credit-stated-total"
              currency="EUR"
              value={statedTotal}
              onValueChange={setStatedTotal}
            />
          </div>
          <p className="text-xs text-muted-foreground">{t('creditNotes.statedTotalHint')}</p>

          <Button
            variant="outline"
            size="sm"
            disabled={!complete || preview.isPending}
            onClick={() => preview.mutate({ data: body() })}
          >
            {t('creditNotes.preview')}
          </Button>
          <Refusal error={preview.error} />

          {priced && (
            <dl className="grid gap-2 text-sm sm:grid-cols-2">
              <Figure label={t('creditNotes.field.net')}>
                {formatMoney(priced.net, i18n.language)}
              </Figure>
              <Figure label={t('creditNotes.field.vat')}>
                {formatMoney(priced.vat, i18n.language)}
              </Figure>
              <Figure label={t('creditNotes.field.gross')}>
                {formatMoney(priced.gross, i18n.language)}
              </Figure>
              {/* ⭐ `payable`, not `receivable`: a credit note is money going the other way, and the
                  preview record names it differently for that reason. */}
              <Figure label={t('creditNotes.field.payable')}>
                {formatMoney(priced.payable, i18n.language)}
              </Figure>
              {priced.roundingDifference.amount !== '0.00' && (
                <>
                  <Figure label={t('creditNotes.field.rounding')}>
                    {formatMoney(priced.roundingDifference, i18n.language)}
                  </Figure>
                  <Figure label={t('creditNotes.field.threshold')}>
                    {formatMoney(priced.roundingThreshold, i18n.language)}
                  </Figure>
                </>
              )}
            </dl>
          )}

          {needsAcceptance && (
            <div className="space-y-3 rounded-md border border-destructive/40 p-3">
              <p className="text-sm">{t('creditNotes.acceptanceNeeded')}</p>
              <div className="space-y-1">
                <Label htmlFor="credit-accepted-by">
                  {t('creditNotes.field.roundingAcceptedBy')}
                </Label>
                <Input
                  id="credit-accepted-by"
                  value={roundingAcceptedBy}
                  onChange={(event) => setRoundingAcceptedBy(event.target.value)}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="credit-rounding-note">{t('creditNotes.field.roundingNote')}</Label>
                <Input
                  id="credit-rounding-note"
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
              // No invalidation of its own: the shared `MutationCache` handles it.
              { onSuccess: (created) => void navigate(`/sales/credit-notes/${created.id}`) },
            )
          }
        >
          {t('creditNotes.record')}
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
