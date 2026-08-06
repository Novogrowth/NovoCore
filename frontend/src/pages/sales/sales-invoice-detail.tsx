import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import {
  useSalesControllerInvoice,
  useSalesControllerReverse,
} from '@/api/generated/endpoints/sales/sales'
import { Section, type SalesInvoiceView } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'
import { UnsetValue } from '@/components/field-editor/field-editor'
import { Refusal } from '@/components/refusal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { localIsoDate } from '@/lib/calendar-date'
import { formatMoney, formatQuantity, formatUnitCost } from '@/lib/decimal'

/**
 * One recorded sale.
 *
 * <h2>⚠️ THERE IS NO EDIT FORM ON THIS SCREEN, AND THAT IS THE DESIGN</h2>
 *
 * A posted document is immutable (ADR 0006, Q13), and it is immutable for a second reason that is
 * specific to sales: **the document exists outside Novocore**. Prosvasis Go issued it and AADE holds
 * it, so editing the copy here would make the two disagree about what was issued.
 *
 * This is not a `FieldEditor` screen with everything set to `editable: false`. That string means
 * *"your role may not"*, and it would tell an operator holding `SALES:FULL` something false. It is
 * `frontend/README.md`'s **third** field state — *no route exists, on any installation* — so every
 * value is plain text. Confirmed at the wire, not assumed: `PATCH /api/sales-invoices/{id}/…`
 * answers `404` (no handler registered at all) and `DELETE` answers `405`.
 *
 * **Correction is by reversal or by credit note**, and the two are not interchangeable — the backend
 * refuses to mix them, and both refusals are rendered here rather than hidden:
 *
 * - a document recorded **in error** is reversed;
 * - goods that **came back**, or a price that was wrong, are a credit note — a reversal would claim
 *   the sale never happened.
 *
 * <h2>The statutory identifiers, and why empty is correct forever in this phase</h2>
 *
 * ΜΑΡΚ, UID, QR URL and transmission status are ordinary core fields on the record (ADR 0016) — not
 * adapter data, not fetched from anywhere special. **Novocore never obtains a ΜΑΡΚ**, and there is
 * no route on the entire surface that writes any of the four. So they are blank on every invoice
 * today and will stay blank until step 18, and the screen says so in words rather than showing four
 * dashes that read like data somebody forgot to enter.
 */
