package gr.novotrade.novocore.core.api.settlement;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Objects;

/**
 * A reference to a document that carries an open amount.
 *
 * <p>The same shape and the same reasoning as {@code SubLedgerRef}: the reference is polymorphic, so
 * it cannot be a foreign key, and the type has to travel with the id or a bare number means nothing.
 * The referenced row is checked to exist by trigger, as a journal line's sub-ledger reference is.
 */
public record OpenItemRef(@Mandatory OpenItemType type, long id) {

    public OpenItemRef {
        Objects.requireNonNull(type, "type");
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "open item id must be a positive NovoCore id, got " + id);
        }
    }

    public static OpenItemRef salesInvoice(long invoiceId) {
        return new OpenItemRef(OpenItemType.SALES_INVOICE, invoiceId);
    }

    public static OpenItemRef purchaseInvoice(long invoiceId) {
        return new OpenItemRef(OpenItemType.PURCHASE_INVOICE, invoiceId);
    }

    public static OpenItemRef creditNote(long creditNoteId) {
        return new OpenItemRef(OpenItemType.CREDIT_NOTE, creditNoteId);
    }

    public static OpenItemRef customerCredit(long customerCreditId) {
        return new OpenItemRef(OpenItemType.CUSTOMER_CREDIT, customerCreditId);
    }

    public boolean isCustomerSide() {
        return type.isCustomerSide();
    }

    @Override
    public String toString() {
        return type + "#" + id;
    }
}
