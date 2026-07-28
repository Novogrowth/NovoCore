package gr.novotrade.novocore.core.api.customer;

import gr.novotrade.novocore.core.api.tax.VatStatus;
import java.util.Objects;

/**
 * Request to add a customer.
 *
 * @param vatStatus never defaulted. A customer's VAT category is a fact about them, and quietly
 *     assuming {@link VatStatus#DOMESTIC} for an intra-EU business would produce an invoice
 *     charging Greek VAT on a reverse-charged supply. {@link #retail} and {@link #domestic} state
 *     the common cases explicitly instead.
 */
public record NewCustomer(
        String name,
        String email,
        String phone,
        String vatNumber,
        VatStatus vatStatus,
        Long vatClassOverrideId,
        Long vatExemptionReasonId) {

    public NewCustomer {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(vatStatus, "vatStatus");
    }

    /** A walk-in or online retail customer: domestic, no VAT number, no overrides. */
    public static NewCustomer retail(String name, String email, String phone) {
        return new NewCustomer(name, email, phone, null, VatStatus.DOMESTIC, null, null);
    }

    /** A Greek business customer. */
    public static NewCustomer domestic(String name, String vatNumber) {
        return new NewCustomer(name, null, null, vatNumber, VatStatus.DOMESTIC, null, null);
    }
}
