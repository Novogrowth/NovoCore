package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Required;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Request to issue a credit note against a sale. <strong>Q26, answered: its own transaction type,
 * not a negative sales invoice.</strong>
 *
 * <p>It references the invoice it corrects, which is what supplies the customer, the channel and
 * therefore the {@code Sales returns} account it debits. A credit note against no invoice would be a
 * discount posted as a return — a different fact needing a different account — so the reference is
 * required rather than optional.
 *
 * <p>Rounding works exactly as it does on the invoice: {@link #statedTotal()} is what the issuing
 * system's document says, the difference posts to {@code Rounding differences}, and a difference above
 * {@code ledger.rounding.threshold} refuses until somebody accepts it.
 */
public record NewCreditNote(
        long salesInvoiceId,
        @Mandatory String documentNumber,
        @Mandatory LocalDate creditNoteDate,
        String description,
        Money statedTotal,
        String roundingAcceptedBy,
        String roundingNote,
        @Mandatory List<NewCreditNoteLine> lines) {

    public NewCreditNote {
        Objects.requireNonNull(creditNoteDate, "creditNoteDate");
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
        description = (description == null || description.isBlank()) ? null : description.trim();
        roundingAcceptedBy = (roundingAcceptedBy == null || roundingAcceptedBy.isBlank())
                ? null : roundingAcceptedBy.trim();
        roundingNote = (roundingNote == null || roundingNote.isBlank()) ? null : roundingNote.trim();

        if (salesInvoiceId <= 0) {
            throw new IllegalArgumentException(
                    "salesInvoiceId must be a positive NovoCore id, got " + salesInvoiceId
                            + ". Q26 makes a credit note a correction to a specific sale, and the "
                            + "invoice is what supplies the channel, the customer and the rate.");
        }
        // Required.text, for the reasons written out at NewSalesInvoice.documentNumber (F5 A.2):
        // the component is mandatory in fact, was invisible to the generated contract, and answered
        // in a different shape from every Required-guarded field beside it.
        documentNumber = Required.text(documentNumber, "documentNumber").trim();

        if (lines.isEmpty()) {
            throw new IllegalArgumentException("A credit note with no lines credits nothing.");
        }
    }

    public static NewCreditNote of(long salesInvoiceId, String documentNumber,
            LocalDate creditNoteDate, List<NewCreditNoteLine> lines) {
        return new NewCreditNote(
                salesInvoiceId, documentNumber, creditNoteDate, null, null, null, null, lines);
    }

    public NewCreditNote statedAs(Money externalGrossTotal) {
        return new NewCreditNote(salesInvoiceId, documentNumber, creditNoteDate, description,
                externalGrossTotal, roundingAcceptedBy, roundingNote, lines);
    }

    public NewCreditNote acceptingRoundingDifference(String acceptedBy, String note) {
        return new NewCreditNote(salesInvoiceId, documentNumber, creditNoteDate, description,
                statedTotal, acceptedBy, note, lines);
    }

    public NewCreditNote describedAs(String creditNoteDescription) {
        return new NewCreditNote(salesInvoiceId, documentNumber, creditNoteDate,
                creditNoteDescription, statedTotal, roundingAcceptedBy, roundingNote, lines);
    }

    public Optional<Money> statedTotalIfAny() {
        return Optional.ofNullable(statedTotal);
    }

    public boolean roundingDifferenceIsAccepted() {
        return roundingAcceptedBy != null;
    }
}
