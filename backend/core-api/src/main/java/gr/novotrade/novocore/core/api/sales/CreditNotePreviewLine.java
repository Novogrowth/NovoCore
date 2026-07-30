package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.util.Objects;

/**
 * One line of a {@link CreditNotePreview}, credited exactly as it would be posted.
 *
 * @param salesInvoiceLineId the invoice line being credited, as supplied
 * @param productId the product that line sold, or null where it was a charge. Resolved from the
 *     invoice rather than taken from the request — the caller names a line, not a product, and a
 *     screen listing what is being credited should not have to look it up separately.
 * @param chargeTypeId the fee that line charged, or null where it was a product
 * @param quantity as supplied
 * @param unitPrice as supplied
 * @param net the quantity extended at the unit price, rounded once
 * @param vat <strong>at the rate the original sale charged</strong>, taken from the invoice line —
 *     not re-resolved through {@code VatClassPrecedence}, because the customer's override may have
 *     changed since the sale and a return must give back what was actually taken
 * @param gross {@code net + vat}
 * @param vatClassId the class the original sale used, or null where that line was exempt
 * @param stockReturned whether this line puts goods back on the shelf, as supplied. Echoed rather
 *     than computed — it is the caller's statement about what physically happened, and nothing here
 *     is in a position to contradict it.
 */
public record CreditNotePreviewLine(
        long salesInvoiceLineId,
        Long productId,
        Long chargeTypeId,
        String description,
        Quantity quantity,
        UnitCost unitPrice,
        Money net,
        Money vat,
        Money gross,
        Long vatClassId,
        boolean stockReturned) {

    public CreditNotePreviewLine {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(net, "net");
        Objects.requireNonNull(vat, "vat");
        Objects.requireNonNull(gross, "gross");

        if ((productId == null) == (chargeTypeId == null)) {
            throw new IllegalArgumentException(
                    "A credited line is either a product or a charge, never both and never neither — "
                            + "it mirrors the invoice line it credits.");
        }
    }
}
