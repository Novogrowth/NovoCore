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

    /*
     * ⚠️ `isCustomerSide()` was DELETED in W1, 2026-08-04, and the reason is that it had ZERO
     * REFERENCES ANYWHERE in compiled backend code — no invokevirtual, no method handle, no
     * constant-pool entry, in production or in tests. It was a bean getter, so Jackson published
     * `customerSide` on every wire body while this document said nothing about it; deleting an
     * unused accessor is one of the two honest ways to close that, and here it was plainly the
     * right one.
     *
     * It is NOT deleted because it made W1's direction rule simpler. It did — OpenItemRef is the
     * one record reached as both a request and a response, so a derived property on it was the
     * only case the rule could not describe either way — but that is a CONSEQUENCE, not the
     * justification. Recorded this way deliberately: a reader who believes the reason was
     * convenience will restore the accessor the first time it looks useful, and
     * SerialisedRecordContractIT will then refuse the build with a message that reads as an
     * obstacle rather than as an answer.
     *
     * `OpenItemType.isCustomerSide()` is untouched and is where the question belongs — a caller
     * that wants the answer asks the type, which is what the one real caller already did.
     */

    @Override
    public String toString() {
        return type + "#" + id;
    }
}
