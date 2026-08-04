package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Required;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Request to record a sale.
 *
 * <p><strong>Recorded, not issued.</strong> Prosvasis Go is the invoicing system of record until
 * roadmap phase 11, so {@link #documentNumber()} is the number Go printed and {@link #statedTotal()}
 * is what Go's document says the customer owes. That is also the second reason a sales invoice is
 * immutable (Q13): the document exists outside NovoCore, and editing it here would make the two
 * disagree about what was issued.
 *
 * <p><strong>{@link #statedTotal()} is what rounding is compared against</strong> — brief §6's
 * independently-computed comparison, per document (Q15). NovoCore recomputes the gross from the
 * lines; any difference posts to {@code Rounding differences} so that Accounts receivable always
 * agrees with the document the customer holds. Below {@code ledger.rounding.threshold} that happens
 * automatically; above it the invoice is <em>refused</em> until {@link #roundingAcceptedBy()} says who
 * agreed to it. Null means nothing external stated a total, and then there is nothing to compare
 * against and no rounding difference by definition.
 *
 * <p><strong>⚠️ There is no {@code channel} here, and its absence is the decision (R1b).</strong> A
 * sale's {@link SalesChannel} comes from its {@link #seriesId() series} and is not independently
 * settable: ΑΛΠW is the web series, so an invoice in it is a web sale <em>by definition</em> rather
 * than by someone remembering to tick a box. One fact, one place, and no way for the two to
 * disagree. <strong>F5 therefore has no channel field</strong> — there is nothing for it to bind.
 *
 * @param settlementMethod brief §6's settlement automation. Decides which account the invoice debits
 *     and therefore whether it is an open item at all — see {@link SettlementMethod}.
 * @param seriesId the numbering series this document belongs to. <strong>Mandatory since R1b</strong>,
 *     and it carries two things rather than one:
 *     <ul>
 *       <li>the <strong>channel</strong>, read straight off the series. ⚠️ A series whose channel is
 *           null is <em>not a sales channel</em> — the self-supply series genuinely are not, since
 *           the customer is the issuer — and recording against one is <strong>refused</strong>.
 *           {@code sales_invoice.channel} stays {@code NOT NULL}; see
 *           {@code SalesInvoiceService#record} for why that is a refusal rather than a relaxed
 *           constraint.
 *       <li>the <strong>document type</strong>, through {@code sales_document_series.document_type_id},
 *           which is {@code NOT NULL}. ⚠️ <strong>This is how the document type became mandatory —
 *           THROUGH the series.</strong> There is deliberately no {@code documentTypeId} here and no
 *           {@code document_type_id} column on {@code sales_invoice}: two independently settable
 *           fields could disagree about which type a document is, which is the same defect the
 *           channel rule above exists to prevent. The type is what decides whether stock moves.
 *     </ul>
 * @param roundingAcceptedBy who agreed to a rounding difference larger than the threshold, or null.
 *     Q15's remainder answered: the confirmation happens at entry and is recorded on the record,
 *     because the person who can explain the difference is the one holding the document, not whoever
 *     opens a review queue next week.
 */
public record NewSalesInvoice(
        long customerId,
        @Mandatory Long seriesId,
        @Mandatory SettlementMethod settlementMethod,
        String documentNumber,
        @Mandatory LocalDate invoiceDate,
        String description,
        Money statedTotal,
        String roundingAcceptedBy,
        String roundingNote,
        @Mandatory List<NewSalesInvoiceLine> lines) {

    public NewSalesInvoice {
        // Required.field rather than requireNonNull, because a caller omitting this has made a
        // client's mistake and not a programming error: it answers 400 naming the field, through
        // InvalidInputException, instead of "Malformed request body".
        Required.field(seriesId, "seriesId");
        Objects.requireNonNull(settlementMethod, "settlementMethod");
        Objects.requireNonNull(invoiceDate, "invoiceDate");
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
        description = (description == null || description.isBlank()) ? null : description.trim();
        roundingAcceptedBy =
                (roundingAcceptedBy == null || roundingAcceptedBy.isBlank())
                        ? null : roundingAcceptedBy.trim();
        roundingNote = (roundingNote == null || roundingNote.isBlank()) ? null : roundingNote.trim();

        if (customerId <= 0) {
            throw new IllegalArgumentException(
                    "customerId must be a positive NovoCore id, got " + customerId + ". A sale is "
                            + "always against somebody — for a walk-in with no details recorded that "
                            + "is the shared retail customer (Q10), which is a real answer rather "
                            + "than a missing one.");
        }
        if (documentNumber == null || documentNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "A sales invoice needs the number the issuing system printed on it. It is what a "
                            + "customer quotes, what AADE holds, and what makes a duplicate entry "
                            + "detectable.");
        }
        documentNumber = documentNumber.trim();

        if (lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "A sales invoice with no lines states no revenue and could not balance.");
        }
    }

    public static NewSalesInvoice of(long customerId, long seriesId,
            SettlementMethod settlementMethod, String documentNumber, LocalDate invoiceDate,
            List<NewSalesInvoiceLine> lines) {
        return new NewSalesInvoice(customerId, seriesId, settlementMethod, documentNumber,
                invoiceDate, null, null, null, null, lines);
    }

    /** The same request, compared against what the issuing system says the gross came to. */
    public NewSalesInvoice statedAs(Money externalGrossTotal) {
        return new NewSalesInvoice(customerId, seriesId, settlementMethod, documentNumber, invoiceDate,
                description, externalGrossTotal, roundingAcceptedBy, roundingNote, lines);
    }

    /** The same request, with a larger-than-threshold rounding difference explicitly accepted. */
    public NewSalesInvoice acceptingRoundingDifference(String acceptedBy, String note) {
        return new NewSalesInvoice(customerId, seriesId, settlementMethod, documentNumber, invoiceDate,
                description, statedTotal, acceptedBy, note, lines);
    }

    public NewSalesInvoice describedAs(String invoiceDescription) {
        return new NewSalesInvoice(customerId, seriesId, settlementMethod, documentNumber, invoiceDate,
                invoiceDescription, statedTotal, roundingAcceptedBy, roundingNote, lines);
    }

    public Optional<Money> statedTotalIfAny() {
        return Optional.ofNullable(statedTotal);
    }

    public boolean roundingDifferenceIsAccepted() {
        return roundingAcceptedBy != null;
    }
}
