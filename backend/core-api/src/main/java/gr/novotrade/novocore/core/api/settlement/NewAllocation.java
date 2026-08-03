package gr.novotrade.novocore.core.api.settlement;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import java.util.Objects;

/**
 * One document being settled, and by how much — brief §6's open item matching.
 *
 * <p>Covers everything §6 lists with no special cases: a simple payment is one of these, an
 * instalment is a smaller one, a bulk remittance is several against different invoices, and an
 * overpayment is the amount left over when they add up to less than the settlement.
 */
public record NewAllocation(@Mandatory OpenItemRef target, @Mandatory Money amount) {

    public NewAllocation {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "Allocation amount " + amount + " is not positive. Un-allocating is releasing the "
                            + "allocation, not adding a negative one — an allocation that could be "
                            + "negative would let a receipt increase what an invoice still owes.");
        }
    }

    public static NewAllocation of(OpenItemRef target, Money amount) {
        return new NewAllocation(target, amount);
    }

    public static NewAllocation againstSalesInvoice(long invoiceId, Money amount) {
        return new NewAllocation(OpenItemRef.salesInvoice(invoiceId), amount);
    }

    public static NewAllocation againstPurchaseInvoice(long invoiceId, Money amount) {
        return new NewAllocation(OpenItemRef.purchaseInvoice(invoiceId), amount);
    }

    public static NewAllocation againstCreditNote(long creditNoteId, Money amount) {
        return new NewAllocation(OpenItemRef.creditNote(creditNoteId), amount);
    }
}