export function SalesInvoiceDetail() {
  const { t, i18n } = useTranslation('common')
  const { id } = useParams<{ id: string }>()
  const permissions = usePermissions()
  // id is a count, not a value, and the lint rule's documented escape is to say so on the line.
  // eslint-disable-next-line no-restricted-syntax
  const invoiceId = Number(id)

  const invoice = useSalesControllerInvoice(invoiceId)

  if (invoice.isLoading) return <p>{t('app.loading')}</p>
  if (!invoice.data) return <p>{t('salesInvoices.notFound')}</p>

  const document = invoice.data

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link to="/sales/invoices" className="text-sm text-muted-foreground hover:underline">
            ← {t('salesInvoices.backToList')}
          </Link>
          <h1 className="mt-1 flex items-center gap-2 text-xl font-semibold">
            {document.documentNumber}
            <StateBadge invoice={document} t={t} />
          </h1>
        </div>

        {permissions.canEdit(Section.SALES) && document.inForce && (
          <ReversalAction invoiceId={invoiceId} />
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('salesInvoices.section.document')}</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <ReadOnly label={t('salesInvoices.field.customer')}>
            <Link to={`/customers/${document.customerId}`} className="hover:underline">
              {document.customerName}
            </Link>
          </ReadOnly>
          <ReadOnly label={t('salesInvoices.field.date')}>
            {new Date(document.invoiceDate).toLocaleDateString(i18n.language)}
          </ReadOnly>
          {/*
           * ⚠️ Series and channel sit together deliberately, and the channel is labelled as coming
           * FROM the series. A reader who saw them as two independent facts would eventually ask
           * why they cannot be edited separately — and the answer is that they are one fact. ΑΛΠW is
           * the web series, so an invoice in it is a web sale by definition rather than because
           * somebody remembered to tick a box.
           */}
          <ReadOnly label={t('salesInvoices.field.series')}>
            {document.seriesAbbreviation ?? <UnsetValue />}
          </ReadOnly>
          <ReadOnly
            label={t('salesInvoices.field.channel')}
            hint={t('salesInvoices.channelFromSeries')}
          >
            {t(`SalesChannel.${document.channel}`, { ns: 'enums' })}
          </ReadOnly>
          <ReadOnly label={t('salesInvoices.field.settlementMethod')}>
            {t(`SettlementMethod.${document.settlementMethod}`, { ns: 'enums' })}
          </ReadOnly>
          <ReadOnly label={t('salesInvoices.field.description')}>
            {document.description ?? <UnsetValue />}
          </ReadOnly>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('salesInvoices.section.lines')}</CardTitle>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-muted-foreground">
                <th className="py-2 pr-3">{t('salesInvoices.line.what')}</th>
                <th className="py-2 pr-3 text-right">{t('salesInvoices.line.quantity')}</th>
                <th className="py-2 pr-3 text-right">{t('salesInvoices.line.unitPrice')}</th>
                <th className="py-2 pr-3 text-right">{t('salesInvoices.line.net')}</th>
                <th className="py-2 pr-3 text-right">{t('salesInvoices.line.vat')}</th>
              </tr>
            </thead>
            <tbody>
              {document.lines.map((line) => (
                <tr key={line.id} className="border-b last:border-0">
                  <td className="py-2 pr-3">
                    <span className="font-medium">
                      {line.productSku ?? line.chargeTypeName ?? '—'}
                    </span>
                    {line.description && (
                      <span className="block text-muted-foreground">{line.description}</span>
                    )}
                    <span className="flex gap-1 pt-1">
                      {/* Both are W1-published derived properties, read off the view rather than
                          re-derived here — `bundle` and `exempt` are part of the documented schema. */}
                      {line.bundle && (
                        <Badge variant="outline">{t('salesInvoices.line.bundle')}</Badge>
                      )}
                      {line.exempt && (
                        <Badge variant="outline">{t('salesInvoices.line.exempt')}</Badge>
                      )}
                    </span>
                  </td>
                  <td className="py-2 pr-3 text-right tabular-nums">
                    {formatQuantity(line.quantity, i18n.language)}
                  </td>
                  <td className="py-2 pr-3 text-right tabular-nums">
                    {formatUnitCost(line.unitPrice, i18n.language)}
                  </td>
                  <td className="py-2 pr-3 text-right tabular-nums">
                    {formatMoney(line.netAmount, i18n.language)}
                  </td>
                  <td className="py-2 pr-3 text-right tabular-nums">
                    {formatMoney(line.vatAmount, i18n.language)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('salesInvoices.section.totals')}</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <ReadOnly label={t('salesInvoices.field.net')}>
            {formatMoney(document.netTotal, i18n.language)}
          </ReadOnly>
          <ReadOnly label={t('salesInvoices.field.vat')}>
            {formatMoney(document.vatTotal, i18n.language)}
          </ReadOnly>
          <ReadOnly label={t('salesInvoices.field.gross')}>
            {formatMoney(document.grossTotal, i18n.language)}
          </ReadOnly>
          <ReadOnly
            label={t('salesInvoices.field.statedTotal')}
            hint={t('salesInvoices.statedTotalHint')}
          >
            {document.statedTotal ? (
              formatMoney(document.statedTotal, i18n.language)
            ) : (
              <UnsetValue />
            )}
          </ReadOnly>

          {/*
           * The rounding block appears only when there IS a difference. A row of zeroes on every
           * ordinary invoice trains an operator to ignore the one place this matters.
           */}
          {document.roundingAmount.amount !== '0.00' && (
            <>
              <ReadOnly
                label={t('salesInvoices.field.rounding')}
                hint={t('salesInvoices.roundingHint')}
              >
                {formatMoney(document.roundingAmount, i18n.language)}
              </ReadOnly>
              {document.roundingNeededReview && (
                <ReadOnly
                  label={t('salesInvoices.field.roundingAcceptedBy')}
                  hint={t('salesInvoices.roundingAcceptedHint')}
                >
                  {document.roundingAcceptedBy ?? <UnsetValue />}
                  {document.roundingNote && (
                    <span className="block text-muted-foreground">{document.roundingNote}</span>
                  )}
                </ReadOnly>
              )}
            </>
          )}
        </CardContent>
      </Card>

      <StatutoryIdentifiers invoice={document} />
    </div>
  )
}

/** Three states from two booleans, and the order of the checks is load-bearing. */
function StateBadge({
  invoice,
  t,
}: {
  invoice: SalesInvoiceView
  t: (key: string) => string
}) {
  // A reversing document must be labelled before a reversed one, or the reversal reads as reversed.
  if (invoice.reversal) return <Badge variant="outline">{t('salesInvoices.state.reversal')}</Badge>
  if (invoice.reversed) {
    return <Badge variant="destructive">{t('salesInvoices.state.reversed')}</Badge>
  }
  return <Badge variant="secondary">{t('salesInvoices.state.inForce')}</Badge>
}

/**
 * ⚠️ **Plain text with a reason, never a disabled control.**
 *
 * There is no route that writes any of these, on any installation, and a disabled input would send
 * an administrator hunting for the permission that unlocks it. `frontend/README.md`'s third field
 * state, and the same treatment `cash.payment.limit` and a VAT class's rate already get.
 */
function StatutoryIdentifiers({ invoice }: { invoice: SalesInvoiceView }) {
  const { t } = useTranslation('common')

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('salesInvoices.section.statutory')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">{t('salesInvoices.statutoryExplanation')}</p>
        <div className="grid gap-4 sm:grid-cols-2">
          <ReadOnly label={t('salesInvoices.field.mark')}>
            {invoice.mark ?? <UnsetValue />}
          </ReadOnly>
          <ReadOnly label={t('salesInvoices.field.uid')}>
            {invoice.uid ?? <UnsetValue />}
          </ReadOnly>
          <ReadOnly label={t('salesInvoices.field.transmissionStatus')}>
            {t(`TransmissionStatus.${invoice.transmissionStatus}`, { ns: 'enums' })}
          </ReadOnly>
          <ReadOnly label={t('salesInvoices.field.qrUrl')}>
            {invoice.qrUrl ?? <UnsetValue />}
          </ReadOnly>
        </div>
      </CardContent>
    </Card>
  )
}

/**
 * Reversing a document recorded in error.
 *
 * Every one of the backend's four refusals reaches the operator through `<Refusal>`: reversing a
 * reversal, reversing twice, reversing an invoice that has credit notes against it, and — until
 * F5's A.1c settles it — anything the document-number rules refuse.
 */
function ReversalAction({ invoiceId }: { invoiceId: number }) {
  const { t } = useTranslation('common')
  const [open, setOpen] = useState(false)
  const [reversalDate, setReversalDate] = useState(() => localIsoDate(new Date()))
  const [reason, setReason] = useState('')

  // No invalidation of its own: `createQueryClient` invalidates every query on any successful
  // mutation. Thirteen create forms each carrying their own copy of that line is what caused the
  // stale-list defect, and a fourteenth is what the global fix exists to prevent.
  const reverse = useSalesControllerReverse()

  if (!open) {
    return (
      <Button variant="outline" size="sm" onClick={() => setOpen(true)}>
        {t('salesInvoices.reverse')}
      </Button>
    )
  }

  return (
    <div className="w-full max-w-sm space-y-3 rounded-md border p-4">
      <p className="text-sm text-muted-foreground">{t('salesInvoices.reverseExplanation')}</p>
      <div className="space-y-1">
        <Label htmlFor="reversal-date">{t('salesInvoices.reversalDate')}</Label>
        <Input
          id="reversal-date"
          type="date"
          value={reversalDate}
          onChange={(event) => setReversalDate(event.target.value)}
        />
      </div>
      {/*
       * ⚠️ The reason is REQUIRED, and the button stays disabled until there is one.
       *
       * `ReversalCommand.reason` is guarded by `Required.text`, so a blank one is refused by the
       * server — and the record says why in as many words: a reversal that says nothing about why
       * leaves the ledger internally consistent and unexplainable. Six routes share that record.
       *
       * This was written as an optional field first and `tsc` refused it, because 8a and F5's A.2
       * put the mandatory components into the generated types. That is the contract work paying for
       * itself: the old failure mode was a 400 naming no field, discovered by an operator.
       */}
      <div className="space-y-1">
        <Label htmlFor="reversal-reason">{t('salesInvoices.reversalReason')}</Label>
        <Input
          id="reversal-reason"
          value={reason}
          required
          onChange={(event) => setReason(event.target.value)}
        />
        <p className="text-xs text-muted-foreground">{t('salesInvoices.reversalReasonHint')}</p>
      </div>
      <Refusal error={reverse.error} />
      <div className="flex gap-2">
        <Button
          size="sm"
          disabled={reverse.isPending || reason.trim() === ''}
          onClick={() =>
            reverse.mutate(
              { id: invoiceId, data: { reversalDate, reason: reason.trim() } },
              { onSuccess: () => setOpen(false) },
            )
          }
        >
          {t('salesInvoices.reverseConfirm')}
        </Button>
        <Button variant="ghost" size="sm" onClick={() => setOpen(false)}>
          {t('field.cancel')}
        </Button>
      </div>
    </div>
  )
}

/** A value nobody can change, with an optional line saying where it comes from. */
function ReadOnly({
  label,
  hint,
  children,
}: {
  label: string
  hint?: string
  children: React.ReactNode
}) {
  return (
    <div className="space-y-1">
      <p className="text-sm font-medium">{label}</p>
      <div className="text-sm">{children}</div>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}
