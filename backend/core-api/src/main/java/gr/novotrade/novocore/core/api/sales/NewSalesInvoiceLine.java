package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.tax.VatClassPrecedence;
import java.util.List;
import java.util.Objects;

/**
 * One line of a sale being recorded.
 *
 * <p><strong>The VAT class is optional here, and that is the precedence rule working.</strong>
 * {@link VatClassPrecedence} is <em>invoice line beats customer beats product</em>, so leaving
 * {@link #vatClassId()} null is how a line says "whatever this customer or this product says". There
 * is no fallback rate anywhere in NovoCore, so a line where all three levels are silent is refused
 * rather than assumed to be 24% — an undercharge is not recoverable from the customer after issue.
 *
 * @param unitPrice the price per unit, <em>excluding</em> VAT. Six decimals, because it is a
 *     multiplier: quantity times price is rounded once, at the line, rather than the price being
 *     rounded first and the error multiplied out.
 * @param vatClassId the line-level override, or null to let the customer then the product decide.
 * @param vatExemptionReasonId the article this line is outside VAT under. Mutually exclusive with
 *     {@link #vatClassId()} <em>and</em> with the precedence rule: an exempt line has no rate to
 *     resolve, so stating one here stops the customer's and the product's classes from applying.
 * @param serialNumbers the units being sold, for a serial-tracked product. Required for one and
 *     refused for anything else: brief §5 says COGS on a serialized item uses the named unit's own
 *     actual cost with no FIFO, so which machine left the shelf is not something a costing rule may
 *     choose. The count must equal {@link #quantity()}.
 */
public record NewSalesInvoiceLine(
        @Mandatory SalesLineType lineType,
        Long productId,
        Long chargeTypeId,
        @Mandatory Quantity quantity,
        @Mandatory UnitCost unitPrice,
        Long vatClassId,
        Long vatExemptionReasonId,
        List<String> serialNumbers,
        String description) {

    public NewSalesInvoiceLine {
        Objects.requireNonNull(lineType, "lineType");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        serialNumbers = serialNumbers == null ? List.of() : List.copyOf(serialNumbers);
        description = (description == null || description.isBlank()) ? null : description.trim();

        boolean isProduct = lineType == SalesLineType.PRODUCT;
        if (isProduct == (productId == null)) {
            throw new IllegalArgumentException(
                    "A " + lineType + " line " + (isProduct ? "must name a product." : "must not name "
                            + "a product — a charge line is a fee, and the account it credits comes "
                            + "from its charge type."));
        }
        if (isProduct == (chargeTypeId != null)) {
            throw new IllegalArgumentException(
                    "A " + lineType + " line " + (isProduct ? "must not name a charge type."
                            : "must name a charge type, which is what supplies its income account."));
        }
        if (!quantity.isPositive()) {
            throw new IllegalArgumentException(
                    "Sales invoice line quantity " + quantity + " is not positive. A return is a "
                            + "credit note, not a negative line — Q26 makes that its own transaction "
                            + "type so the return posts to contra-revenue rather than reducing sales.");
        }
        if (vatClassId != null && vatExemptionReasonId != null) {
            throw new IllegalArgumentException(
                    "This line states both a VAT class and an exemption reason, which are two legal "
                            + "positions about the same money. A line is charged at a rate or it is "
                            + "outside the scope of VAT under a named article; never both.");
        }
        if (!isProduct && !serialNumbers.isEmpty()) {
            throw new IllegalArgumentException(
                    "A charge line named " + serialNumbers.size() + " serial numbers. A fee has no "
                            + "units and takes nothing off a shelf.");
        }
    }

    /** A plain goods or service line, letting the customer then the product decide the rate. */
    public static NewSalesInvoiceLine product(long productId, Quantity quantity, UnitCost unitPrice) {
        return new NewSalesInvoiceLine(SalesLineType.PRODUCT, productId, null, quantity, unitPrice,
                null, null, List.of(), null);
    }

    /** A line for a serial-tracked product, naming the machines that left the shelf. */
    public static NewSalesInvoiceLine serializedProduct(
            long productId, UnitCost unitPrice, List<String> serialNumbers) {
        return new NewSalesInvoiceLine(SalesLineType.PRODUCT, productId, null,
                Quantity.of(serialNumbers.size()), unitPrice, null, null, serialNumbers, null);
    }

    /** A delivery or COD fee, at the charge type's own rate (Q33). */
    public static NewSalesInvoiceLine charge(long chargeTypeId, Money amount) {
        return new NewSalesInvoiceLine(SalesLineType.CHARGE, null, chargeTypeId, Quantity.of(1),
                UnitCost.from(amount), null, null, List.of(), null);
    }

    /** The same line at an explicit VAT class, overriding both the customer's and the product's. */
    public NewSalesInvoiceLine atVatClass(long overrideVatClassId) {
        return new NewSalesInvoiceLine(lineType, productId, chargeTypeId, quantity, unitPrice,
                overrideVatClassId, null, serialNumbers, description);
    }

    /** The same line, outside the scope of VAT under a named article. */
    public NewSalesInvoiceLine exemptUnder(long exemptionReasonId) {
        return new NewSalesInvoiceLine(lineType, productId, chargeTypeId, quantity, unitPrice,
                null, exemptionReasonId, serialNumbers, description);
    }

    public NewSalesInvoiceLine describedAs(String lineDescription) {
        return new NewSalesInvoiceLine(lineType, productId, chargeTypeId, quantity, unitPrice,
                vatClassId, vatExemptionReasonId, serialNumbers, lineDescription);
    }

    public boolean isProduct() {
        return lineType == SalesLineType.PRODUCT;
    }

    public boolean isExempt() {
        return vatExemptionReasonId != null;
    }

    public boolean isSerialized() {
        return !serialNumbers.isEmpty();
    }

    /** The exact net, unrounded, so the single rounding happens where the line is costed. */
    public java.math.BigDecimal netExactly() {
        return unitPrice.extendExactly(quantity);
    }
}
