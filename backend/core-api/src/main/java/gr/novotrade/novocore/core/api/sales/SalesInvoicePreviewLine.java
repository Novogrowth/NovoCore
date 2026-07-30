package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.tax.VatClassSource;
import java.util.Objects;

/**
 * One line of a {@link SalesInvoicePreview}, priced exactly as it would be posted.
 *
 * @param productId the product sold, or null on a charge line
 * @param chargeTypeId the fee charged, or null on a product line. Exactly one of the two is set,
 *     which is the same shape the recorded line has.
 * @param quantity as supplied
 * @param unitPrice as supplied
 * @param net the quantity extended at the unit price, <strong>rounded once</strong> at the mode from
 *     {@code ledger.rounding.mode}. Rounded here rather than at the total, because that is where the
 *     posting rounds it and a preview that rounded elsewhere would differ by a cent on exactly the
 *     lines that matter.
 * @param vat that net at the resolved rate, rounded once. Zero on an exempt line, which posts no VAT
 *     line at all.
 * @param gross {@code net + vat}
 * @param vatClassId the class the rate came from, or null on an exempt line
 * @param vatClassSource <strong>which level of the precedence rule supplied it</strong> — invoice
 *     line, customer, or product. Carried so an entry screen can say why a line is at the rate it is
 *     while the operator can still change it; after posting the same value is stored on the line, so
 *     the answer survives a later change to the customer's override.
 * @param vatExemptionReasonId the exemption claimed, or null. Present when the line is exempt rather
 *     than zero-rated — a distinction that is a named article of the Κώδικας ΦΠΑ, not a rate of zero.
 */
public record SalesInvoicePreviewLine(
        Long productId,
        Long chargeTypeId,
        String description,
        Quantity quantity,
        UnitCost unitPrice,
        Money net,
        Money vat,
        Money gross,
        Long vatClassId,
        VatClassSource vatClassSource,
        Long vatExemptionReasonId) {

    public SalesInvoicePreviewLine {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(net, "net");
        Objects.requireNonNull(vat, "vat");
        Objects.requireNonNull(gross, "gross");

        if ((productId == null) == (chargeTypeId == null)) {
            throw new IllegalArgumentException(
                    "A preview line is either a product or a charge, never both and never neither — "
                            + "the same shape the recorded line has.");
        }
    }
}
