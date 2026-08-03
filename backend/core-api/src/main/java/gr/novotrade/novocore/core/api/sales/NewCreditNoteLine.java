package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.Required;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.util.Objects;

/**
 * One line of a credit note, crediting one line of the sale it corrects.
 *
 * <p><strong>It names the invoice line, not a product.</strong> That is what keeps a credit note
 * anchored: the VAT class, the product and the channel all come from the line being credited, so a
 * credit note cannot credit at a rate the sale never charged, nor credit something that was never on
 * the invoice. Both are ordinary mistakes when a credit note is free-hand, and both produce a VAT
 * return that does not reconcile.
 *
 * @param quantity how much of the line is being credited. May be less than the line sold — a
 *     customer returning two of five — and never more.
 * @param unitPrice what is being credited per unit. Normally the price it was sold at; a different
 *     figure is a partial credit, which is legitimate and is why this is stated rather than copied.
 * @param stockReturned whether the goods physically came back. False is the ordinary case for a price
 *     correction and for anything that was never stock. True puts the quantity back into the lots it
 *     left, at the cost it left at — <em>not</em> at any later cost, which would revalue stock through
 *     a credit note.
 */
public record NewCreditNoteLine(
        long salesInvoiceLineId,
        Quantity quantity,
        UnitCost unitPrice,
        Boolean stockReturned,
        String description) {

    public NewCreditNoteLine {
        Required.field(stockReturned, "stockReturned");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        description = (description == null || description.isBlank()) ? null : description.trim();

        if (salesInvoiceLineId <= 0) {
            throw new IllegalArgumentException(
                    "salesInvoiceLineId must be a positive NovoCore id, got " + salesInvoiceLineId);
        }
        if (!quantity.isPositive()) {
            throw new IllegalArgumentException(
                    "Credit note line quantity " + quantity + " is not positive. A credit note is "
                            + "already the negative direction; a negative line inside one would be a "
                            + "sale hidden in a return.");
        }
    }

    /** A credit for goods coming back, at the price they were sold at. */
    public static NewCreditNoteLine returning(
            long salesInvoiceLineId, Quantity quantity, UnitCost unitPrice) {
        return new NewCreditNoteLine(salesInvoiceLineId, quantity, unitPrice, true, null);
    }

    /** A credit with no goods coming back — a price correction, or a service credited. */
    public static NewCreditNoteLine priceOnly(
            long salesInvoiceLineId, Quantity quantity, UnitCost unitPrice) {
        return new NewCreditNoteLine(salesInvoiceLineId, quantity, unitPrice, false, null);
    }

    public NewCreditNoteLine describedAs(String lineDescription) {
        return new NewCreditNoteLine(
                salesInvoiceLineId, quantity, unitPrice, stockReturned, lineDescription);
    }

    /** The exact net, unrounded, so the single rounding happens where the line is costed. */
    public java.math.BigDecimal netExactly() {
        return unitPrice.extendExactly(quantity);
    }
}
