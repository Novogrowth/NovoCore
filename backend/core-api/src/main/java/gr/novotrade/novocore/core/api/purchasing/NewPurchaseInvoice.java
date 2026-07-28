package gr.novotrade.novocore.core.api.purchasing;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Request to record a supplier's invoice.
 *
 * <p><strong>Recorded, not issued.</strong> A purchase invoice is somebody else's document: the
 * supplier wrote it, it carries their number, and from roadmap phase 7 it will usually arrive through
 * the myDATA import rather than being typed. That is why it is immutable once posted (Q13) and why
 * {@link #supplierInvoiceNumber} is required — without the supplier's own reference there is nothing
 * to reconcile against what they think they sent us.
 *
 * @param supplierInvoiceNumber the supplier's document number, unique per supplier. A supplier does
 *     not issue the same number twice, so a repeat is a duplicate entry — which the myDATA import
 *     makes a routine hazard rather than a theoretical one, since the same invoice can arrive from
 *     AADE and from a human on the same morning.
 * @param invoiceDate the supplier's document date, which is also the accounting date of the entry.
 *     Not the date it reached us: a report for the month it was issued in should contain it.
 */
public record NewPurchaseInvoice(
        long supplierId,
        String supplierInvoiceNumber,
        LocalDate invoiceDate,
        String description,
        List<NewPurchaseInvoiceLine> lines) {

    public NewPurchaseInvoice {
        Objects.requireNonNull(invoiceDate, "invoiceDate");
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);

        if (supplierId <= 0) {
            throw new IllegalArgumentException(
                    "supplierId must be a positive NovoCore id, got " + supplierId);
        }
        if (supplierInvoiceNumber == null || supplierInvoiceNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "A purchase invoice needs the supplier's own document number. It is the only "
                            + "handle both sides of the transaction share, and without it a duplicate "
                            + "is undetectable and a dispute is unanswerable.");
        }
        supplierInvoiceNumber = supplierInvoiceNumber.trim();
        description = (description == null || description.isBlank()) ? null : description.trim();

        if (lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "A purchase invoice with no lines owes nothing and says nothing. If the supplier "
                            + "sent an empty document, there is nothing to record.");
        }
    }

    public static NewPurchaseInvoice of(long supplierId, String supplierInvoiceNumber,
            LocalDate invoiceDate, List<NewPurchaseInvoiceLine> lines) {
        return new NewPurchaseInvoice(
                supplierId, supplierInvoiceNumber, invoiceDate, null, lines);
    }

    public NewPurchaseInvoice describedAs(String invoiceDescription) {
        return new NewPurchaseInvoice(
                supplierId, supplierInvoiceNumber, invoiceDate, invoiceDescription, lines);
    }
}
