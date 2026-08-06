import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import {
  useSalesControllerNote,
  useSalesControllerReverseNote,
} from '@/api/generated/endpoints/sales/sales'
import { Section, type CreditNoteView } from '@/api/generated/model'
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
 * One recorded credit note.
 *
 * <h2>⚠️ NOTHING HERE IS EDITABLE, for the same two reasons the invoice screen is not</h2>
 *
 * A posted document is immutable (ADR 0006), and this one exists outside Novocore as well: Prosvasis
 * Go issued it and AADE holds it, so editing the copy would make the two disagree. Every value is
 * therefore plain text — `frontend/README.md`'s **third** field state, *no route exists on any
 * installation* — and not a `FieldEditor` with `editable: false`, which means *"your role may not"*
 * and would tell somebody holding `SALES:FULL` something false.
 *
 * <h2>⭐ What a credit note has that an invoice does not: the sale it corrects</h2>
 *
 * `salesInvoiceId` is mandatory on `NewCreditNote` — a credit note cannot exist on its own — and
 * every line names the **invoice line** it credits. So the invoice is linked from the header and
 * each line shows which sold line it came from, because "2 × ESP-001 returned" is only meaningful
 * against the sale that shipped them.
 *
 * <h2>⚠️ `stockReturned` is per LINE, and it is not a formality</h2>
 *
 * Crediting a price error returns no goods; crediting a return does. The two produce different
 * postings and only one of them touches inventory, so the flag is shown per line rather than
 * summarised — a note that credits three lines can genuinely have goods back on one of them.
 */
