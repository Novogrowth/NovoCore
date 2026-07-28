package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.tax.VatClassSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One line of a recorded sale.
 *
 * @param vatClassSource which level of {@code VatClassPrecedence} supplied the rate — the line, the
 *     customer, or the product. Stored rather than recomputed, because that is what makes "why is
 *     this line at 13%?" answerable a year later, when the customer's override has been changed and
 *     the product's default with it. Empty on an exempt line, which had no rate to choose.
 * @param components the bundle decomposition, empty for an ordinary line. See
 *     {@link SalesInvoiceLineComponentView} — and read <em>these</em> rather than the bundle's current
 *     definition.
 */
public record SalesInvoiceLineView(
        long id,
        int lineNumber,
        SalesLineType lineType,
        Long productId,
        String productSku,
        Long chargeTypeId,
        String chargeTypeName,
        Quantity quantity,
        UnitCost unitPrice,
        Money netAmount,
        Money vatAmount,
        Long vatClassId,
        VatClassSource vatClassSource,
        Long vatExemptionReasonId,
        Long stockConsumptionId,
        List<String> soldSerialNumbers,
        String description,
        List<SalesInvoiceLineComponentView> components) {

    public SalesInvoiceLineView {
        Objects.requireNonNull(lineType, "lineType");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(netAmount, "netAmount");
        Objects.requireNonNull(vatAmount, "vatAmount");
        soldSerialNumbers = List.copyOf(Objects.requireNonNull(soldSerialNumbers, "soldSerialNumbers"));
        components = List.copyOf(Objects.requireNonNull(components, "components"));
    }

    /** Net plus VAT — what this line adds to what the customer owes. */
    public Money grossAmount() {
        return netAmount.plus(vatAmount);
    }

    public boolean isExempt() {
        return vatExemptionReasonId != null;
    }

    /** True when this line sold a bundle, and therefore carries its decomposition. */
    public boolean isBundle() {
        return !components.isEmpty();
    }

    public Optional<Long> stockConsumption() {
        return Optional.ofNullable(stockConsumptionId);
    }

    public Optional<VatClassSource> vatSource() {
        return Optional.ofNullable(vatClassSource);
    }
}
