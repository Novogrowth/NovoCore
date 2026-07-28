package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.util.Objects;
import java.util.Optional;

/**
 * One line of a credit note.
 *
 * @param returnConsumptionId the record that put the stock back, when goods came back. Empty when
 *     nothing did — a price-only credit, a service, or a return of stock that was carried at zero and
 *     therefore recognised nothing on the way out and nothing on the way back.
 */
public record CreditNoteLineView(
        long id,
        int lineNumber,
        long salesInvoiceLineId,
        Long productId,
        String productSku,
        Quantity quantity,
        UnitCost unitPrice,
        Money netAmount,
        Money vatAmount,
        Long vatClassId,
        Long vatExemptionReasonId,
        boolean stockReturned,
        Long returnConsumptionId,
        String description) {

    public CreditNoteLineView {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(netAmount, "netAmount");
        Objects.requireNonNull(vatAmount, "vatAmount");
    }

    public Money grossAmount() {
        return netAmount.plus(vatAmount);
    }

    public Optional<Long> returnConsumption() {
        return Optional.ofNullable(returnConsumptionId);
    }

    /** True when goods came back but nothing was posted for them — a lot carried at zero. */
    public boolean returnedStockWorthNothing() {
        return stockReturned && returnConsumptionId == null;
    }
}
