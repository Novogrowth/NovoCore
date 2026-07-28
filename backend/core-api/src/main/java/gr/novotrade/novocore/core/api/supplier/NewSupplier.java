package gr.novotrade.novocore.core.api.supplier;

import gr.novotrade.novocore.core.api.tax.VatStatus;
import java.util.Objects;

/**
 * Request to add a supplier.
 *
 * <p>A command object rather than a parameter list, for the reason {@code NewAccount} gives: with
 * several adjacent {@code String} parameters, a transposed pair compiles cleanly and produces a
 * supplier whose phone number is in the email field.
 *
 * @param vatStatus never defaulted here. A supplier's VAT category is a fact about them, and
 *     silently assuming {@link VatStatus#DOMESTIC} for an import supplier is the kind of guess
 *     {@code CLAUDE.md} rule 7 exists to prevent. {@link #domestic} states it explicitly for the
 *     common case.
 */
public record NewSupplier(
        String name,
        String email,
        String phone,
        String vatNumber,
        VatStatus vatStatus,
        Long vatExemptionReasonId) {

    public NewSupplier {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(vatStatus, "vatStatus");
    }

    /** An ordinary Greek supplier. */
    public static NewSupplier domestic(String name, String vatNumber) {
        return new NewSupplier(name, null, null, vatNumber, VatStatus.DOMESTIC, null);
    }
}