export function CreditNoteDetail() {
  const { t, i18n } = useTranslation('common')
  const { id } = useParams<{ id: string }>()
  const permissions = usePermissions()
  // id is a count, not a value, and the lint rule's documented escape is to say so on the line.
  // eslint-disable-next-line no-restricted-syntax
  const noteId = Number(id)

  const note = useSalesControllerNote(noteId)

  if (note.isLoading) return <p>{t('app.loading')}</p>
  if (!note.data) return <p>{t('creditNotes.notFound')}</p>

  const document = note.data

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link to="/sales/credit-notes" className="text-sm text-muted-foreground hover:underline">
            ← {t('creditNotes.backToList')}
          </Link>
          <h1 className="mt-1 flex items-center gap-2 text-xl font-semibold">
            {document.documentNumber}
            <StateBadge note={document} t={t} />
          </h1>
        </div>

        {permissions.canEdit(Section.SALES) && document.inForce && (
          <ReversalAction noteId={noteId} />
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('creditNotes.section.document')}</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <ReadOnly label={t('creditNotes.field.invoice')} hint={t('creditNotes.invoiceHint')}>
            <Link to={`/sales/invoices/${document.salesInvoiceId}`} className="hover:underline">
              {document.salesInvoiceNumber}
            </Link>
          </ReadOnly>
          <ReadOnly label={t('creditNotes.field.customer')} hint={t('creditNotes.derivedHint')}>
            <Link to={`/customers/${document.customerId}`} className="hover:underline">
              {document.customerName}
            </Link>
          </ReadOnly>
          <ReadOnly label={t('creditNotes.field.date')}>
            {new Date(document.creditNoteDate).toLocaleDateString(i18n.language)}
          </ReadOnly>
          {/*
           * ⚠️ Channel and settlement method are both DERIVED from the invoice, and are labelled as
           * such. `NewCreditNote` carries neither — the note takes them from the sale it corrects,
           * which is also why a deactivated payment method is deliberately NOT guarded here: the
           * note is HOLDING a method, not SETTING one, and the guard exists on setting.
           */}
          <ReadOnly label={t('creditNotes.field.channel')} hint={t('creditNotes.derivedHint')}>
            {t(`SalesChannel.${document.channel}`, { ns: 'enums' })}
          </ReadOnly>
          <ReadOnly
            label={t('creditNotes.field.settlementMethod')}
            hint={t('creditNotes.derivedHint')}
          >
            {/* ⚠️ R4/C.4: the row's OWN description, not an i18n enum label. A payment method is
                business data now, and business data is one Greek string — see NewPaymentMethod. */}
            {document.paymentMethodDescription ?? <UnsetValue />}
          </ReadOnly>
          <ReadOnly label={t('creditNotes.field.description')}>
            {document.description ?? <UnsetValue />}
          </ReadOnly>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('creditNotes.section.lines')}</CardTitle>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-muted-foreground">
                <th className="py-2 pr-3">{t('creditNotes.line.what')}</th>
                <th className="py-2 pr-3 text-right">{t('creditNotes.line.quantity')}</th>
                <th className="py-2 pr-3 text-right">{t('creditNotes.line.unitPrice')}</th>
                <th className="py-2 pr-3 text-right">{t('creditNotes.line.net')}</th>
                <th className="py-2 pr-3 text-right">{t('creditNotes.line.vat')}</th>
                <th className="py-2 pr-3">{t('creditNotes.line.stock')}</th>
              </tr>
            </thead>
            <tbody>
              {document.lines.map((line) => (
                <tr key={line.id} className="border-b last:border-0">
                  <td className="py-2 pr-3">
                    <span className="font-medium">{line.productSku ?? '—'}</span>
                    {line.description && (
                      <span className="block text-muted-foreground">{line.description}</span>
                    )}
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
                  <td className="py-2 pr-3">
                    {/*
                     * Named in both directions rather than a tick and a blank: "no goods came back"
                     * is a positive statement about this line, not a field somebody left empty.
                     */}
                    <Badge variant={line.stockReturned ? 'secondary' : 'outline'}>
                      {line.stockReturned
                        ? t('creditNotes.line.stockReturned')
                        : t('creditNotes.line.stockNotReturned')}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('creditNotes.section.totals')}</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <ReadOnly label={t('creditNotes.field.net')}>
            {formatMoney(document.netTotal, i18n.language)}
          </ReadOnly>
          <ReadOnly label={t('creditNotes.field.vat')}>
            {formatMoney(document.vatTotal, i18n.language)}
          </ReadOnly>
          <ReadOnly label={t('creditNotes.field.gross')}>
            {formatMoney(document.grossTotal, i18n.language)}
          </ReadOnly>
          <ReadOnly
            label={t('creditNotes.field.statedTotal')}
            hint={t('creditNotes.statedTotalHint')}
          >
            {document.statedTotal ? (
              formatMoney(document.statedTotal, i18n.language)
            ) : (
              <UnsetValue />
            )}
          </ReadOnly>

          {/* Only when there IS one: a row of zeroes on every note trains an operator to ignore
              the one place this matters. */}
          {document.roundingAmount.amount !== '0.00' && (
            <>
              <ReadOnly label={t('creditNotes.field.rounding')}>
                {formatMoney(document.roundingAmount, i18n.language)}
              </ReadOnly>
              {document.roundingNeededReview && (
                <ReadOnly label={t('creditNotes.field.roundingAcceptedBy')}>
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
    </div>
  )
}

/** Three states from two booleans, and `reversal` must be read before `reversed`. */
function StateBadge({ note, t }: { note: CreditNoteView; t: (key: string) => string }) {
  if (note.reversal) return <Badge variant="outline">{t('creditNotes.state.reversal')}</Badge>
  if (note.reversed) return <Badge variant="destructive">{t('creditNotes.state.reversed')}</Badge>
  return <Badge variant="secondary">{t('creditNotes.state.inForce')}</Badge>
}

/**
 * Reversing a credit note recorded in error.
 *
 * ⚠️ **Reversal undoes NOVOCORE'S MIS-RECORDING, not the document.** Greek law has no cancellation of
 * an issued document; Go's credit note still exists and must be recorded again correctly. That is
 * why reversal is itself transitional — once the adapter exists, the correction for a wrong mirror
 * is a re-fetch from the source (`CLAUDE.md` §1b and §2b).
 *
 * The reason is required, and the button stays disabled without one: `ReversalCommand.reason` is
 * guarded by `Required.text`, and the record argues why — a reversal that says nothing about why
 * leaves the ledger internally consistent and unexplainable.
 */
function ReversalAction({ noteId }: { noteId: number }) {
  const { t } = useTranslation('common')
  const [open, setOpen] = useState(false)
  const [reversalDate, setReversalDate] = useState(() => localIsoDate(new Date()))
  const [reason, setReason] = useState('')

  // No invalidation of its own — `createQueryClient` invalidates every query on any successful
  // mutation, and a per-screen copy is what the global fix exists to prevent.
  const reverse = useSalesControllerReverseNote()

  if (!open) {
    return (
      <Button variant="outline" size="sm" onClick={() => setOpen(true)}>
        {t('creditNotes.reverse')}
      </Button>
    )
  }

  return (
    <div className="w-full max-w-sm space-y-3 rounded-md border p-4">
      <p className="text-sm text-muted-foreground">{t('creditNotes.reverseExplanation')}</p>
      <div className="space-y-1">
        <Label htmlFor="note-reversal-date">{t('creditNotes.reversalDate')}</Label>
        <Input
          id="note-reversal-date"
          type="date"
          value={reversalDate}
          onChange={(event) => setReversalDate(event.target.value)}
        />
      </div>
      <div className="space-y-1">
        <Label htmlFor="note-reversal-reason">{t('creditNotes.reversalReason')}</Label>
        <Input
          id="note-reversal-reason"
          value={reason}
          required
          onChange={(event) => setReason(event.target.value)}
        />
        <p className="text-xs text-muted-foreground">{t('creditNotes.reversalReasonHint')}</p>
      </div>
      <Refusal error={reverse.error} />
      <div className="flex gap-2">
        <Button
          size="sm"
          disabled={reverse.isPending || reason.trim() === ''}
          onClick={() =>
            reverse.mutate(
              { id: noteId, data: { reversalDate, reason: reason.trim() } },
              { onSuccess: () => setOpen(false) },
            )
          }
        >
          {t('creditNotes.reverseConfirm')}
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
